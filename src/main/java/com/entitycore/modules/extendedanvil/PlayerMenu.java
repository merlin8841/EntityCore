package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class PlayerMenu {

    public static final String TITLE = "Extended Anvil";
    public static final int SIZE = 27;

    public static final int SLOT_ITEM = 10;
    public static final int SLOT_BOOK = 12;
    public static final int SLOT_RESULT = 16;

    private static final Map<Inventory, Integer> lastCost = new HashMap<>();

    public static Inventory create(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        decorate(inv);
        return inv;
    }

    public static void decorate(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = glass.getItemMeta();
        m.setDisplayName(" ");
        glass.setItemMeta(m);

        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, glass);
        }

        inv.setItem(SLOT_ITEM, null);
        inv.setItem(SLOT_BOOK, null);
        inv.setItem(SLOT_RESULT, null);
    }

    public static void refreshPreview(
            Player player,
            Inventory inv,
            ExtendedAnvilConfig config,
            EnchantCostService costService
    ) {
        inv.setItem(SLOT_RESULT, null);
        lastCost.put(inv, 0);

        ItemStack item = inv.getItem(SLOT_ITEM);
        ItemStack book = inv.getItem(SLOT_BOOK);

        if (item == null || book == null) return;

        if (book.getType() == Material.BOOK) {
            Map<Enchantment, Integer> ench = item.getEnchantments();
            if (ench.isEmpty()) return;

            Enchantment target = config.chooseNextDisenchant(ench.keySet());
            if (target == null) return;

            ItemStack out = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) out.getItemMeta();
            meta.addStoredEnchant(target, ench.get(target), false);
            out.setItemMeta(meta);

            inv.setItem(SLOT_RESULT, out);
            lastCost.put(inv, 1);
            return;
        }

        if (book.getType() == Material.ENCHANTED_BOOK) {
            EnchantCostService.ApplyPreview preview =
                    costService.previewApply(player, item, book);

            if (!preview.canApply) return;

            inv.setItem(SLOT_RESULT, preview.result.clone());
            lastCost.put(inv, preview.levelCost);
        }
    }

    public static int getLastCost(Inventory inv) {
        return lastCost.getOrDefault(inv, 0);
    }

    private PlayerMenu() {}
}
