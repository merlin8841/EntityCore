package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class CapsMenu {

    public static final String TITLE = "Extended Anvil: Caps";
    public static final int SIZE = 54;

    public static final int SLOT_BACK = 45;
    public static final int SLOT_PAGE_INFO = 49;

    // 0..44 are list entries, 46/52 could be prev/next if you already had them; keep simple here:
    public static final int SLOT_PREV = 46;
    public static final int SLOT_NEXT = 52;

    private static final int PAGE_SIZE = 45;

    public static Inventory create(org.bukkit.entity.Player player, JavaPlugin plugin, ExtendedAnvilConfig cfg, int page) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        render(inv, plugin, cfg, page);
        return inv;
    }

    public static boolean isThis(InventoryView view) {
        return view != null && TITLE.equals(view.getTitle());
    }

    public static void render(Inventory inv, JavaPlugin plugin, ExtendedAnvilConfig cfg, int page) {
        if (inv == null) return;

        // Fill list
        List<Enchantment> enchants = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            if (e != null) enchants.add(e);
        }
        enchants.sort(Comparator.comparing(e -> e.getKey().toString()));

        int maxPage = Math.max(0, (enchants.size() - 1) / PAGE_SIZE);
        int p = Math.max(0, Math.min(maxPage, page));

        // Clear
        for (int i = 0; i < SIZE; i++) inv.setItem(i, null);

        int start = p * PAGE_SIZE;
        int end = Math.min(enchants.size(), start + PAGE_SIZE);

        int slot = 0;
        for (int i = start; i < end; i++) {
            Enchantment e = enchants.get(i);
            String key = e.getKey().toString();
            int cap = cfg.getCap(key);

            inv.setItem(slot++, capItem(key, cap));
        }

        inv.setItem(SLOT_BACK, button(Material.BARRIER, "§cBack", List.of("§7Return to admin menu.")));
        inv.setItem(SLOT_PREV, button(Material.ARROW, "§ePrev", List.of("§7Page " + (p + 1) + " / " + (maxPage + 1))));
        inv.setItem(SLOT_NEXT, button(Material.ARROW, "§eNext", List.of("§7Page " + (p + 1) + " / " + (maxPage + 1))));
        inv.setItem(SLOT_PAGE_INFO, button(Material.PAPER, "§bPage", List.of("§7" + (p + 1) + " / " + (maxPage + 1))));
    }

    public static Click getClicked(Inventory inv, int rawSlot, int currentPage) {
        if (rawSlot < 0 || rawSlot >= inv.getSize()) return null;

        if (rawSlot == SLOT_PREV) return new Click(null, Math.max(0, currentPage - 1), ClickType.PAGE);
        if (rawSlot == SLOT_NEXT) return new Click(null, currentPage + 1, ClickType.PAGE);
        if (rawSlot == SLOT_BACK) return new Click(null, currentPage, ClickType.BACK);

        if (rawSlot >= 0 && rawSlot < PAGE_SIZE) {
            ItemStack it = inv.getItem(rawSlot);
            if (it == null || it.getType() == Material.AIR) return null;

            ItemMeta meta = it.getItemMeta();
            if (meta == null || meta.getLore() == null) return null;

            // lore line: "§7Key: §f<namespaced>"
            String key = null;
            for (String line : meta.getLore()) {
                if (line != null && line.startsWith("§7Key: §f")) {
                    key = line.substring("§7Key: §f".length()).trim();
                    break;
                }
            }
            if (key == null || key.isEmpty()) return null;

            return new Click(key, currentPage, ClickType.ENTRY);
        }

        return null;
    }

    private static ItemStack capItem(String key, int cap) {
        ItemStack it = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d" + key);
            meta.setLore(List.of(
                    "§7Key: §f" + key,
                    "§7Cap: §f" + cap,
                    "§7Left click: §a-1",
                    "§7Right click: §a+1",
                    "§7Shift+Left: §a-10",
                    "§7Shift+Right: §a+10"
            ));
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

    public static final class Click {
        public final String enchantKey; // only set when ENTRY
        public final int page;
        public final ClickType type;

        public Click(String enchantKey, int page, ClickType type) {
            this.enchantKey = enchantKey;
            this.page = page;
            this.type = type;
        }
    }

    public enum ClickType {
        ENTRY, PAGE, BACK
    }

    private CapsMenu() {}
}
