package com.entitycore.modules.hoppers;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopperFiltersAdminListener implements Listener {

    private final JavaPlugin plugin;
    private final HopperFiltersAdminMenu menu;
    private final HopperFiltersListener hopperListener;

    public HopperFiltersAdminListener(JavaPlugin plugin, HopperFiltersAdminMenu menu, HopperFiltersListener hopperListener) {
        this.plugin = plugin;
        this.menu = menu;
        this.hopperListener = hopperListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!HopperFiltersAdminMenu.TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);

        Inventory inv = event.getView().getTopInventory();
        int slot = event.getRawSlot();

        if (slot == HopperFiltersAdminMenu.SLOT_CLOSE) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (!player.hasPermission("entitycore.hopperfilters.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            player.closeInventory();
            return;
        }

        if (slot == HopperFiltersAdminMenu.SLOT_DECREASE) {
            menu.slower(player, inv);
            saveInterval();
            return;
        }

        if (slot == HopperFiltersAdminMenu.SLOT_INCREASE) {
            menu.faster(player, inv);
            saveInterval();
            return;
        }

        if (slot == HopperFiltersAdminMenu.SLOT_RESET) {
            menu.reset(player, inv);
            saveInterval();
        }
    }

    private void saveInterval() {
        plugin.getConfig().set("hopperfilters.tick-interval", hopperListener.getTickInterval());
        plugin.saveConfig();
    }
}
