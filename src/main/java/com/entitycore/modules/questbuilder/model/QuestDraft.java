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

    public final List<Vector> points = new ArrayList<>();

    public QuestDraft(String id, World world) {
        this.id = id;
        this.world = world;
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

    public boolean isAreaComplete() {
        return pos1 != null && pos2 != null;
    }

    public void preview(Player player) {
        if (!isAreaComplete()) return;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int y = player.getLocation().getBlockY();

        for (int x = minX; x <= maxX; x++) {
            world.spawnParticle(Particle.VILLAGER_HAPPY, x, y, minZ, 1);
            world.spawnParticle(Particle.VILLAGER_HAPPY, x, y, maxZ, 1);
        }
        for (int z = minZ; z <= maxZ; z++) {
            world.spawnParticle(Particle.VILLAGER_HAPPY, minX, y, z, 1);
            world.spawnParticle(Particle.VILLAGER_HAPPY, maxX, y, z, 1);
        }
    }
}
