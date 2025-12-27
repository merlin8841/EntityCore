package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class AdminMenu {

    public static final String TITLE = "Extended Anvil Admin";

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, TITLE);

        inv.setItem(11, button(Material.ENCHANTED_BOOK, "§bCaps", List.of("§7Set max levels per enchant.")));
        inv.setItem(15, button(Material.ANVIL, "§dPriority", List.of("§7Set disenchant priority order.")));
        inv.setItem(26, button(Material.BARRIER, "§cClose", List.of()));

        player.openInventory(inv);
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private AdminMenu() {}
}
