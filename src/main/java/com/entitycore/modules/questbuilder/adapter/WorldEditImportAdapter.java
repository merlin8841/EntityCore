package com.entitycore.modules.questbuilder.adapter;

import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.LocalSession;
import org.bukkit.entity.Player;

public final class WorldEditImportAdapter {

    private WorldEditImportAdapter() {}

    public static boolean importSelection(Player player, QuestDraft draft) {
        if (player == null || draft == null) return false;

        LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get(BukkitAdapter.adapt(player));

        if (session == null) return false;

        Region selection = session.getSelection(BukkitAdapter.adapt(player.getWorld()));
        if (selection == null) return false;

        draft.pos1 = selection.getMinimumPoint().toVector();
        draft.pos2 = selection.getMaximumPoint().toVector();
        return true;
    }
}
