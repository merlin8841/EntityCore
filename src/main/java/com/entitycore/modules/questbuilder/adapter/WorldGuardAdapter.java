package com.entitycore.modules.questbuilder.adapter;

import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;

public final class WorldGuardAdapter {

    private WorldGuardAdapter() {}

    public static boolean mirrorCuboidRegion(QuestDraft draft) {
        if (draft == null || !draft.isAreaComplete()) return false;

        RegionManager manager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(draft.world));

        if (manager == null) return false;

        int minX = Math.min(draft.pos1.getBlockX(), draft.pos2.getBlockX());
        int minY = Math.min(draft.pos1.getBlockY(), draft.pos2.getBlockY());
        int minZ = Math.min(draft.pos1.getBlockZ(), draft.pos2.getBlockZ());

        int maxX = Math.max(draft.pos1.getBlockX(), draft.pos2.getBlockX());
        int maxY = Math.max(draft.pos1.getBlockY(), draft.pos2.getBlockY());
        int maxZ = Math.max(draft.pos1.getBlockZ(), draft.pos2.getBlockZ());

        BlockVector3 min = BlockVector3.at(minX, minY, minZ);
        BlockVector3 max = BlockVector3.at(maxX, maxY, maxZ);

        String regionId = "qb_" + draft.id;

        ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

        manager.removeRegion(regionId);
        manager.addRegion(region);
        return true;
    }
}
