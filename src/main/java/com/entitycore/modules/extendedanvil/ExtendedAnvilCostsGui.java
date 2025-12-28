package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ExtendedAnvilCostsGui {

    private static final int SIZE = 27;

    private static final int SLOT_BASE_COST = 10;
    private static final int SLOT_ADD_MULT = 12;

    private static final int SLOT_RET_FIRST = 14;
    private static final int SLOT_RET_SECOND = 16;
    private static final int SLOT_RET_THIRD = 18;

    private static final int SLOT_BACK = 22;

    private ExtendedAnvilCostsGui() {}

    public static void open(Player player, JavaPlugin plugin, ExtendedAnvilConfig config) {
        Inventory inv = Bukkit.createInventory(new ExtendedAnvilAdminGui.Holder(player), SIZE, "EA Costs & Returns");
        build(inv, config);
        player.openInventory(inv);
    }

    private static void build(Inventory inv, ExtendedAnvilConfig config) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_BASE_COST, lore(Material.EXPERIENCE_BOTTLE, "Enchant base cost / level",
            List.of("Value: " + config.enchantBaseCostPerLevel(), "Left +1, Right -1, Shift +10/-10")));

        inv.setItem(SLOT_ADD_MULT, lore(Material.ANVIL, "Enchant add-count multiplier",
            List.of("Value: " + config.enchantAddCountMultiplier(), "Left +1, Right -1, Shift +10/-10")));

        inv.setItem(SLOT_RET_FIRST, lore(Material.EMERALD, "Disenchant return % (first time)",
            List.of("Value: " + pct(config.firstReturnPercent()), "Left +0.05, Right -0.05")));

        inv.setItem(SLOT_RET_SECOND, lore(Material.GOLD_INGOT, "Disenchant return % (second same enchant)",
            List.of("Value: " + pct(config.secondSameEnchantReturnPercent()), "Left +0.05, Right -0.05")));

        inv.setItem(SLOT_RET_THIRD, lore(Material.REDSTONE, "Disenchant return % (third+ same enchant)",
            List.of("Value: " + pct(config.thirdPlusSameEnchantReturnPercent()), "Left +0.05, Right -0.05")));

        inv.setItem(SLOT_BACK, named(Material.ARROW, "Back"));
    }

    public static void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin, ExtendedAnvilConfig config) {
        if (!(event.getView().getTopInventory().getTitle().equals("EA Costs & Returns"))) return;

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        boolean shift = event.isShiftClick();
        boolean left = event.isLeftClick();
        boolean right = event.isRightClick();

        if (slot == SLOT_BACK) {
            ExtendedAnvilAdminGui.open(player, plugin, config, new ExtendedAnvilService(plugin, config));
            return;
        }

        if (slot == SLOT_BASE_COST) {
            int delta = shift ? 10 : 1;
            int v = config.enchantBaseCostPerLevel();
            if (left) v += delta;
            if (right) v -= delta;
            config.setEnchantBaseCostPerLevel(v);
            config.save();
            build(event.getInventory(), config);
            click(player);
            return;
        }

        if (slot == SLOT_ADD_MULT) {
            int delta = shift ? 10 : 1;
            int v = config.enchantAddCountMultiplier();
            if (left) v += delta;
            if (right) v -= delta;
            config.setEnchantAddCountMultiplier(v);
            config.save();
            build(event.getInventory(), config);
            click(player);
            return;
        }

        if (slot == SLOT_RET_FIRST) {
            config.setFirstReturnPercent(bump(config.firstReturnPercent(), left, right));
            config.save();
            build(event.getInventory(), config);
            click(player);
            return;
        }

        if (slot == SLOT_RET_SECOND) {
            config.setSecondSameEnchantReturnPercent(bump(config.secondSameEnchantReturnPercent(), left, right));
            config.save();
            build(event.getInventory(), config);
            click(player);
            return;
        }

        if (slot == SLOT_RET_THIRD) {
            config.setThirdPlusSameEnchantReturnPercent(bump(config.thirdPlusSameEnchantReturnPercent(), left, right));
            config.save();
            build(event.getInventory(), config);
            click(player);
        }
    }

    private static double bump(double v, boolean left, boolean right) {
        if (left) v += 0.05;
        if (right) v -= 0.05;
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        return v;
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    private static String pct(double v) {
        return Math.round(v * 100.0) + "%";
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
