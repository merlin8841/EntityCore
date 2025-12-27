package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class PriorityMenu {

    public static final String TITLE = "Disenchant Priority";
    public static final int SIZE = 54;

    public enum ClickType { MOVE_UP, MOVE_DOWN, BACK }
    public static final class Click {
        public final ClickType type;
        public final int index;

        public Click(ClickType type, int index) {
            this.type = type;
            this.index = index;
        }
    }

    public static Inventory create(Player player, ExtendedAnvilConfig cfg) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        render(inv, cfg);
        return inv;
    }

    public static void render(Inventory inv, ExtendedAnvilConfig cfg) {
        inv.clear();

        List<String> list = cfg.getPriorityKeys();

        int max = Math.min(45, list.size());
        for (int i = 0; i < max; i++) {
            String key = list.get(i);
            inv.setItem(i, entryItem(i, key));
        }

        inv.setItem(45, button(Material.ARROW, "§eBack", List.of("§7Return to admin menu.")));
        inv.setItem(53, button(Material.BARRIER, "§cClose", List.of()));
    }

    private static ItemStack entryItem(int index, String key) {
        ItemStack it = new ItemStack(Material.ANVIL, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d#" + (index + 1) + " §f" + key);
            meta.setLore(List.of(
                    "§7Left click: move up",
                    "§7Right click: move down"
            ));
            it.setItemMeta(meta);
        }
        return it;
    }

    public static Click getClicked(Inventory inv, int slot) {
        if (slot == 45) return new Click(ClickType.BACK, -1);

        if (slot >= 0 && slot < 45) {
            ItemStack it = inv.getItem(slot);
            if (it == null) return null;

            // direction is decided by click in SessionManager (left/right),
            // here we just say "index"
            return new Click(ClickType.MOVE_UP, slot);
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

    private PriorityMenu() {}
}
