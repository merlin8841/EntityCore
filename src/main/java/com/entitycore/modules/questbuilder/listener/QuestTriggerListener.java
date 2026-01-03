package com.entitycore.modules.questbuilder.listener;

import com.entitycore.modules.questbuilder.trigger.QuestTriggerEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public final class QuestTriggerListener implements Listener {

    private final QuestTriggerEngine engine;

    public QuestTriggerListener(QuestTriggerEngine engine) {
        this.engine = engine;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;

        // Only evaluate when changing blocks (reduces spam)
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        engine.handleMove(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        // World changes and large teleports are safest to treat as “exit all”
        if (e.getTo() == null) return;
        if (e.getFrom().getWorld() != null && e.getTo().getWorld() != null
                && !e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            engine.handleHardLocationChange(e.getPlayer());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        engine.handleHardLocationChange(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        engine.handleHardLocationChange(e.getPlayer());
    }
}
