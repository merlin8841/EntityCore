package com.entitycore.modules.questbuilder.trigger;

import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public final class QuestTriggerEngine {

    private final QuestStorage storage;
    private final Map<UUID, Set<String>> insideAreas = new HashMap<>();

    public QuestTriggerEngine(QuestStorage storage) {
        this.storage = storage;
    }

    public void handleMove(Player player, Location to) {
        if (player == null || to == null || to.getWorld() == null) return;

        Set<String> prev = insideAreas.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        Set<String> now = new HashSet<>();

        for (QuestDraft draft : storage.all()) {
            if (!draft.isAreaComplete()) continue;
            if (!draft.world.equals(to.getWorld())) continue;

            if (draft.contains(to)) {
                now.add(draft.id);
                if (!prev.contains(draft.id)) {
                    fire(player, draft.id, QuestTriggerType.ENTER_AREA);
                }
            } else if (prev.contains(draft.id)) {
                fire(player, draft.id, QuestTriggerType.EXIT_AREA);
            }
        }

        insideAreas.put(player.getUniqueId(), now);
    }

    /**
     * Call when player changes world / teleports / quits, so EXIT fires for any areas they were inside.
     */
    public void handleHardLocationChange(Player player) {
        if (player == null) return;

        Set<String> prev = insideAreas.remove(player.getUniqueId());
        if (prev == null || prev.isEmpty()) return;

        // Fire EXIT for all areas we believed they were inside
        for (String areaId : prev) {
            fire(player, areaId, QuestTriggerType.EXIT_AREA);
        }
    }

    private void fire(Player player, String areaId, QuestTriggerType type) {
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "entitycore-qb-trigger " + areaId + " " + type.name() + " " + player.getName()
        );
    }
}
