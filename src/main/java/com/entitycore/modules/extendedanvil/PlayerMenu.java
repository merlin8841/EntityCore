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

    // three functional slots
    public static final int SLOT_ITEM = 11;
    public static final int SLOT_BOOK = 15;
    public static final int SLOT_OUTPUT = 13;

    public static final int SLOT_CLOSE = 26;

    public enum Mode { NONE, DISENCHANT, APPLY }

    public static Inventory create(Player player) {
        return Bukkit.createInventory(player, SIZE, TITLE);
    }

    public static void renderStatic(Inventory inv) {
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_ITEM, null);
        inv.setItem(SLOT_BOOK, null);
        inv.setItem(SLOT_OUTPUT, null);

        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cClose", List.of()));
    }

    public static boolean isInputSlot(int slot) {
        return slot == SLOT_ITEM || slot == SLOT_BOOK;
    }

    public static Mode inferMode(Inventory inv) {
        if (inv == null) return Mode.NONE;
        ItemStack right = inv.getItem(SLOT_BOOK);
        if (right == null || right.getType() == Material.AIR) return Mode.NONE;
        if (right.getType() == Material.BOOK) return Mode.DISENCHANT;
        if (right.getType() == Material.ENCHANTED_BOOK) return Mode.APPLY;
        return Mode.NONE;
    }

    public static ItemStack previewDisenchantBook(LinkedHashMap<Enchantment, Integer> removed, boolean removeAll) {
        ItemStack out = buildEnchantedBook(removed);
        ItemMeta meta = out.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Mode: §bDisenchant",
                    "§7Consumes: §f1 book",
                    removeAll ? "§7Removes: §fall enchants" : "§7Removes: §fone enchant (priority)",
                    "§7Click result to craft."
            ));
            out.setItemMeta(meta);
        }
        return out;
    }

    public static ItemStack previewApplyResult(ItemStack result, int cost) {
        ItemStack out = result.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Mode: §dApply",
                    "§7Cost: §f" + cost + " levels",
                    "§7Click result to craft."
            ));
            out.setItemMeta(meta);
        }
        return out;
    }

    public static LinkedHashMap<Enchantment, Integer> sortedAllForBook(Map<Enchantment, Integer> ench) {
        List<Enchantment> list = new ArrayList<>(ench.keySet());
        list.sort(Comparator.comparing(a -> a.getKey().toString()));
        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<>();
        for (Enchantment e : list) out.put(e, ench.get(e));
        return out;
    }

    public static ItemStack buildEnchantedBook(LinkedHashMap<Enchantment, Integer> enchants) {
        ItemStack out = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = out.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta esm = (EnchantmentStorageMeta) meta;
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                esm.addStoredEnchant(e.getKey(), e.getValue(), false);
            }
            out.setItemMeta(esm);
        }
        return out;
    }

    public static ItemStack errorItem(String msg) {
        ItemStack it = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c" + msg);
            meta.setLore(List.of("§7Fix inputs and try again."));
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack pane(Material mat, String name) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
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

    private PlayerMenu() {}
}
