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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderListener implements Listener {

    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    private final QuestStorage storage;
    private final QuestScriptRegistry scripts;

    // Resize detection
    private static final int CORNER_THRESHOLD = 2; // blocks in X/Z

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

        // Prevent interacting with doors/buttons/etc while holding the tool
        event.setCancelled(true);

        var meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();

        QuestBuilderMode mode = QuestBuilderMode.from(
                pdc.get(QuestBuilderKeys.MODE, PersistentDataType.STRING)
        );

        // Bedrock-friendly: Sneak + Use cycles mode
        if (event.getPlayer().isSneaking()) {
            QuestBuilderMode next = mode.next();
            pdc.set(QuestBuilderKeys.MODE, PersistentDataType.STRING, next.name());

            if (next == QuestBuilderMode.AREA_SET) {
                pdc.set(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 1);
                pdc.set(QuestBuilderKeys.AREA_CORNER, PersistentDataType.INTEGER, 0);
            }

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

            case AREA_SET -> {
                handleAreaSet(event, item, draft, meta, pdc);
            }

            case DELETE -> {
                // Cancel resize selection first
                int corner = pdc.getOrDefault(QuestBuilderKeys.AREA_CORNER, PersistentDataType.INTEGER, 0);
                if (corner != 0) {
                    pdc.set(QuestBuilderKeys.AREA_CORNER, PersistentDataType.INTEGER, 0);
                    item.setItemMeta(meta);
                    event.getPlayer().sendMessage(ChatColor.YELLOW + "Resize selection cancelled.");
                    return;
                }

                if (draft.isAreaComplete()) {
                    draft.clearArea();
                    pdc.set(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 1);
                    item.setItemMeta(meta);
                    event.getPlayer().sendMessage(ChatColor.RED + "Area cleared.");
                    storage.save();
                    return;
                }

                if (!draft.points.isEmpty()) {
                    draft.points.remove(draft.points.size() - 1);
                    event.getPlayer().sendMessage(ChatColor.RED + "Removed last point. (" + draft.points.size() + " remain)");
                    storage.save();
                    return;
                }

                event.getPlayer().sendMessage(ChatColor.GRAY + "Nothing to delete.");
                return;
            }

            case INFO -> {
                event.getPlayer().sendMessage(ChatColor.AQUA + "Draft: " + draft.id);
                event.getPlayer().sendMessage("World: " + draft.world.getName());
                event.getPlayer().sendMessage("Points: " + draft.points.size());
                event.getPlayer().sendMessage("Area complete: " + draft.isAreaComplete());
                if (draft.isAreaComplete()) {
                    event.getPlayer().sendMessage("Pos1: " + draft.pos1.getBlockX() + "," + draft.pos1.getBlockY() + "," + draft.pos1.getBlockZ());
                    event.getPlayer().sendMessage("Pos2: " + draft.pos2.getBlockX() + "," + draft.pos2.getBlockY() + "," + draft.pos2.getBlockZ());
                }
                event.getPlayer().sendMessage("Mirror WG: " + draft.mirrorWorldGuard);
                int enter = scripts.getActionsForAreaTrigger(draft.id, com.entitycore.modules.questbuilder.trigger.QuestTriggerType.ENTER_AREA).size();
                int exit = scripts.getActionsForAreaTrigger(draft.id, com.entitycore.modules.questbuilder.trigger.QuestTriggerType.EXIT_AREA).size();
                event.getPlayer().sendMessage("ENTER actions: " + enter + " | EXIT actions: " + exit);
            }

            case PREVIEW -> {
                byte border = pdc.getOrDefault(QuestBuilderKeys.PREVIEW_BORDER, PersistentDataType.BYTE, (byte) 1);
                draft.preview(event.getPlayer(), border == (byte) 1);
            }

            case IMPORT_WORLD_EDIT -> {
                boolean ok = WorldEditImportAdapter.importSelection(event.getPlayer(), draft);
                if (ok) {
                    event.getPlayer().sendMessage(ChatColor.GREEN + "Imported WorldEdit selection into draft area.");
                    // Show border immediately after import
                    draft.preview(event.getPlayer(), true);
                } else {
                    event.getPlayer().sendMessage(ChatColor.RED + "No WorldEdit selection found.");
                }
            }

            case EDITOR -> {
                QuestBuilderEditorGui.open(event.getPlayer(), draftId, scripts);
            }
        }

        // Mirror to WG if enabled
        if (draft.mirrorWorldGuard && draft.isAreaComplete()) {
            WorldGuardAdapter.mirrorCuboidRegion(draft);
        }

        storage.save();
    }

    private void handleAreaSet(PlayerInteractEvent event, ItemStack item, QuestDraft draft, var meta, var pdc) {
        // If complete: support resize via corner selection
        if (draft.isAreaComplete()) {
            int selectedCorner = pdc.getOrDefault(QuestBuilderKeys.AREA_CORNER, PersistentDataType.INTEGER, 0);

            int minX = Math.min(draft.pos1.getBlockX(), draft.pos2.getBlockX());
            int maxX = Math.max(draft.pos1.getBlockX(), draft.pos2.getBlockX());
            int minZ = Math.min(draft.pos1.getBlockZ(), draft.pos2.getBlockZ());
            int maxZ = Math.max(draft.pos1.getBlockZ(), draft.pos2.getBlockZ());

            int clickX = event.getPlayer().getLocation().getBlockX();
            int clickY = event.getPlayer().getLocation().getBlockY();
            int clickZ = event.getPlayer().getLocation().getBlockZ();

            if (selectedCorner == 0) {
                int corner = detectCorner(clickX, clickZ, minX, maxX, minZ, maxZ);
                if (corner != 0) {
                    pdc.set(QuestBuilderKeys.AREA_CORNER, PersistentDataType.INTEGER, corner);
                    item.setItemMeta(meta);
                    event.getPlayer().sendMessage(ChatColor.YELLOW + "Corner selected. Click new location to move it.");
                    return;
                }

                // If not near a corner, don’t nuke selection.
                event.getPlayer().sendMessage(ChatColor.GRAY + "Click near a corner to resize, or use DELETE to clear.");
                return;
            }

            // Move the selected corner to new X/Z, preserve the opposite corner and Y range
            int oldMinY = Math.min(draft.pos1.getBlockY(), draft.pos2.getBlockY());
            int oldMaxY = Math.max(draft.pos1.getBlockY(), draft.pos2.getBlockY());

            int newMinX = minX, newMaxX = maxX, newMinZ = minZ, newMaxZ = maxZ;

            // corner mapping:
            // 1 = (minX,minZ)
            // 2 = (maxX,minZ)
            // 3 = (minX,maxZ)
            // 4 = (maxX,maxZ)
            switch (selectedCorner) {
                case 1 -> { newMinX = clickX; newMinZ = clickZ; }
                case 2 -> { newMaxX = clickX; newMinZ = clickZ; }
                case 3 -> { newMinX = clickX; newMaxZ = clickZ; }
                case 4 -> { newMaxX = clickX; newMaxZ = clickZ; }
                default -> {}
            }

            // Normalize mins/maxs after movement
            int fMinX = Math.min(newMinX, newMaxX);
            int fMaxX = Math.max(newMinX, newMaxX);
            int fMinZ = Math.min(newMinZ, newMaxZ);
            int fMaxZ = Math.max(newMinZ, newMaxZ);

            // Write back into pos1/pos2 keeping Y span
            draft.pos1 = new Vector(fMinX, oldMinY, fMinZ);
            draft.pos2 = new Vector(fMaxX, oldMaxY, fMaxZ);

            // Clear selection and refresh
            pdc.set(QuestBuilderKeys.AREA_CORNER, PersistentDataType.INTEGER, 0);
            item.setItemMeta(meta);

            event.getPlayer().sendMessage(ChatColor.GREEN + "Area resized.");
            draft.preview(event.getPlayer(), true); // auto preview refresh
            return;
        }

        // Otherwise: alternating pos1/pos2 setup
        int next = pdc.getOrDefault(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 1);
        if (next != 2) next = 1;

        if (next == 1) {
            draft.setPos1(event.getPlayer().getLocation());
            pdc.set(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 2);
            item.setItemMeta(meta);
            event.getPlayer().sendMessage(ChatColor.GREEN + "Pos1 set. (Next: Pos2)");
        } else {
            draft.setPos2(event.getPlayer().getLocation());
            pdc.set(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
            event.getPlayer().sendMessage(ChatColor.GREEN + "Pos2 set. Area complete.");
            draft.preview(event.getPlayer(), true); // auto preview immediately
        }
    }

    private int detectCorner(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        if (near(x, z, minX, minZ)) return 1;
        if (near(x, z, maxX, minZ)) return 2;
        if (near(x, z, minX, maxZ)) return 3;
        if (near(x, z, maxX, maxZ)) return 4;
        return 0;
    }

    private boolean near(int x, int z, int cx, int cz) {
        return Math.abs(x - cx) <= CORNER_THRESHOLD && Math.abs(z - cz) <= CORNER_THRESHOLD;
    }
}
