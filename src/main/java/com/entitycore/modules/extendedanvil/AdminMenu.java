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
    public static final int SIZE = 27;

    public enum Click { CAPS, PRIORITY, TOGGLE_REFUNDS, CLOSE }

    public static Inventory create(Player player, ExtendedAnvilConfig cfg) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);

        inv.setItem(11, button(Material.ENCHANTED_BOOK, "§bCaps", List.of("§7Set max levels per enchant.")));
        inv.setItem(15, button(Material.ANVIL, "§dPriority", List.of("§7Set disenchant priority order.")));

        String state = cfg.isRefundsEnabled() ? "§aON" : "§cOFF";
        inv.setItem(13, button(Material.EXPERIENCE_BOTTLE, "§eRefunds: " + state, List.of("§7Toggle XP refund on disenchant.")));

        inv.setItem(26, button(Material.BARRIER, "§cClose", List.of()));

        return inv;
    }

    public static Click getClicked(int slot) {
        if (slot == 11) return Click.CAPS;
        if (slot == 15) return Click.PRIORITY;
        if (slot == 13) return Click.TOGGLE_REFUNDS;
        if (slot == 26) return Click.CLOSE;
        return null;
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
