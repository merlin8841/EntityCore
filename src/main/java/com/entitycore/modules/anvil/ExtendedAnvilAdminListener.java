package com.entitycore.modules.anvil;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilAdminListener implements Listener {

    private final JavaPlugin plugin;
    private final ExtendedAnvilAdminMenu menu;

    public ExtendedAnvilAdminListener(JavaPlugin plugin, ExtendedAnvilAdminMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!ExtendedAnvilAdminMenu.TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            player.closeInventory();
            return;
        }

        Inventory inv = event.getView().getTopInventory();
        int slot = event.getRawSlot();

        if (slot == ExtendedAnvilAdminMenu.SLOT_CLOSE) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        boolean changed = false;

        if (slot == ExtendedAnvilAdminMenu.SLOT_FIRST_DOWN) changed = menu.adjust(inv, "extendedanvil.refund.first", -0.05);
        if (slot == ExtendedAnvilAdminMenu.SLOT_FIRST_UP)   changed = menu.adjust(inv, "extendedanvil.refund.first", +0.05);

        if (slot == ExtendedAnvilAdminMenu.SLOT_SECOND_DOWN) changed = menu.adjust(inv, "extendedanvil.refund.second", -0.05);
        if (slot == ExtendedAnvilAdminMenu.SLOT_SECOND_UP)   changed = menu.adjust(inv, "extendedanvil.refund.second", +0.05);

        if (slot == ExtendedAnvilAdminMenu.SLOT_THIRD_DOWN) changed = menu.adjust(inv, "extendedanvil.refund.thirdPlus", -0.05);
        if (slot == ExtendedAnvilAdminMenu.SLOT_THIRD_UP)   changed = menu.adjust(inv, "extendedanvil.refund.thirdPlus", +0.05);

        if (slot == ExtendedAnvilAdminMenu.SLOT_RESET) {
            plugin.getConfig().set("extendedanvil.refund.first", 0.75);
            plugin.getConfig().set("extendedanvil.refund.second", 0.25);
            plugin.getConfig().set("extendedanvil.refund.thirdPlus", 0.0);
            plugin.saveConfig();
            menu.render(inv);
            changed = true;
        }

        if (changed) {
            plugin.saveConfig();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }
}
