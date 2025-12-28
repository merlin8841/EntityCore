package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ExtendedAnvilCapsGui {

    private static final int SIZE = 54;
    private static final int SLOT_BACK = 49;

    private ExtendedAnvilCapsGui() {}

    public static void open(Player player, JavaPlugin plugin, ExtendedAnvilConfig config) {
        Inventory inv = Bukkit.createInventory(new ExtendedAnvilAdminGui.Holder(player), SIZE, "EA Enchant Caps");
        build(inv, config, 0);
        player.openInventory(inv);
    }

    private static void build(Inventory inv, ExtendedAnvilConfig config, int page) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        List<Enchantment> enchants = new ArrayList<>();
        for (Enchantment e : Enchantment.values()) {
            if (e != null && e.getKey() != null) enchants.add(e);
        }
        enchants.sort(Comparator.comparing(e -> e.getKey().toString()));

        int perPage = 45;
        int start = page * perPage;
        int end = Math.min(enchants.size(), start + perPage);

        for (int i = start; i < end; i++) {
            Enchantment e = enchants.get(i);
            int slot = i - start;
            int cap = config.capFor(e);

            ItemStack it = lore(Material.ENCHANTED_BOOK,
                e.getKey().toString(),
                List.of(
                    "Cap: " + cap,
                    "Left +1, Right -1",
                    "Shift +10/-10"
                )
            );
            inv.setItem(slot, it);
        }

        inv.setItem(45, named(Material.ARROW, "Prev Page"));
        inv.setItem(53, named(Material.ARROW, "Next Page"));
        inv.setItem(SLOT_BACK, named(Material.ARROW, "Back"));

        // store page in item meta (simple + safe)
        inv.setItem(48, lore(Material.PAPER, "Page",
            List.of(String.valueOf(page)))
        );
    }

    public static void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin, ExtendedAnvilConfig config) {
        if (!event.getView().getTopInventory().getTitle().equals("EA Enchant Caps")) return;

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        int page = readPage(event.getView().getTopInventory());
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            ExtendedAnvilAdminGui.open(player, plugin, config, new ExtendedAnvilService(plugin, config));
            return;
        }

        if (slot == 45) {
            page = Math.max(0, page - 1);
            build(event.getInventory(), config, page);
            click(player);
            return;
        }

        if (slot == 53) {
            page = page + 1;
            build(event.getInventory(), config, page);
            click(player);
            return;
        }

        if (slot >= 0 && slot < 45) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.ENCHANTED_BOOK) return;

            String key = nameOf(clicked);
            if (key == null) return;

            Enchantment ench = Enchantment.getByKey(org.bukkit.NamespacedKey.fromString(key));
            if (ench == null) return;

            int delta = event.isShiftClick() ? 10 : 1;
            int cap = config.capFor(ench);

            if (event.isLeftClick()) cap += delta;
            if (event.isRightClick()) cap -= delta;

            if (cap < 0) cap = 0;

            config.setCapFor(ench, cap);
            config.save();

            build(event.getInventory(), config, page);
            click(player);
        }
    }

    private static int readPage(Inventory inv) {
        ItemStack paper = inv.getItem(48);
        if (paper == null) return 0;
        ItemMeta meta = paper.getItemMeta();
        if (meta == null || meta.getLore() == null || meta.getLore().isEmpty()) return 0;
        try {
            return Integer.parseInt(meta.getLore().get(0));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    private static String nameOf(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getDisplayName();
    }

    private static ItemStack named(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack lore(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }
}
