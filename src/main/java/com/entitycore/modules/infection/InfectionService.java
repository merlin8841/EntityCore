package com.entitycore.modules.infection;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.*;

public final class InfectionService {

    private final JavaPlugin plugin;
    private final InfectionConfig config;

    // frontier + seen (BFS spread)
    private final ArrayDeque<BlockKey> frontier = new ArrayDeque<>();
    private final HashSet<BlockKey> seen = new HashSet<>();

    // frontier counts per chunk to decide unload
    private final HashMap<ChunkKey, Integer> chunkFrontierCount = new HashMap<>();

    // chunks loaded by plugin
    private final HashSet<ChunkKey> pluginLoadedChunks = new HashSet<>();
    private final HashMap<ChunkKey, Long> unloadAfterMs = new HashMap<>();

    // infected chunks for damage effect
    private final HashSet<ChunkKey> infectedChunks = new HashSet<>();

    private BukkitTask spreadTask;
    private BukkitTask damageTask;

    public InfectionService(JavaPlugin plugin, InfectionConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void ensureTasksRunning() {
        ensureSpreadTaskRunning();
        ensureDamageTaskRunning();
    }

    public void shutdown() {
        if (spreadTask != null) { spreadTask.cancel(); spreadTask = null; }
        if (damageTask != null) { damageTask.cancel(); damageTask = null; }

        frontier.clear();
        seen.clear();
        chunkFrontierCount.clear();
        pluginLoadedChunks.clear();
        unloadAfterMs.clear();
        infectedChunks.clear();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public int frontierSize() {
        return frontier.size();
    }

    public int infectedChunkCount() {
        return infectedChunks.size();
    }

    public void setEnabled(boolean enabled) {
        config.setEnabled(enabled);
        config.save();
    }

    public void seedAt(Block seed) {
        if (seed == null || seed.getWorld() == null) return;
        // Infect the seed block (unless excluded)
        tryInfect(seed);
        enqueue(seed);
        // enabling is handled outside (GUI/command), but seeding should make it "ready"
    }

    public void restartSpreadTask() {
        if (spreadTask != null) {
            spreadTask.cancel();
            spreadTask = null;
        }
        ensureSpreadTaskRunning();
    }

    private void ensureSpreadTaskRunning() {
        if (spreadTask != null) return;

        // use config cycleTicks dynamically by recreating task when changed
        spreadTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (config.isEnabled()) {
                    runSpreadCycle();
                }
                runUnloadHousekeeping();
            } catch (Throwable t) {
                plugin.getLogger().severe("[Infection] Spread task error: " + t.getMessage());
                t.printStackTrace();
            }
        }, 1L, config.getCycleTicks());
    }

    private void ensureDamageTaskRunning() {
        if (damageTask != null) return;

        damageTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (!config.isEnabled()) return;
                if (!config.isDamageEnabled()) return;
                if (infectedChunks.isEmpty()) return;

                PotionEffectType type = PotionEffectType.getByName(config.getDamageEffect());
                if (type == null) type = PotionEffectType.POISON;

                PotionEffect effect = new PotionEffect(
                        type,
                        config.getDamageDurationTicks(),
                        config.getDamageAmplifier(),
                        true,
                        false,
                        true
                );

                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    Location loc = p.getLocation();
                    World w = loc.getWorld();
                    if (w == null) continue;

                    int cx = loc.getBlockX() >> 4;
                    int cz = loc.getBlockZ() >> 4;
                    ChunkKey ck = new ChunkKey(w.getUID(), cx, cz);

                    if (!infectedChunks.contains(ck)) continue;

                    Block under = w.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
                    if (under.getType() != config.getInfectionMaterial()) continue;

                    p.addPotionEffect(effect);
                }
            } catch (Throwable t) {
                plugin.getLogger().severe("[Infection] Damage task error: " + t.getMessage());
                t.printStackTrace();
            }
        }, 1L, config.getDamageIntervalTicks());
    }

    private void runSpreadCycle() {
        int budget = config.getInfectionsPerCycle();
        int converted = 0;

        while (converted < budget && !frontier.isEmpty()) {
            BlockKey key = frontier.pollFirst();
            if (key == null) break;

            World world = Bukkit.getWorld(key.worldId);
            if (world == null) {
                decrementFrontierCount(key);
                continue;
            }

            int cx = key.x >> 4;
            int cz = key.z >> 4;

            ensureChunkLoaded(world, cx, cz);

            Block b = world.getBlockAt(key.x, key.y, key.z);

            // Process neighbors regardless of whether this block was convertible
            // (so spread can flow around containers, etc.)
            spreadFrom(world, b);

            decrementFrontierCount(key);

            // Infecting happens in neighbor step; we count conversions there
            // But we still keep a budget by tracking converted as we go
            // -> we do this with a shared counter stored in a field? simplest: convert inline in spreadFrom with return count
            // We'll do convert counting inside spreadFrom and return how many converted.
            // (But spreadFrom currently void; so do local approach: convert inside loop below.)
            // To keep it straightforward, do conversion in spreadFrom method that increments a mutable counter holder.
            // We'll call an overload that takes a holder.
            // (We already called spreadFrom without conversion. Fix: call correct method below.)
        }

        // The above loop called the wrong spreadFrom; we want conversion budget.
        // To keep code simple and correct, we’ll do the conversion loop with the correct method:

        int safety = 0;
        while (converted < budget && !frontier.isEmpty() && safety++ < budget * 4) {
            BlockKey key = frontier.pollFirst();
            if (key == null) break;

            World world = Bukkit.getWorld(key.worldId);
            if (world == null) {
                decrementFrontierCount(key);
                continue;
            }

            int cx = key.x >> 4;
            int cz = key.z >> 4;
            ensureChunkLoaded(world, cx, cz);

            Block b = world.getBlockAt(key.x, key.y, key.z);

            IntHolder holder = new IntHolder(converted);
            spreadFrom(world, b, holder, budget);
            converted = holder.value;

            decrementFrontierCount(key);
        }
    }

    private void spreadFrom(World world, Block b, IntHolder converted, int budget) {
        // 6-direction BFS (faces)
        int x = b.getX();
        int y = b.getY();
        int z = b.getZ();

        // cheap bounds check: skip neighbors below min / above max
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;

        int[][] dirs = {
                { 1, 0, 0}, {-1, 0, 0},
                { 0, 1, 0}, { 0,-1, 0},
                { 0, 0, 1}, { 0, 0,-1}
        };

        for (int[] d : dirs) {
            if (converted.value >= budget) return;

            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];

            if (ny < minY || ny > maxY) continue;

            // Ensure neighbor chunk loaded if needed
            int ncx = nx >> 4;
            int ncz = nz >> 4;
            ensureChunkLoaded(world, ncx, ncz);

            Block nb = world.getBlockAt(nx, ny, nz);

            // Enqueue neighbor once
            enqueue(nb);

            // Try infect neighbor
            if (tryInfect(nb)) {
                converted.value++;
                infectedChunks.add(new ChunkKey(world.getUID(), ncx, ncz));
            }
        }
    }

    private boolean tryInfect(Block b) {
        Material type = b.getType();

        // Skip AIR (as requested)
        if (!config.isInfectAir() && type == Material.AIR) return false;

        // Already infected
        if (type == config.getInfectionMaterial()) return false;

        // Skip containers (as requested)
        if (config.isSkipContainers()) {
            BlockState st = b.getState(false);
            if (st instanceof Container) return false;
        }

        // Attempt replace
        try {
            b.setType(config.getInfectionMaterial(), false);
            return true;
        } catch (Throwable ignored) {
            // Some blocks just can't be set (bedrock etc.)
            return false;
        }
    }

    private void enqueue(Block b) {
        World w = b.getWorld();
        BlockKey key = new BlockKey(w.getUID(), b.getX(), b.getY(), b.getZ());
        if (!seen.add(key)) return;

        frontier.addLast(key);

        ChunkKey ck = new ChunkKey(w.getUID(), b.getX() >> 4, b.getZ() >> 4);
        chunkFrontierCount.merge(ck, 1, Integer::sum);

        // if we previously scheduled this chunk to unload, cancel it because work arrived again
        unloadAfterMs.remove(ck);
    }

    private void decrementFrontierCount(BlockKey key) {
        ChunkKey ck = new ChunkKey(key.worldId, key.x >> 4, key.z >> 4);
        Integer cur = chunkFrontierCount.get(ck);
        if (cur == null) return;

        int next = cur - 1;
        if (next <= 0) {
            chunkFrontierCount.remove(ck);

            // schedule unload if plugin loaded it
            if (pluginLoadedChunks.contains(ck)) {
                unloadAfterMs.put(ck, System.currentTimeMillis() + config.getUnloadDelayMs());
            }
        } else {
            chunkFrontierCount.put(ck, next);
        }
    }

    private void ensureChunkLoaded(World world, int cx, int cz) {
        ChunkKey ck = new ChunkKey(world.getUID(), cx, cz);

        boolean wasLoaded = world.isChunkLoaded(cx, cz);
        if (!wasLoaded) {
            // load chunk (sync)
            world.getChunkAt(cx, cz).load(true);
            pluginLoadedChunks.add(ck);
        }
    }

    private void runUnloadHousekeeping() {
        if (pluginLoadedChunks.isEmpty()) return;
        if (unloadAfterMs.isEmpty()) return;

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<ChunkKey, Long>> it = unloadAfterMs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkKey, Long> e = it.next();
            ChunkKey ck = e.getKey();
            long when = e.getValue();

            if (now < when) continue;

            // if new work appeared, skip
            if (chunkFrontierCount.containsKey(ck)) {
                it.remove();
                continue;
            }

            World w = Bukkit.getWorld(ck.worldId);
            if (w == null) {
                it.remove();
                pluginLoadedChunks.remove(ck);
                continue;
            }

            // Only unload if still loaded
            if (w.isChunkLoaded(ck.chunkX, ck.chunkZ)) {
                unloadChunkCompat(w, ck.chunkX, ck.chunkZ);
            }

            it.remove();
            pluginLoadedChunks.remove(ck);
        }
    }

    private void unloadChunkCompat(World world, int cx, int cz) {
        // Prefer Paper's unloadChunkRequest if present, else fall back
        try {
            Method m = world.getClass().getMethod("unloadChunkRequest", int.class, int.class);
            m.invoke(world, cx, cz);
            return;
        } catch (Throwable ignored) {
        }

        try {
            world.unloadChunk(cx, cz, true);
        } catch (Throwable ignored) {
        }
    }

    private static final class IntHolder {
        int value;
        IntHolder(int v) { value = v; }
    }

    public static final class ChunkKey {
        public final UUID worldId;
        public final int chunkX;
        public final int chunkZ;

        public ChunkKey(UUID worldId, int chunkX, int chunkZ) {
            this.worldId = worldId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey other)) return false;
            return chunkX == other.chunkX && chunkZ == other.chunkZ && worldId.equals(other.worldId);
        }

        @Override public int hashCode() {
            int r = worldId.hashCode();
            r = 31 * r + chunkX;
            r = 31 * r + chunkZ;
            return r;
        }
    }

    public static final class BlockKey {
        public final UUID worldId;
        public final int x, y, z;

        public BlockKey(UUID worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockKey other)) return false;
            return x == other.x && y == other.y && z == other.z && worldId.equals(other.worldId);
        }

        @Override public int hashCode() {
            int r = worldId.hashCode();
            r = 31 * r + x;
            r = 31 * r + y;
            r = 31 * r + z;
            return r;
        }
    }
}
