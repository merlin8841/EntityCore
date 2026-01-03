package com.entitycore.modules.questbuilder.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;

public final class QuestDraft {

    public final String id;
    public final World world;

    public Vector pos1;
    public Vector pos2;

    public boolean mirrorWorldGuard = false;

    public final List<Vector> points = new ArrayList<>();

    // Preview spoof tracking per-player (client-side only)
    private static final Map<UUID, PreviewState> PREVIEWS = new HashMap<>();

    // Tunables (later move to config)
    private static final int EDGE_MARK_EVERY = 4;
    private static final int PREVIEW_TICKS = 20 * 30; // 30 seconds

    public QuestDraft(String id, World world) {
        this.id = id;
        this.world = world;
    }

    public boolean isAreaComplete() {
        return pos1 != null && pos2 != null;
    }

    public void addPoint(Location loc) {
        points.add(loc.toVector());
    }

    public void setPos1(Location loc) {
        pos1 = loc.toVector();
    }

    public void setPos2(Location loc) {
        pos2 = loc.toVector();
    }

    public void clearArea() {
        pos1 = null;
        pos2 = null;
    }

    public boolean contains(Location loc) {
        if (!isAreaComplete()) return false;
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().equals(world)) return false;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());

        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());

        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Preview:
     * - Shows particles along edges (at surface)
     * - Optionally shows a client-side painted block border (spoof blocks) at surface
     */
    public void preview(Player player, boolean showBorder) {
        if (!isAreaComplete()) {
            player.sendMessage("§cArea incomplete.");
            return;
        }
        if (player == null || player.getWorld() == null) return;
        if (!player.getWorld().equals(world)) {
            player.sendMessage("§cYou must be in the same world as the draft area to preview it.");
            return;
        }

        // Clear any previous border now (prevents stacking / lingering)
        clearPreview(player);

        Particle particle = resolvePreviewParticle();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        // Particles along edges at surface
        for (int x = minX; x <= maxX; x++) {
            int y1 = surfaceY(x, minZ);
            int y2 = surfaceY(x, maxZ);
            world.spawnParticle(particle, x + 0.5, y1 + 0.2, minZ + 0.5, 1);
            world.spawnParticle(particle, x + 0.5, y2 + 0.2, maxZ + 0.5, 1);
        }
        for (int z = minZ; z <= maxZ; z++) {
            int y1 = surfaceY(minX, z);
            int y2 = surfaceY(maxX, z);
            world.spawnParticle(particle, minX + 0.5, y1 + 0.2, z + 0.5, 1);
            world.spawnParticle(particle, maxX + 0.5, y2 + 0.2, z + 0.5, 1);
        }

        if (!showBorder) {
            player.sendMessage("§aPreview shown (particles only).");
            return;
        }

        // Always-available border base
        BlockData glow = safeBlockData(resolveMaterial(
                "GLOWSTONE",
                "SEA_LANTERN",
                "SHROOMLIGHT",
                "REDSTONE_LAMP"
        ));
        if (glow == null) {
            player.sendMessage("§cPreview border unavailable (no suitable light block found).");
            return;
        }

        // Frog lights: version-dependent; choose best match if available.
        BlockData helper = safeBlockData(resolveMaterial(
                "ORANGE_FROGLIGHT",
                "OCHRE_FROGLIGHT",
                "VERDANT_FROGLIGHT",
                "PEARLESCENT_FROGLIGHT"
        ));
        if (helper == null) helper = glow;

        Set<Location> spoofed = new HashSet<>();

        // Corners at surface
        spoof(player, locSurface(minX, minZ), glow, spoofed);
        spoof(player, locSurface(maxX, minZ), glow, spoofed);
        spoof(player, locSurface(minX, maxZ), glow, spoofed);
        spoof(player, locSurface(maxX, maxZ), glow, spoofed);

        // Corner helpers (adjacent edge markers)
        addCornerHelpers(player, minX, maxX, minZ, maxZ, helper, spoofed);

        // Edge breadcrumbs every N blocks at surface
        for (int x = minX; x <= maxX; x++) {
            if (x == minX || x == maxX) continue;
            if ((x - minX) % EDGE_MARK_EVERY == 0) {
                spoof(player, locSurface(x, minZ), glow, spoofed);
                spoof(player, locSurface(x, maxZ), glow, spoofed);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (z == minZ || z == maxZ) continue;
            if ((z - minZ) % EDGE_MARK_EVERY == 0) {
                spoof(player, locSurface(minX, z), glow, spoofed);
                spoof(player, locSurface(maxX, z), glow, spoofed);
            }
        }

        Plugin plugin = JavaPluginProvider.get();
        PreviewState state = new PreviewState(world, spoofed);

        // Cancel any previous scheduled clear and replace it
        PreviewState prev = PREVIEWS.put(player.getUniqueId(), state);
        if (prev != null && prev.clearTaskId != -1) {
            Bukkit.getScheduler().cancelTask(prev.clearTaskId);
        }

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(player), PREVIEW_TICKS).getTaskId();
        state.clearTaskId = taskId;

        player.sendMessage("§aPreview shown (border + particles) for " + (PREVIEW_TICKS / 20) + "s.");
    }

    private void addCornerHelpers(Player player, int minX, int maxX, int minZ, int maxZ, BlockData helper, Set<Location> spoofed) {
        if (minX + 1 <= maxX) spoof(player, locSurface(minX + 1, minZ), helper, spoofed);
        if (minZ + 1 <= maxZ) spoof(player, locSurface(minX, minZ + 1), helper, spoofed);

        if (maxX - 1 >= minX) spoof(player, locSurface(maxX - 1, minZ), helper, spoofed);
        if (minZ + 1 <= maxZ) spoof(player, locSurface(maxX, minZ + 1), helper, spoofed);

        if (minX + 1 <= maxX) spoof(player, locSurface(minX + 1, maxZ), helper, spoofed);
        if (maxZ - 1 >= minZ) spoof(player, locSurface(minX, maxZ - 1), helper, spoofed);

        if (maxX - 1 >= minX) spoof(player, locSurface(maxX - 1, maxZ), helper, spoofed);
        if (maxZ - 1 >= minZ) spoof(player, locSurface(maxX, maxZ - 1), helper, spoofed);
    }

    private Location locSurface(int x, int z) {
        return new Location(world, x, surfaceY(x, z), z);
    }

    /**
     * Choose the “ground” surface Y at X/Z.
     * We paint at the topmost block (highest solid-ish position) to avoid floating markers.
     */
    private int surfaceY(int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        // highestBlockYAt returns the first air block above terrain; paint on the block below.
        return Math.max(world.getMinHeight(), y - 1);
    }

    private static void spoof(Player player, Location loc, BlockData data, Set<Location> out) {
        player.sendBlockChange(loc, data);
        out.add(loc);
    }

    private static void clearPreview(Player player) {
        if (player == null) return;
        PreviewState state = PREVIEWS.remove(player.getUniqueId());
        if (state == null) return;

        if (state.clearTaskId != -1) {
            try { Bukkit.getScheduler().cancelTask(state.clearTaskId); } catch (Exception ignored) {}
        }

        for (Location loc : state.locations) {
            try {
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            } catch (Exception ignored) {}
        }
    }

    private static Material resolveMaterial(String... names) {
        if (names == null) return null;
        for (String n : names) {
            if (n == null || n.isBlank()) continue;
            Material m = Material.matchMaterial(n.trim().toUpperCase());
            if (m != null) return m;
        }
        return null;
    }

    private static BlockData safeBlockData(Material mat) {
        if (mat == null) return null;
        try {
            return mat.createBlockData();
        } catch (Exception e) {
            return null;
        }
    }

    private static Particle resolvePreviewParticle() {
        Particle p = tryParticle("VILLAGER_HAPPY");
        if (p != null) return p;
        p = tryParticle("HAPPY_VILLAGER");
        if (p != null) return p;
        p = tryParticle("CRIT");
        if (p != null) return p;
        return Particle.values()[0];
    }

    private static Particle tryParticle(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final class PreviewState {
        final World world;
        final Set<Location> locations;
        int clearTaskId = -1;

        PreviewState(World world, Set<Location> locations) {
            this.world = world;
            this.locations = locations;
        }
    }

    private static final class JavaPluginProvider {
        private static Plugin cached;

        static Plugin get() {
            if (cached != null) return cached;
            cached = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(QuestDraft.class);
            return cached;
        }
    }
}
