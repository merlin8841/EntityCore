package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class CapsMenu {

    public static final String TITLE = "Enchantment Caps";
    public static final int SIZE = 54;

    public static final int SLOT_BACK = 45;

    public static Inventory create(org.bukkit.entity.Player player, JavaPlugin plugin, ExtendedAnvilConfig cfg, int page) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        render(inv, plugin, cfg, page);
        return inv;
    }

    public static boolean isThis(InventoryView view) {
        return view != null && TITLE.equals(view.getTitle());
    }

    public static void render(Inventory inv, JavaPlugin plugin, ExtendedAnvilConfig cfg, int page) {
        inv.clear();

        // Simple: show first ~45 enchants alphabetically by key
        List<Enchantment> enchants = new ArrayList<>(Enchantment.values());
        enchants.sort((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()));

        int start = page * 45;
        int end = Math.min(enchants.size(), start + 45);

        int slot = 0;
        for (int i = start; i < end; i++) {
            Enchantment e = enchants.get(i);
            String k = e.getKey().toString();
            int cap = cfg.getCapFor(k, e.getMaxLevel());

            inv.setItem(slot, capEntry(plugin, k, e.getMaxLevel(), cap));
            slot++;
        }

        inv.setItem(SLOT_BACK, button(Material.ARROW, "§eBack", List.of("§7Return to settings.")));

        inv.setItem(49, button(Material.OAK_SIGN, "§bHow to edit", List.of(
                "§7Left Click: -1",
                "§7Right Click: +1",
                "§7Shift+Left: -10",
                "§7Shift+Right: +10",
                "§7Cap 0 = disabled"
        )));
    }

    public static Click getClicked(Inventory inv, int rawSlot, int page) {
        if (rawSlot < 0 || rawSlot >= 45) return null;
        ItemStack it = inv.getItem(rawSlot);
        if (it == null || it.getType() == Material.AIR) return null;

        ItemMeta meta = it.getItemMeta();
        if (meta == null || meta.getLore() == null) return null;

        for (String line : meta.getLore()) {
            if (line != null && line.startsWith("KEY=")) {
                String key = line.substring("KEY=".length());
                return new Click(key, page);
            }
        }
        return null;
    }

    public static final class Click {
        public final String enchantKey;
        public final int page;
        Click(String enchantKey, int page) {
            this.enchantKey = enchantKey;
            this.page = page;
        }
    }

    private static ItemStack capEntry(JavaPlugin plugin, String key, int vanillaMax, int cap) {
        ItemStack it = new ItemStack(Material.ENCHANTING_TABLE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a" + key);
            meta.setLore(List.of(
                    "§7Vanilla max: §f" + vanillaMax,
                    "§7Cap: §f" + cap,
                    "§8KEY=" + key
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

    private CapsMenu() {}
}
