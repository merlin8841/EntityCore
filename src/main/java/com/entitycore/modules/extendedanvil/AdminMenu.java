package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class AdminMenu {

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 9, "Extended Anvil Admin");

        inv.setItem(3, new ItemStack(Material.ANVIL));
        inv.setItem(5, new ItemStack(Material.ENCHANTED_BOOK));

        player.openInventory(inv);
    }

    private AdminMenu() {}
}
