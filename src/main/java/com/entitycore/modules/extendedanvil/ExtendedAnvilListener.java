package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class ExtendedAnvilListener implements Listener {

    private final ExtendedAnvilSessionManager sessions;

    public ExtendedAnvilListener(ExtendedAnvilSessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!sessions.isPlayerMenu(player, e.getInventory())) return;

        int slot = e.getRawSlot();

        // Result slot is "craft"
        if (slot == PlayerMenu.SLOT_RESULT) {
            e.setCancelled(true);
            sessions.completeCraft(player, e.getInventory());
            return;
        }

        // Only allow interacting with the two input slots
        if (slot != PlayerMenu.SLOT_ITEM && slot != PlayerMenu.SLOT_BOOK) {
            e.setCancelled(true);
            return;
        }

        // Let the click happen, then refresh preview next tick
        Bukkit.getScheduler().runTaskLater(
                sessions.getPlugin(),
                () -> sessions.refresh(player, e.getInventory()),
                1L
        );
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            sessions.close(p);
        }
    }
}
