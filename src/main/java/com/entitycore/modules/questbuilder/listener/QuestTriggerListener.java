package com.entitycore.modules.questbuilder.listener;

import com.entitycore.modules.questbuilder.trigger.QuestTriggerEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class QuestTriggerListener implements Listener {

    private final QuestTriggerEngine engine;

    public QuestTriggerListener(QuestTriggerEngine engine) {
        this.engine = engine;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        engine.handleMove(e.getPlayer(), e.getTo());
    }
}
