package com.entitycore.modules.questbuilder.adapter;

import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class WorldEditImportAdapter {

    private WorldEditImportAdapter() {}

    public static boolean importSelection(Player player, QuestDraft draft) {
        if (player == null || draft == null) return false;

        SessionManager sessions = WorldEdit.getInstance().getSessionManager();
        var wePlayer = BukkitAdapter.adapt(player);

        var session = sessions.get(wePlayer);
        if (session == null) return false;

        Region selection;
        try {
            selection = session.getSelection(wePlayer.getWorld());
        } catch (Exception e) {
            return false;
        }

        if (selection == null) return false;

        var min = selection.getMinimumPoint();
        var max = selection.getMaximumPoint();

        // BlockVector3 → Bukkit Vector (manual conversion)
        draft.pos1 = new Vector(min.getX(), min.getY(), min.getZ());
        draft.pos2 = new Vector(max.getX(), max.getY(), max.getZ());

        return true;
    }
}
