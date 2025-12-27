package com.entitycore.modules.extendedanvil;

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

        if (e.getSlot() == PlayerMenu.SLOT_RESULT) {
            e.setCancelled(true);
            sessions.completeCraft(player, e.getInventory());
            return;
        }

        if (e.getSlot() != PlayerMenu.SLOT_ITEM &&
            e.getSlot() != PlayerMenu.SLOT_BOOK) {
            e.setCancelled(true);
            return;
        }

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
