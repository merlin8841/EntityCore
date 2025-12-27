package com.entitycore.modules.extendedanvil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ExtendedAnvilListener implements Listener {

    private final ExtendedAnvilSessionManager sessions;

    public ExtendedAnvilListener(ExtendedAnvilSessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPrepare(PrepareAnvilEvent event) {
        sessions.handlePrepare(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            sessions.handleClick(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            sessions.handleDrag(player, event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sessions.handleClose(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.handleClose(event.getPlayer());
    }
}
