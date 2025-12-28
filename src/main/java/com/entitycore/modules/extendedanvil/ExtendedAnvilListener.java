package com.entitycore.modules.extendedanvil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilListener implements Listener {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;
    private final ExtendedAnvilService service;

    public ExtendedAnvilListener(JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof ExtendedAnvilGui.Holder) {
            ExtendedAnvilGui.handleClick((Player) event.getWhoClicked(), event, plugin, config, service);
        } else if (holder instanceof ExtendedAnvilAdminGui.Holder) {
            ExtendedAnvilAdminGui.handleClick((Player) event.getWhoClicked(), event, plugin, config, service);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof ExtendedAnvilGui.Holder) {
            ExtendedAnvilGui.handleDrag(event);
        } else if (holder instanceof ExtendedAnvilAdminGui.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof ExtendedAnvilGui.Holder) {
            ExtendedAnvilGui.handleClose((Player) event.getPlayer(), event, plugin, config);
        } else if (holder instanceof ExtendedAnvilAdminGui.Holder) {
            ExtendedAnvilAdminGui.handleClose((Player) event.getPlayer(), event, plugin, config);
        }
    }
}
