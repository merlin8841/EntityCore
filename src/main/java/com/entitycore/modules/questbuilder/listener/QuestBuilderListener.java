package com.entitycore.modules.questbuilder.listener;

import com.entitycore.modules.questbuilder.*;
import com.entitycore.modules.questbuilder.adapter.WorldEditImportAdapter;
import com.entitycore.modules.questbuilder.adapter.WorldGuardAdapter;
import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderListener implements Listener {

    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    private final QuestStorage storage;

    public QuestBuilderListener(JavaPlugin plugin, QuestStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!QuestBuilderTool.isTool(item)) return;

        if (!event.getPlayer().hasPermission("entitycore.questbuilder.operator")) return;

        // Avoid using the stick on blocks normally (doors/buttons etc) for now
        event.setCancelled(true);

        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();

        QuestBuilderMode mode = QuestBuilderMode.from(
                pdc.get(QuestBuilderKeys.MODE, PersistentDataType.STRING)
        );

        // Sneak-right-click cycles mode
        if (event.getPlayer().isSneaking()) {
            QuestBuilderMode next = mode.next();
            pdc.set(QuestBuilderKeys.MODE, PersistentDataType.STRING, next.name());
            item.setItemMeta(meta);

            event.getPlayer().sendMessage(ChatColor.AQUA + "Mode: " + next.name());
            return;
        }

        String draftId = pdc.getOrDefault(QuestBuilderKeys.DRAFT, PersistentDataType.STRING, "default");
        Location loc = event.getPlayer().getLocation();

        QuestDraft draft = storage.getOrCreate(draftId, loc.getWorld());

        switch (mode) {
            case POINT -> {
                draft.addPoint(loc);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Point added (" + draft.points.size() + ").");
            }
            case AREA_POS1 -> {
                draft.setPos1(loc);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Area pos1 set.");
            }
            case AREA_POS2 -> {
                draft.setPos2(loc);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Area pos2 set.");
            }
            case INFO -> {
                event.getPlayer().sendMessage(ChatColor.AQUA + "Draft: " + draft.id);
                event.getPlayer().sendMessage("Points: " + draft.points.size());
                event.getPlayer().sendMessage("Area complete: " + draft.isAreaComplete());
                event.getPlayer().sendMessage("Mirror WG: " + draft.mirrorWorldGuard);
            }
            case PREVIEW -> {
                draft.preview(event.getPlayer());
            }
            case IMPORT_WORLD_EDIT -> {
                boolean ok = WorldEditImportAdapter.importSelection(event.getPlayer(), draft);
                if (ok) {
                    event.getPlayer().sendMessage(ChatColor.GREEN + "Imported WorldEdit selection into draft area.");
                } else {
                    event.getPlayer().sendMessage(ChatColor.RED + "No WorldEdit selection found.");
                }
            }
        }

        // If mirror enabled and area complete, mirror to WorldGuard
        if (draft.mirrorWorldGuard && draft.isAreaComplete()) {
            WorldGuardAdapter.mirrorCuboidRegion(draft);
        }

        storage.save();
    }
}
