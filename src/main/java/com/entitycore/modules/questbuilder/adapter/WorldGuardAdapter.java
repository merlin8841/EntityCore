package com.entitycore.modules.questbuilder.adapter;

import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.Bukkit;

public final class WorldGuardAdapter {

    public static void mirrorRegion(QuestDraft draft) {
        var wg = WorldGuard.getInstance();
        var container = wg.getPlatform().getRegionContainer();
        var world = Bukkit.getWorld(draft.world.getName());
        if (world == null) return;

        RegionManager manager = container.get(
                com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world)
        );
        if (manager == null) return;

        var min = new com.sk89q.worldedit.math.BlockVector3(
                Math.min(draft.pos1.getBlockX(), draft.pos2.getBlockX()),
                Math.min(draft.pos1.getBlockY(), draft.pos2.getBlockY()),
                Math.min(draft.pos1.getBlockZ(), draft.pos2.getBlockZ())
        );

        var max = new com.sk89q.worldedit.math.BlockVector3(
                Math.max(draft.pos1.getBlockX(), draft.pos2.getBlockX()),
                Math.max(draft.pos1.getBlockY(), draft.pos2.getBlockY()),
                Math.max(draft.pos1.getBlockZ(), draft.pos2.getBlockZ())
        );

        ProtectedCuboidRegion region =
                new ProtectedCuboidRegion("qb_" + draft.id, min, max);

        manager.addRegion(region);
    }
}
