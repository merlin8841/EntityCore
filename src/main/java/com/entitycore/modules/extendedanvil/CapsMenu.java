package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class CapsMenu {

    public static final String TITLE = "Extended Anvil Caps";
    public static final int SIZE = 54;
    private static final int PAGE_SIZE = 45; // 0..44

    public enum ClickType { ENTRY, PAGE, BACK }
    public static final class Click {
        public final ClickType type;
        public final String enchantKey;
        public final int page;

        public Click(ClickType type, String enchantKey, int page) {
            this.type = type;
            this.enchantKey = enchantKey;
            this.page = page;
        }
    }

    public static Inventory create(Player player, ExtendedAnvilConfig cfg, int page) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        render(inv, cfg, page);
        return inv;
    }

    public static void render(Inventory inv, ExtendedAnvilConfig cfg, int page) {
        inv.clear();

        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) all.add(e);
        all.sort(Comparator.comparing(a -> a.getKey().toString()));

        int start = page * PAGE_SIZE;
        int end = Math.min(all.size(), start + PAGE_SIZE);

        for (int i = start; i < end; i++) {
            Enchantment e = all.get(i);
            String key = e.getKey().toString();
            int cap = cfg.getCap(key);

            int slot = i - start; // 0..44
            inv.setItem(slot, capItem(key, cap));
        }

        inv.setItem(45, button(Material.ARROW, "§eBack", List.of("§7Return to admin menu.")));
        inv.setItem(53, button(Material.BARRIER, "§cClose", List.of()));

        if (start > 0) inv.setItem(48, button(Material.PAPER, "§fPrev Page", List.of("§7Page: §f" + (page + 1))));
        if (end < all.size()) inv.setItem(50, button(Material.PAPER, "§fNext Page", List.of("§7Page: §f" + (page + 1))));

        inv.setItem(49, button(Material.BOOK, "§bHow to edit", List.of(
                "§7Left click: -1",
                "§7Right click: +1",
                "§7Shift + Left: -10",
                "§7Shift + Right: +10"
        )));
    }

    private static ItemStack capItem(String key, int cap) {
        ItemStack it = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b" + key);
            meta.setLore(List.of("§7Cap: §f" + cap));
            it.setItemMeta(meta);
        }
        return it;
    }

    public static Click getClicked(Inventory inv, int rawSlot, int page) {
        if (rawSlot == 45) return new Click(ClickType.BACK, null, page);
        if (rawSlot == 48) return new Click(ClickType.PAGE, null, Math.max(0, page - 1));
        if (rawSlot == 50) return new Click(ClickType.PAGE, null, page + 1);

        if (rawSlot >= 0 && rawSlot < PAGE_SIZE) {
            ItemStack it = inv.getItem(rawSlot);
            if (it == null) return null;
            ItemMeta meta = it.getItemMeta();
            if (meta == null || meta.getDisplayName() == null) return null;

            String name = meta.getDisplayName();
            if (!name.startsWith("§b")) return null;

            String key = name.substring(2);
            return new Click(ClickType.ENTRY, key, page);
        }

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

    private CapsMenu() {}
}
