package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class PriorityMenu {

    public static final String TITLE = "Disenchant Priority";
    public static final int SIZE = 54;

    public static final int SLOT_BACK = 45;
    public static final int SLOT_RESET = 53;

    public static Inventory create(org.bukkit.entity.Player player, ExtendedAnvilConfig cfg) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        render(inv, cfg);
        return inv;
    }

    public static boolean isThis(InventoryView view) {
        return view != null && TITLE.equals(view.getTitle());
    }

    public static void render(Inventory inv, ExtendedAnvilConfig cfg) {
        inv.clear();

        List<String> list = cfg.getPriorityKeys();
        int slot = 0;

        for (String key : list) {
            if (slot >= 45) break;
            inv.setItem(slot, entry(slot + 1, key));
            slot++;
        }

        inv.setItem(SLOT_BACK, button(Material.ARROW, "§eBack", List.of("§7Return to settings.")));
        inv.setItem(SLOT_RESET, button(Material.PAPER, "§cReset", List.of("§7Restore default priority list.")));

        // help
        inv.setItem(49, button(Material.OAK_SIGN, "§bHow to reorder", List.of(
                "§7Left Click: move up",
                "§7Right Click: move down",
                "§7Shift+Left: move to top",
                "§7Shift+Right: move to bottom"
        )));
    }

    public static EntryClick getClicked(int slot, ExtendedAnvilConfig cfg) {
        if (slot < 0 || slot >= 45) return null;
        List<String> list = cfg.getPriorityKeys();
        if (slot >= list.size()) return null;
        return new EntryClick(list.get(slot));
    }

    public static final class EntryClick {
        public final String key;
        EntryClick(String key) { this.key = key; }
    }

    private static ItemStack entry(int pos, String key) {
        return button(Material.ENCHANTED_BOOK, "§f#" + pos + " §a" + key, new ArrayList<>());
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

    private PriorityMenu() {}
}
