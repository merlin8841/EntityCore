package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;

public final class ExtendedAnvilListener implements Listener {

    private final ExtendedAnvilSessionManager sessions;

    public ExtendedAnvilListener(ExtendedAnvilSessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!sessions.isAnyEaInventory(e.getView())) return;

        sessions.handleClick(p, e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!sessions.isAnyEaInventory(e.getView())) return;

        sessions.handleDrag(p, e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!sessions.isAnyEaInventory(e.getView())) return;

        sessions.handleClose(p, e.getView().getTopInventory());
    }

    // Safety: if player quits with menu open, return items
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(sessions.getPlugin(), () -> sessions.forceClose(p));
    }
}
