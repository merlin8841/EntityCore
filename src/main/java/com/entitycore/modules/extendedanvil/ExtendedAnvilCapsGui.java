package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ExtendedAnvilCapsGui {

    public static final String TITLE = "EA Enchant Caps";

    private static final int SIZE = 54;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_PAGE = 48;
    private static final int SLOT_BACK = 49;

    // Touch controls
    private static final int SLOT_MODE_PAPER = 52;
    private static final int SLOT_PLUS_1 = 46;
    private static final int SLOT_MINUS_1 = 47;
    private static final int SLOT_PLUS_10 = 50;
    private static final int SLOT_MINUS_10 = 51;

    private ExtendedAnvilCapsGui() {}

    public static void open(Player player, JavaPlugin plugin, ExtendedAnvilConfig config) {
        Inventory inv = Bukkit.createInventory(new ExtendedAnvilAdminGui.Holder(player), SIZE, TITLE);
        build(inv, config, 0, 1);
        player.openInventory(inv);
    }

    private static void build(Inventory inv, ExtendedAnvilConfig config, int page, int delta) {
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
                    "Tap to apply delta"
                )
            );
            inv.setItem(slot, it);
        }

        inv.setItem(SLOT_PREV, named(Material.ARROW, "Prev Page"));
        inv.setItem(SLOT_NEXT, named(Material.ARROW, "Next Page"));
        inv.setItem(SLOT_BACK, named(Material.ARROW, "Back"));
        inv.setItem(SLOT_PAGE, lore(Material.PAPER, "Page", List.of(String.valueOf(page))));

        // Touch control buttons
        inv.setItem(SLOT_PLUS_1, named(Material.LIME_DYE, "Set Delta: +1"));
        inv.setItem(SLOT_MINUS_1, named(Material.RED_DYE, "Set Delta: -1"));
        inv.setItem(SLOT_PLUS_10, named(Material.LIME_DYE, "Set Delta: +10"));
        inv.setItem(SLOT_MINUS_10, named(Material.RED_DYE, "Set Delta: -10"));
        inv.setItem(SLOT_MODE_PAPER, lore(Material.PAPER, "Current Delta", List.of(String.valueOf(delta))));
    }

    public static void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin, ExtendedAnvilConfig config) {
        if (!event.getView().getTitle().equals(TITLE)) return;

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Inventory top = event.getView().getTopInventory();
        int page = readPage(top);
        int delta = readDelta(top);
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            ExtendedAnvilAdminGui.open(player, plugin, config, new ExtendedAnvilService(plugin, config));
            click(player);
            return;
        }

        if (slot == SLOT_PREV) {
            page = Math.max(0, page - 1);
            build(top, config, page, delta);
            click(player);
            return;
        }

        if (slot == SLOT_NEXT) {
            page = page + 1;
            build(top, config, page, delta);
            click(player);
            return;
        }

        // Touch delta buttons
        if (slot == SLOT_PLUS_1) { build(top, config, page, 1); click(player); return; }
        if (slot == SLOT_MINUS_1) { build(top, config, page, -1); click(player); return; }
        if (slot == SLOT_PLUS_10) { build(top, config, page, 10); click(player); return; }
        if (slot == SLOT_MINUS_10) { build(top, config, page, -10); click(player); return; }

        // Enchant entries
        if (slot >= 0 && slot < 45) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.ENCHANTED_BOOK) return;

            String key = nameOf(clicked);
            if (key == null) return;

            Enchantment ench = Enchantment.getByKey(org.bukkit.NamespacedKey.fromString(key));
            if (ench == null) return;

            int cap = config.capFor(ench);
            cap += delta;
            if (cap < 0) cap = 0;

            config.setCapFor(ench, cap);
            config.save();

            build(top, config, page, delta);
            click(player);
        }
    }

    private static int readPage(Inventory inv) {
        ItemStack paper = inv.getItem(SLOT_PAGE);
        if (paper == null) return 0;
        ItemMeta meta = paper.getItemMeta();
        if (meta == null || meta.getLore() == null || meta.getLore().isEmpty()) return 0;
        try {
            return Integer.parseInt(meta.getLore().get(0));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int readDelta(Inventory inv) {
        ItemStack paper = inv.getItem(SLOT_MODE_PAPER);
        if (paper == null) return 1;
        ItemMeta meta = paper.getItemMeta();
        if (meta == null || meta.getLore() == null || meta.getLore().isEmpty()) return 1;
        try {
            return Integer.parseInt(meta.getLore().get(0));
        } catch (Exception ignored) {
            return 1;
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
