package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class PriorityMenu {

    public static void open(Player player, ExtendedAnvilConfig config) {
        Inventory inv = Bukkit.createInventory(player, 54, "Disenchant Priority");
        player.openInventory(inv);
    }

    private PriorityMenu() {}
}
