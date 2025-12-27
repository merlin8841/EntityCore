package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public final class CapsMenu {

    public static void open(Player player, ExtendedAnvilConfig config) {
        Inventory inv = Bukkit.createInventory(player, 27, "Enchant Caps");
        player.openInventory(inv);
    }

    private CapsMenu() {}
}
