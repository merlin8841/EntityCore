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
        if (m != null) {
            m.setDisplayName(" ");
            glass.setItemMeta(m);
        }

        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass);

        inv.setItem(SLOT_ITEM, null);
        inv.setItem(SLOT_BOOK, null);
        inv.setItem(SLOT_RESULT, null);
    }

    public static void refreshPreview(Player player,
                                      Inventory inv,
                                      ExtendedAnvilConfig config,
                                      EnchantCostService costService) {

        inv.setItem(SLOT_RESULT, null);
        lastCost.put(inv, 0);

        ItemStack item = inv.getItem(SLOT_ITEM);
        ItemStack book = inv.getItem(SLOT_BOOK);

        if (item == null || item.getType() == Material.AIR) return;
        if (book == null || book.getType() == Material.AIR) return;

        // Disenchant
        if (book.getType() == Material.BOOK) {
            Map<Enchantment, Integer> ench = item.getEnchantments();
            if (ench == null || ench.isEmpty()) return;

            boolean removeAll = book.getAmount() == 1;

            LinkedHashMap<Enchantment, Integer> removed = new LinkedHashMap<>();
            if (removeAll) {
                // all enchants (sorted)
                List<Enchantment> list = new ArrayList<>(ench.keySet());
                list.sort(Comparator.comparing(e -> e.getKey().toString()));
                for (Enchantment e : list) removed.put(e, ench.get(e));
            } else {
                Enchantment next = config.chooseNextDisenchant(ench.keySet());
                if (next == null) return;
                removed.put(next, ench.get(next));
            }

            ItemStack out = new ItemStack(Material.ENCHANTED_BOOK, 1);
            ItemMeta meta = out.getItemMeta();
            if (meta instanceof EnchantmentStorageMeta esm) {
                for (Map.Entry<Enchantment, Integer> e : removed.entrySet()) {
                    esm.addStoredEnchant(e.getKey(), e.getValue(), false);
                }
                out.setItemMeta(esm);
            }

            inv.setItem(SLOT_RESULT, out);

            // cost placeholder (disenchant cost is handled by refund service later)
            lastCost.put(inv, 1);
            return;
        }

        // Apply enchanted book
        if (book.getType() == Material.ENCHANTED_BOOK) {
            EnchantCostService.ApplyPreview p = costService.previewApply(player, item, book);
            if (!p.canApply || p.result == null) return;

            inv.setItem(SLOT_RESULT, p.result.clone());
            lastCost.put(inv, Math.max(0, p.levelCost));
        }
    }

    public static int getLastCost(Inventory inv) {
        return lastCost.getOrDefault(inv, 0);
    }

    private PlayerMenu() {}
}
