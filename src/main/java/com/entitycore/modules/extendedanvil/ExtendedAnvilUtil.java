package com.entitycore.modules.extendedanvil;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ExtendedAnvilUtil {

    private ExtendedAnvilUtil() {}

    static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(" ");
            it.setItemMeta(im);
        }
        return it;
    }

    static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.YELLOW + name);
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(ChatColor.GRAY + line);
                im.setLore(colored);
            }
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(im);
        }
        return it;
    }

    static ItemStack info(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.AQUA + name);
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(ChatColor.GRAY + line);
                im.setLore(colored);
            }
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(im);
        }
        return it;
    }

    static boolean isEmptyBook(ItemStack it) {
        return it != null && it.getType() == Material.BOOK;
    }

    static boolean isEnchantedBook(ItemStack it) {
        if (it == null || it.getType() != Material.ENCHANTED_BOOK) return false;
        if (!(it.getItemMeta() instanceof EnchantmentStorageMeta esm)) return false;
        return !esm.getStoredEnchants().isEmpty();
    }

    static int countEmptyBooks(ItemStack[] contents, int fromSlot, int toSlotInclusive) {
        int c = 0;
        for (int i = fromSlot; i <= toSlotInclusive; i++) {
            ItemStack it = contents[i];
            if (isEmptyBook(it)) c += it.getAmount();
        }
        return c;
    }

    static ItemStack takeOneEmptyBook(InventoryViewAccessor inv, int fromSlot, int toSlotInclusive) {
        for (int i = fromSlot; i <= toSlotInclusive; i++) {
            ItemStack it = inv.getItem(i);
            if (isEmptyBook(it)) {
                ItemStack one = it.clone();
                one.setAmount(1);
                if (it.getAmount() <= 1) inv.setItem(i, null);
                else {
                    it.setAmount(it.getAmount() - 1);
                    inv.setItem(i, it);
                }
                return one;
            }
        }
        return null;
    }

    static ItemStack findFirstEnchantedBook(InventoryViewAccessor inv, int fromSlot, int toSlotInclusive) {
        for (int i = fromSlot; i <= toSlotInclusive; i++) {
            ItemStack it = inv.getItem(i);
            if (isEnchantedBook(it)) return it;
        }
        return null;
    }

    static String enchantKey(Enchantment e) {
        NamespacedKey key = e.getKey();
        return key == null ? e.getName().toLowerCase() : key.toString().toLowerCase();
    }

    static String prettyEnchantName(String key) {
        if (key == null) return "";
        String s = key;
        int idx = s.indexOf(':');
        if (idx >= 0 && idx < s.length() - 1) s = s.substring(idx + 1);
        s = s.replace('_', ' ');
        String[] parts = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return out.toString().trim();
    }

    static List<String> describeEnchantsOnBook(ItemStack enchantedBook) {
        List<String> lore = new ArrayList<>();
        if (!(enchantedBook.getItemMeta() instanceof EnchantmentStorageMeta esm)) return lore;
        for (Map.Entry<Enchantment, Integer> e : esm.getStoredEnchants().entrySet()) {
            lore.add(prettyEnchantName(enchantKey(e.getKey())) + " " + e.getValue());
        }
        return lore;
    }

    interface InventoryViewAccessor {
        ItemStack getItem(int slot);
        void setItem(int slot, ItemStack item);
    }
}
