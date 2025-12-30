package com.entitycore.modules.questbuilder.listener;

import com.entitycore.modules.questbuilder.action.QuestActionExecutor;
import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.entitycore.modules.questbuilder.script.QuestScriptRegistry;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import com.entitycore.modules.questbuilder.trigger.QuestTriggerType;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

public final class QuestInteractTriggerListener implements Listener {

    private final QuestStorage storage;
    private final QuestScriptRegistry scripts;

    public QuestInteractTriggerListener(QuestStorage storage, QuestScriptRegistry scripts) {
        this.storage = storage;
        this.scripts = scripts;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;

        QuestTriggerType type = mapAction(e.getAction());
        if (type == null) return;

        var mat = e.getClickedBlock().getType();
        Location loc = e.getClickedBlock().getLocation();

        List<QuestScriptRegistry.InteractionTrigger> triggers = scripts.getInteractionTriggers();
        if (triggers.isEmpty()) return;

        for (QuestScriptRegistry.InteractionTrigger t : triggers) {
            if (t.type != type) continue;
            if (t.block != mat) continue;

            // Optional area requirement
            if (t.requireArea != null && !t.requireArea.isBlank()) {
                QuestDraft area = findAreaById(t.requireArea);
                if (area == null) continue;
                if (!area.contains(loc)) continue;
            }

            if (!t.actions.isEmpty()) {
                QuestActionExecutor.execute(e.getPlayer(), t.actions);
            }
        }
    }

    private QuestTriggerType mapAction(Action a) {
        if (a == Action.RIGHT_CLICK_BLOCK) return QuestTriggerType.RIGHT_CLICK_BLOCK;
        if (a == Action.PHYSICAL) return QuestTriggerType.PHYSICAL_TRIGGER;
        return null;
    }

    private QuestDraft findAreaById(String id) {
        for (QuestDraft d : storage.all()) {
            if (d.id.equalsIgnoreCase(id)) return d;
        }
        return null;
    }
}
