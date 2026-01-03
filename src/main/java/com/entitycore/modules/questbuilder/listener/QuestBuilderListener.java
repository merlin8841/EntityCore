package com.entitycore.modules.questbuilder.listener;

import com.entitycore.modules.questbuilder.*;
import com.entitycore.modules.questbuilder.adapter.WorldEditImportAdapter;
import com.entitycore.modules.questbuilder.adapter.WorldGuardAdapter;
import com.entitycore.modules.questbuilder.editor.QuestBuilderEditorGui;
import com.entitycore.modules.questbuilder.model.QuestDraft;
import com.entitycore.modules.questbuilder.script.QuestScriptRegistry;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderListener implements Listener {

    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    private final QuestStorage storage;
    private final QuestScriptRegistry scripts;

    public QuestBuilderListener(JavaPlugin plugin, QuestStorage storage, QuestScriptRegistry scripts) {
        this.plugin = plugin;
        this.storage = storage;
        this.scripts = scripts;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!QuestBuilderTool.isTool(item)) return;

        if (!event.getPlayer().hasPermission("entitycore.questbuilder.operator")) return;

        // Prevent normal interactions (doors/buttons etc)
        event.setCancelled(true);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        QuestBuilderMode mode = QuestBuilderMode.from(
                pdc.get(QuestBuilderKeys.MODE, PersistentDataType.STRING)
        );

        // Sneak-right-click cycles mode
        if (event.getPlayer().isSneaking()) {
            QuestBuilderMode next = mode.next();
            pdc.set(QuestBuilderKeys.MODE, PersistentDataType.STRING, next.name());

            // Reset AREA_SET alternation when entering AREA_SET
            if (next == QuestBuilderMode.AREA_SET) {
                pdc.set(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 1);
            }

            item.setItemMeta(meta);
            event.getPlayer().sendMessage(ChatColor.AQUA + "Mode: " + next.name());
            return;
        }

        // PREVIEW toggle (left-click): border on/off
        if (mode == QuestBuilderMode.PREVIEW
                && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {

            byte cur = pdc.getOrDefault(QuestBuilderKeys.PREVIEW_BORDER, PersistentDataType.BYTE, (byte) 1);
            byte next = (cur == (byte) 1) ? (byte) 0 : (byte) 1;
            pdc.set(QuestBuilderKeys.PREVIEW_BORDER, PersistentDataType.BYTE, next);

            item.setItemMeta(meta);
            event.getPlayer().sendMessage(ChatColor.AQUA + "Preview border: " + (next == 1 ? "ON" : "OFF"));
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

            case AREA_SET -> handleAreaSet(event, item, draft, meta, pdc);

            // Legacy/manual
            case AREA_POS1 -> {
                draft.setPos1(loc);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Area pos1 set.");
            }
            case AREA_POS2 -> {
                draft.setPos2(loc);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Area pos2 set.");
            }

            case INFO -> sendInfo(event, draftId, draft);

            case PREVIEW -> {
                byte border = pdc.getOrDefault(QuestBuilderKeys.PREVIEW_BORDER, PersistentDataType.BYTE, (byte) 1);
                draft.preview(event.getPlayer(), border == (byte) 1);
            }

            case IMPORT_WORLD_EDIT -> {
                boolean ok = WorldEditImportAdapter.importSelection(event.getPlayer(), draft);
                if (ok) {
                    event.getPlayer().sendMessage(ChatColor.GREEN + "Imported WorldEdit selection into draft area.");
                } else {
                    event.getPlayer().sendMessage(ChatColor.RED + "No WorldEdit selection found.");
                }
            }

            case EDITOR -> QuestBuilderEditorGui.open(event.getPlayer(), draftId, scripts);
        }

        // Mirror to WorldGuard if enabled and area complete
        if (draft.mirrorWorldGuard && draft.isAreaComplete()) {
            WorldGuardAdapter.mirrorCuboidRegion(draft);
        }

        storage.save();
    }

    private void handleAreaSet(PlayerInteractEvent event,
                               ItemStack item,
                               QuestDraft draft,
                               ItemMeta meta,
                               PersistentDataContainer pdc) {

        Location loc = event.getPlayer().getLocation();

        int next = pdc.getOrDefault(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 1);
        if (next != 2) next = 1;

        if (next == 1) {
            draft.setPos1(loc);
            pdc.set(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 2);
            item.setItemMeta(meta);
            event.getPlayer().sendMessage(ChatColor.GREEN + "Area pos1 set. (Next: pos2)");
        } else {
            draft.setPos2(loc);
            pdc.set(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
            event.getPlayer().sendMessage(ChatColor.GREEN + "Area pos2 set. (Next: pos1)");
        }
    }

    private void sendInfo(PlayerInteractEvent event, String draftId, QuestDraft draft) {
        event.getPlayer().sendMessage(ChatColor.AQUA + "Draft: " + draftId);
        event.getPlayer().sendMessage("Points: " + draft.points.size());
        event.getPlayer().sendMessage("Area complete: " + draft.isAreaComplete());
        if (draft.isAreaComplete()) {
            int minX = Math.min(draft.pos1.getBlockX(), draft.pos2.getBlockX());
            int minY = Math.min(draft.pos1.getBlockY(), draft.pos2.getBlockY());
            int minZ = Math.min(draft.pos1.getBlockZ(), draft.pos2.getBlockZ());
            int maxX = Math.max(draft.pos1.getBlockX(), draft.pos2.getBlockX());
            int maxY = Math.max(draft.pos1.getBlockY(), draft.pos2.getBlockY());
            int maxZ = Math.max(draft.pos1.getBlockZ(), draft.pos2.getBlockZ());
            event.getPlayer().sendMessage("Bounds: (" + minX + "," + minY + "," + minZ + ") -> (" + maxX + "," + maxY + "," + maxZ + ")");
        }
        event.getPlayer().sendMessage("Mirror WG: " + draft.mirrorWorldGuard);
    }
}
