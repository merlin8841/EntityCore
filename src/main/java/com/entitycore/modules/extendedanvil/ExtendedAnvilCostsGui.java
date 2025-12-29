package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ExtendedAnvilCostsGui {

    public static final String TITLE = "EA Costs & Returns";
    private static final int SIZE = 27;

    private static final int SLOT_BASE_COST = 10;
    private static final int SLOT_ADD_MULT = 12;

    private static final int SLOT_RET_FIRST = 14;
    private static final int SLOT_RET_SECOND = 16;
    private static final int SLOT_RET_THIRD = 18;

    private static final int SLOT_BACK = 22;

    // Touch controls
    private static final int SLOT_MODE_PAPER = 4;
    private static final int SLOT_PLUS_1 = 2;
    private static final int SLOT_MINUS_1 = 6;
    private static final int SLOT_PLUS_10 = 11;
    private static final int SLOT_MINUS_10 = 15;

    private ExtendedAnvilCostsGui() {}

    public static void open(Player player, JavaPlugin plugin, ExtendedAnvilConfig config) {
        Inventory inv = Bukkit.createInventory(new ExtendedAnvilAdminGui.Holder(player), SIZE, TITLE);
        build(inv, config, 1);
        player.openInventory(inv);
    }

    private static void build(Inventory inv, ExtendedAnvilConfig config, int deltaMode) {
        // deltaMode: 1, -1, 10, -10 (for ints). Percents always +/-0.05 based on sign.
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_BASE_COST, lore(Material.EXPERIENCE_BOTTLE, "Enchant base cost / level",
            List.of("Value: " + config.enchantBaseCostPerLevel(), "Tap this after setting delta")));

        inv.setItem(SLOT_ADD_MULT, lore(Material.ANVIL, "Enchant add-count multiplier",
            List.of("Value: " + config.enchantAddCountMultiplier(), "Tap this after setting delta")));

        inv.setItem(SLOT_RET_FIRST, lore(Material.EMERALD, "Disenchant return % (first time)",
            List.of("Value: " + pct(config.firstReturnPercent()), "Uses +/- 0.05")));

        inv.setItem(SLOT_RET_SECOND, lore(Material.GOLD_INGOT, "Disenchant return % (second same enchant)",
            List.of("Value: " + pct(config.secondSameEnchantReturnPercent()), "Uses +/- 0.05")));

        inv.setItem(SLOT_RET_THIRD, lore(Material.REDSTONE, "Disenchant return % (third+ same enchant)",
            List.of("Value: " + pct(config.thirdPlusSameEnchantReturnPercent()), "Uses +/- 0.05")));

        inv.setItem(SLOT_BACK, named(Material.ARROW, "Back"));

        // Touch delta buttons
        inv.setItem(SLOT_PLUS_1, named(Material.LIME_DYE, "Delta: +1"));
        inv.setItem(SLOT_MINUS_1, named(Material.RED_DYE, "Delta: -1"));
        inv.setItem(SLOT_PLUS_10, named(Material.LIME_DYE, "Delta: +10"));
        inv.setItem(SLOT_MINUS_10, named(Material.RED_DYE, "Delta: -10"));
        inv.setItem(SLOT_MODE_PAPER, lore(Material.PAPER, "Current Delta", List.of(String.valueOf(deltaMode))));
    }

    public static void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin, ExtendedAnvilConfig config) {
        if (!event.getView().getTitle().equals(TITLE)) return;

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Inventory top = event.getView().getTopInventory();
        int deltaMode = readDelta(top);
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            ExtendedAnvilAdminGui.open(player, plugin, config, new ExtendedAnvilService(plugin, config));
            click(player);
            return;
        }

        // Touch delta buttons
        if (slot == SLOT_PLUS_1) { build(top, config, 1); click(player); return; }
        if (slot == SLOT_MINUS_1) { build(top, config, -1); click(player); return; }
        if (slot == SLOT_PLUS_10) { build(top, config, 10); click(player); return; }
        if (slot == SLOT_MINUS_10) { build(top, config, -10); click(player); return; }

        if (slot == SLOT_BASE_COST) {
            int v = config.enchantBaseCostPerLevel() + deltaMode;
            config.setEnchantBaseCostPerLevel(v);
            config.save();
            build(top, config, deltaMode);
            click(player);
            return;
        }

        if (slot == SLOT_ADD_MULT) {
            int v = config.enchantAddCountMultiplier() + deltaMode;
            config.setEnchantAddCountMultiplier(v);
            config.save();
            build(top, config, deltaMode);
            click(player);
            return;
        }

        // Percents: only use sign of deltaMode (+/-)
        double step = (deltaMode >= 0) ? 0.05 : -0.05;

        if (slot == SLOT_RET_FIRST) {
            config.setFirstReturnPercent(clamp01(config.firstReturnPercent() + step));
            config.save();
            build(top, config, deltaMode);
            click(player);
            return;
        }

        if (slot == SLOT_RET_SECOND) {
            config.setSecondSameEnchantReturnPercent(clamp01(config.secondSameEnchantReturnPercent() + step));
            config.save();
            build(top, config, deltaMode);
            click(player);
            return;
        }

        if (slot == SLOT_RET_THIRD) {
            config.setThirdPlusSameEnchantReturnPercent(clamp01(config.thirdPlusSameEnchantReturnPercent() + step));
            config.save();
            build(top, config, deltaMode);
            click(player);
            return;
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

    private static String pct(double v) {
        return Math.round(v * 100.0) + "%";
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
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
