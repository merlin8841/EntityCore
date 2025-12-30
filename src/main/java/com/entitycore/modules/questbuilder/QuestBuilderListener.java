package com.entitycore.modules.questbuilder.listener;

import com.entitycore.modules.questbuilder.*;
import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderListener implements Listener {

    private final JavaPlugin plugin;
    private final QuestStorage storage;

    public QuestBuilderListener(JavaPlugin plugin, QuestStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (!QuestBuilderTool.isTool(item)) return;

        e.setCancelled(true);

        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();

        QuestBuilderMode mode = QuestBuilderMode.from(
                pdc.get(QuestBuilderKeys.MODE, PersistentDataType.STRING)
        );

        String draftId = pdc.get(
                QuestBuilderKeys.DRAFT,
                PersistentDataType.STRING
        );

        QuestDraft draft = storage.getOrCreate(draftId, e.getPlayer().getWorld());
        Location loc = e.getPlayer().getLocation();

        switch (mode) {
            case POINT -> {
                draft.addPoint(loc);
                e.getPlayer().sendMessage("§aPoint added.");
            }
            case AREA_POS1 -> {
                draft.setPos1(loc);
                e.getPlayer().sendMessage("§aArea pos1 set.");
            }
            case AREA_POS2 -> {
                draft.setPos2(loc);
                e.getPlayer().sendMessage("§aArea pos2 set.");
            }
            case INFO -> {
                e.getPlayer().sendMessage("§bDraft: " + draft.id);
                e.getPlayer().sendMessage("Points: " + draft.points.size());
                e.getPlayer().sendMessage("Area complete: " + draft.isAreaComplete());
            }
            case PREVIEW -> {
                draft.preview(e.getPlayer());
            }
        }

        storage.save();
    }
}
