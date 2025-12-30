package com.entitycore.modules.questbuilder.model;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class QuestDraft {

    public final String id;
    public final World world;

    public Vector pos1;
    public Vector pos2;

    public boolean mirrorWorldGuard = false;

    public final List<Vector> points = new ArrayList<>();

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

    public void preview(Player player) {
        if (!isAreaComplete()) {
            player.sendMessage("§cArea incomplete.");
            return;
        }

        Particle particle = resolvePreviewParticle();

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        int y = player.getLocation().getBlockY();

        for (int x = minX; x <= maxX; x++) {
            world.spawnParticle(particle, x + 0.5, y + 0.2, minZ + 0.5, 1);
            world.spawnParticle(particle, x + 0.5, y + 0.2, maxZ + 0.5, 1);
        }
        for (int z = minZ; z <= maxZ; z++) {
            world.spawnParticle(particle, minX + 0.5, y + 0.2, z + 0.5, 1);
            world.spawnParticle(particle, maxX + 0.5, y + 0.2, z + 0.5, 1);
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
}
