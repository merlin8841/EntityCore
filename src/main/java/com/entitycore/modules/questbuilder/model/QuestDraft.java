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
    private static final int PREVIEW_TICKS = 20 * 8; // 8 seconds

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
     * - Always shows particles along edges
     * - Optionally shows a client-side block border (spoof blocks)
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

        clearPreview(player);

        Particle particle = resolvePreviewParticle();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int y = player.getLocation().getBlockY();

        // Particles along edges
        for (int x = minX; x <= maxX; x++) {
            world.spawnParticle(particle, x + 0.5, y + 0.2, minZ + 0.5, 1);
            world.spawnParticle(particle, x + 0.5, y + 0.2, maxZ + 0.5, 1);
        }
        for (int z = minZ; z <= maxZ; z++) {
            world.spawnParticle(particle, minX + 0.5, y + 0.2, z + 0.5, 1);
            world.spawnParticle(particle, maxX + 0.5, y + 0.2, z + 0.5, 1);
        }

        if (!showBorder) {
            player.sendMessage("§aPreview shown (particles only).");
            return;
        }

        BlockData glow = safeBlockData(Material.GLOWSTONE);
        if (glow == null) {
            player.sendMessage("§cPreview border unavailable (GLOWSTONE missing?)");
            return;
        }

        BlockData frog = safeBlockData(Material.ORANGE_FROGLIGHT);
        if (frog == null) frog = glow; // fallback for older versions

        Set<Location> spoofed = new HashSet<>();

        // Corners
        spoof(player, new Location(world, minX, y, minZ), glow, spoofed);
        spoof(player, new Location(world, maxX, y, minZ), glow, spoofed);
        spoof(player, new Location(world, minX, y, maxZ), glow, spoofed);
        spoof(player, new Location(world, maxX, y, maxZ), glow, spoofed);

        // Corner helpers (adjacent edge markers)
        addCornerHelpers(player, minX, maxX, minZ, maxZ, y, frog, spoofed);

        // Edge breadcrumbs every N blocks
        for (int x = minX; x <= maxX; x++) {
            if (x == minX || x == maxX) continue;
            if ((x - minX) % EDGE_MARK_EVERY == 0) {
                spoof(player, new Location(world, x, y, minZ), glow, spoofed);
                spoof(player, new Location(world, x, y, maxZ), glow, spoofed);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (z == minZ || z == maxZ) continue;
            if ((z - minZ) % EDGE_MARK_EVERY == 0) {
                spoof(player, new Location(world, minX, y, z), glow, spoofed);
                spoof(player, new Location(world, maxX, y, z), glow, spoofed);
            }
        }

        Plugin plugin = JavaPluginProvider.get();
        PREVIEWS.put(player.getUniqueId(), new PreviewState(world, spoofed));

        Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(player), PREVIEW_TICKS);

        player.sendMessage("§aPreview shown (border + particles) for " + (PREVIEW_TICKS / 20) + "s.");
    }

    // Compatibility helper: old signature
    public void preview(Player player) {
        preview(player, true);
    }

    private void addCornerHelpers(Player player, int minX, int maxX, int minZ, int maxZ, int y, BlockData helper, Set<Location> spoofed) {
        if (minX + 1 <= maxX) spoof(player, new Location(world, minX + 1, y, minZ), helper, spoofed);
        if (minZ + 1 <= maxZ) spoof(player, new Location(world, minX, y, minZ + 1), helper, spoofed);

        if (maxX - 1 >= minX) spoof(player, new Location(world, maxX - 1, y, minZ), helper, spoofed);
        if (minZ + 1 <= maxZ) spoof(player, new Location(world, maxX, y, minZ + 1), helper, spoofed);

        if (minX + 1 <= maxX) spoof(player, new Location(world, minX + 1, y, maxZ), helper, spoofed);
        if (maxZ - 1 >= minZ) spoof(player, new Location(world, minX, y, maxZ - 1), helper, spoofed);

        if (maxX - 1 >= minX) spoof(player, new Location(world, maxX - 1, y, maxZ), helper, spoofed);
        if (maxZ - 1 >= minZ) spoof(player, new Location(world, maxX, y, maxZ - 1), helper, spoofed);
    }

    private static void spoof(Player player, Location loc, BlockData data, Set<Location> out) {
        player.sendBlockChange(loc, data);
        out.add(loc);
    }

    private static void clearPreview(Player player) {
        if (player == null) return;
        PreviewState state = PREVIEWS.remove(player.getUniqueId());
        if (state == null) return;

        for (Location loc : state.locations) {
            try {
                player.sendBlockChange(loc, loc.getBlock().getBlockData());
            } catch (Exception ignored) {}
        }
    }

    private static BlockData safeBlockData(Material mat) {
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
