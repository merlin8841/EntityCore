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

    // BFS frontier
    private final ArrayDeque<BlockKey> frontier = new ArrayDeque<>();
    private final HashSet<BlockKey> seen = new HashSet<>();

    // Frontier count per chunk so we can unload when a chunk has no queued work
    private final HashMap<ChunkKey, Integer> chunkFrontierCount = new HashMap<>();

    // Track chunks loaded by this plugin
    private final HashSet<ChunkKey> pluginLoadedChunks = new HashSet<>();
    private final HashMap<ChunkKey, Long> unloadAfterMs = new HashMap<>();

    // Track chunks that have been infected (used for poison dirt effect)
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
        if (spreadTask != null) {
            spreadTask.cancel();
            spreadTask = null;
        }
        if (damageTask != null) {
            damageTask.cancel();
            damageTask = null;
        }

        frontier.clear();
        seen.clear();
        chunkFrontierCount.clear();
        pluginLoadedChunks.clear();
        unloadAfterMs.clear();
        infectedChunks.clear();
    }

    public int frontierSize() {
        return frontier.size();
    }

    public int infectedChunkCount() {
        return infectedChunks.size();
    }

    public void seedAt(Block seed) {
        if (seed == null) return;
        World w = seed.getWorld();
        if (w == null) return;

        // try to infect seed (respecting rules)
        if (tryInfect(seed)) {
            infectedChunks.add(new ChunkKey(w.getUID(), seed.getX() >> 4, seed.getZ() >> 4));
        }

        enqueue(seed);
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

        // Safety to prevent runaway loops if something weird happens
        int safety = 0;
        int safetyLimit = Math.max(10_000, budget * 20);

        while (converted < budget && !frontier.isEmpty() && safety++ < safetyLimit) {
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
        // 6-direction spread (faces)
        int x = b.getX();
        int y = b.getY();
        int z = b.getZ();

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

            int ncx = nx >> 4;
            int ncz = nz >> 4;

            ensureChunkLoaded(world, ncx, ncz);

            Block nb = world.getBlockAt(nx, ny, nz);

            // Continue BFS regardless of whether this block is convertible
            enqueue(nb);

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

        // Skip containers
        if (config.isSkipContainers()) {
            BlockState st = b.getState(false);
            if (st instanceof Container) return false;
        }

        try {
            b.setType(config.getInfectionMaterial(), false);
            return true;
        } catch (Throwable ignored) {
            // Bedrock, barriers, etc.
            return false;
        }
    }

    private void enqueue(Block b) {
        World w = b.getWorld();
        if (w == null) return;

        BlockKey key = new BlockKey(w.getUID(), b.getX(), b.getY(), b.getZ());
        if (!seen.add(key)) return;

        frontier.addLast(key);

        ChunkKey ck = new ChunkKey(w.getUID(), b.getX() >> 4, b.getZ() >> 4);
        chunkFrontierCount.merge(ck, 1, Integer::sum);

        // cancel pending unload because work exists again
        unloadAfterMs.remove(ck);
    }

    private void decrementFrontierCount(BlockKey key) {
        ChunkKey ck = new ChunkKey(key.worldId, key.x >> 4, key.z >> 4);
        Integer cur = chunkFrontierCount.get(ck);
        if (cur == null) return;

        int next = cur - 1;
        if (next <= 0) {
            chunkFrontierCount.remove(ck);
            if (pluginLoadedChunks.contains(ck)) {
                unloadAfterMs.put(ck, System.currentTimeMillis() + config.getUnloadDelayMs());
            }
        } else {
            chunkFrontierCount.put(ck, next);
        }
    }

    private void ensureChunkLoaded(World world, int cx, int cz) {
        ChunkKey ck = new ChunkKey(world.getUID(), cx, cz);

        if (!world.isChunkLoaded(cx, cz)) {
            world.getChunkAt(cx, cz).load(true);
            pluginLoadedChunks.add(ck);
        }
    }

    private void runUnloadHousekeeping() {
        if (unloadAfterMs.isEmpty()) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<ChunkKey, Long>> it = unloadAfterMs.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<ChunkKey, Long> e = it.next();
            ChunkKey ck = e.getKey();
            long when = e.getValue();

            if (now < when) continue;

            // new work appeared
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

            if (w.isChunkLoaded(ck.chunkX, ck.chunkZ)) {
                unloadChunkCompat(w, ck.chunkX, ck.chunkZ);
            }

            it.remove();
            pluginLoadedChunks.remove(ck);
        }
    }

    private void unloadChunkCompat(World world, int cx, int cz) {
        // Prefer Paper unloadChunkRequest if available, else fallback
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey other)) return false;
            return chunkX == other.chunkX && chunkZ == other.chunkZ && worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockKey other)) return false;
            return x == other.x && y == other.y && z == other.z && worldId.equals(other.worldId);
        }

        @Override
        public int hashCode() {
            int r = worldId.hashCode();
            r = 31 * r + x;
            r = 31 * r + y;
            r = 31 * r + z;
            return r;
        }
    }
}
