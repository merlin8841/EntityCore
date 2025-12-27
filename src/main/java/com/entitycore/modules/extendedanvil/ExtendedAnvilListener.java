package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ExtendedAnvilListener implements Listener {

    private final ExtendedAnvilSessionManager sessions;

    public ExtendedAnvilListener(ExtendedAnvilSessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = e.getView().getTopInventory();
        Inventory clicked = e.getClickedInventory();

        // Only care if this is OUR menu
        if (!sessions.isPlayerMenu(player, top)) return;

        int raw = e.getRawSlot();

        // If clicking in the PLAYER inventory (bottom), do NOT cancel normal clicks.
        // But handle SHIFT-click to route items into our two input slots.
        if (clicked != null && clicked != top) {
            if (e.isShiftClick()) {
                ItemStack moving = e.getCurrentItem();
                if (moving == null || moving.getType().isAir()) return;

                e.setCancelled(true);

                // Try put into ITEM slot first, then BOOK slot
                if (isEmpty(top.getItem(PlayerMenu.SLOT_ITEM))) {
                    top.setItem(PlayerMenu.SLOT_ITEM, moving.clone());
                    e.setCurrentItem(null);
                } else if (isEmpty(top.getItem(PlayerMenu.SLOT_BOOK))) {
                    top.setItem(PlayerMenu.SLOT_BOOK, moving.clone());
                    e.setCurrentItem(null);
                } else {
                    // nowhere to put it
                    return;
                }

                // refresh next tick
                Bukkit.getScheduler().runTaskLater(
                        sessions.getPlugin(),
                        () -> sessions.refresh(player, top),
                        1L
                );
            }
            return;
        }

        // Clicking TOP inventory: lock everything except the two input slots + result.
        if (raw == PlayerMenu.SLOT_RESULT) {
            e.setCancelled(true);
            sessions.completeCraft(player, top);
            return;
        }

        if (raw != PlayerMenu.SLOT_ITEM && raw != PlayerMenu.SLOT_BOOK) {
            e.setCancelled(true);
            return;
        }

        // Allow the click (place/take). Refresh after it applies.
        Bukkit.getScheduler().runTaskLater(
                sessions.getPlugin(),
                () -> sessions.refresh(player, top),
                1L
        );
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = e.getView().getTopInventory();
        if (!sessions.isPlayerMenu(player, top)) return;

        // If they drag across multiple slots, only allow if ALL affected slots are the 2 input slots.
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot == PlayerMenu.SLOT_ITEM || rawSlot == PlayerMenu.SLOT_BOOK) continue;
            // result slot + filler slots are blocked
            e.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTaskLater(
                sessions.getPlugin(),
                () -> sessions.refresh(player, top),
                1L
        );
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            sessions.close(p);
        }
    }

    private static boolean isEmpty(ItemStack it) {
        return it == null || it.getType().isAir();
    }
}
