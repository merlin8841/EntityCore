package com.entitycore.modules.extendedanvil;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
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
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof ExtendedAnvilGui.Holder) {
            ExtendedAnvilGui.handleClick(player, event, plugin, config, service);
            return;
        }

        if (holder instanceof ExtendedAnvilAdminGui.Holder) {
            String title = event.getView().getTopInventory().getTitle();

            if (title.equals("EA Admin")) {
                ExtendedAnvilAdminGui.handleClick(player, event, plugin, config, service);
                return;
            }

            if (title.equals("EA Costs & Returns")) {
                ExtendedAnvilCostsGui.handleClick(player, event, plugin, config);
                return;
            }

            if (title.equals("EA Enchant Caps")) {
                ExtendedAnvilCapsGui.handleClick(player, event, plugin, config);
                return;
            }

            if (title.equals("EA Disenchant Priority")) {
                ExtendedAnvilPriorityGui.handleClick(player, event, plugin, config);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ExtendedAnvilGui.Holder) {
            ExtendedAnvilGui.handleDrag(event);
            return;
        }
        if (holder instanceof ExtendedAnvilAdminGui.Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ExtendedAnvilGui.Holder) {
            ExtendedAnvilGui.handleClose(player, event, plugin, config);
        } else if (holder instanceof ExtendedAnvilAdminGui.Holder) {
            ExtendedAnvilAdminGui.handleClose(player, event, plugin, config);
        }
    }
}
