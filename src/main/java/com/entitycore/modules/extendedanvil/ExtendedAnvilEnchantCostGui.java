package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Operator GUI: edit per-enchant base cost + per-level cost (Bedrock-friendly). */
public final class ExtendedAnvilEnchantCostGui {

    private static final int SIZE = 54;
    private static final int LIST_SLOTS = 45; // 0-44

    public static final int SLOT_BACK = 49;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_NEXT = 53;

    // Edit screen
    public static final int SLOT_EDIT_TITLE = 4;
    public static final int SLOT_BASE_MINUS_10 = 19;
    public static final int SLOT_BASE_MINUS_1 = 20;
    public static final int SLOT_BASE_VALUE = 22;
    public static final int SLOT_BASE_PLUS_1 = 24;
    public static final int SLOT_BASE_PLUS_10 = 25;

    public static final int SLOT_PERLVL_MINUS_10 = 28;
    public static final int SLOT_PERLVL_MINUS_1 = 29;
    public static final int SLOT_PERLVL_VALUE = 31;
    public static final int SLOT_PERLVL_PLUS_1 = 33;
    public static final int SLOT_PERLVL_PLUS_10 = 34;

    public static final int SLOT_EDIT_BACK = 49;
    public static final int SLOT_SAVE = 50;

    private final ExtendedAnvilConfig config;
    private final ExtendedAnvilAdminGui adminGui;

    public ExtendedAnvilEnchantCostGui(ExtendedAnvilConfig config, ExtendedAnvilAdminGui adminGui) {
        this.config = config;
        this.adminGui = adminGui;
    }

    public void openList(Player player, int page) {
        ExtendedAnvilHolder holder = new ExtendedAnvilHolder(ExtendedAnvilHolder.Type.ENCHANT_COST_LIST, player.getUniqueId());
        holder.setPage(page);
        Inventory inv = Bukkit.createInventory(holder, SIZE, ChatColor.DARK_RED + "EA Enchant Costs");
        holder.setInventory(inv);

        ItemStack filler = ExtendedAnvilUtil.filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        drawList(inv, page);

        inv.setItem(SLOT_BACK, ExtendedAnvilUtil.button(Material.BARRIER, "Back", List.of("Return to settings")));
        inv.setItem(SLOT_PREV, ExtendedAnvilUtil.button(Material.ARROW, "Prev", List.of("Previous page")));
        inv.setItem(SLOT_NEXT, ExtendedAnvilUtil.button(Material.ARROW, "Next", List.of("Next page")));

        player.openInventory(inv);
    }

    public void drawList(Inventory inv, int page) {
        List<String> keys = new ArrayList<>(config.getEnchantBaseCostMap().keySet());
        Collections.sort(keys);

        int start = page * LIST_SLOTS;
        for (int i = 0; i < LIST_SLOTS; i++) {
            int idx = start + i;
            if (idx >= keys.size()) {
                inv.setItem(i, null);
                continue;
            }

            String key = keys.get(idx);
            int base = config.getEnchantBaseCost(key);
            int perLvl = config.getEnchantPerLevelCost(key);

            ItemStack it = ExtendedAnvilUtil.button(Material.ENCHANTED_BOOK, ExtendedAnvilUtil.prettyEnchantName(key), List.of(
                    "Key: " + key,
                    "Base cost: " + base + " lvl",
                    "Per-level: " + perLvl + " lvl",
                    "",
                    "Tap to edit"
            ));
            inv.setItem(i, it);
        }

        inv.setItem(4, ExtendedAnvilUtil.info(Material.BOOK, "Per-Enchant Costs", List.of(
                "Bedrock-friendly editor",
                "Base cost applies once per enchant",
                "Per-level cost applies for levels above 1",
                "",
                "Example: base 2, per-level 1",
                "Level 1 => 2",
                "Level 3 => 2 + (1*2) = 4"
        )));
    }

    public void openEdit(Player player, String enchantKey) {
        ExtendedAnvilHolder holder = new ExtendedAnvilHolder(ExtendedAnvilHolder.Type.ENCHANT_COST_EDIT, player.getUniqueId());
        holder.setContextKey(enchantKey);
        Inventory inv = Bukkit.createInventory(holder, SIZE, ChatColor.DARK_RED + "EA Cost Editor");
        holder.setInventory(inv);

        ItemStack filler = ExtendedAnvilUtil.filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        drawEdit(inv, enchantKey);
        player.openInventory(inv);
    }

    public void drawEdit(Inventory inv, String enchantKey) {
        String nice = ExtendedAnvilUtil.prettyEnchantName(enchantKey);
        int base = config.getEnchantBaseCost(enchantKey);
        int perLvl = config.getEnchantPerLevelCost(enchantKey);

        inv.setItem(SLOT_EDIT_TITLE, ExtendedAnvilUtil.info(Material.ENCHANTED_BOOK, nice, List.of(
                "Key: " + enchantKey,
                "",
                "Set Base and Per-level costs",
                "Then press Save"
        )));

        inv.setItem(SLOT_BASE_MINUS_10, ExtendedAnvilUtil.button(Material.RED_CONCRETE, "Base -10", List.of("")));
        inv.setItem(SLOT_BASE_MINUS_1, ExtendedAnvilUtil.button(Material.RED_TERRACOTTA, "Base -1", List.of("")));
        inv.setItem(SLOT_BASE_VALUE, ExtendedAnvilUtil.info(Material.COMPARATOR, "Base: " + base, List.of("Tap buttons to change")));
        inv.setItem(SLOT_BASE_PLUS_1, ExtendedAnvilUtil.button(Material.LIME_TERRACOTTA, "Base +1", List.of("")));
        inv.setItem(SLOT_BASE_PLUS_10, ExtendedAnvilUtil.button(Material.LIME_CONCRETE, "Base +10", List.of("")));

        inv.setItem(SLOT_PERLVL_MINUS_10, ExtendedAnvilUtil.button(Material.RED_CONCRETE, "PerLvl -10", List.of("")));
        inv.setItem(SLOT_PERLVL_MINUS_1, ExtendedAnvilUtil.button(Material.RED_TERRACOTTA, "PerLvl -1", List.of("")));
        inv.setItem(SLOT_PERLVL_VALUE, ExtendedAnvilUtil.info(Material.REPEATER, "PerLvl: " + perLvl, List.of("Per level above 1")));
        inv.setItem(SLOT_PERLVL_PLUS_1, ExtendedAnvilUtil.button(Material.LIME_TERRACOTTA, "PerLvl +1", List.of("")));
        inv.setItem(SLOT_PERLVL_PLUS_10, ExtendedAnvilUtil.button(Material.LIME_CONCRETE, "PerLvl +10", List.of("")));

        inv.setItem(SLOT_EDIT_BACK, ExtendedAnvilUtil.button(Material.BARRIER, "Back", List.of("Return to list")));
        inv.setItem(SLOT_SAVE, ExtendedAnvilUtil.button(Material.LIME_DYE, "Save", List.of("Write to extendedanvil.yml")));
    }

    public void backToSettings(Player player) {
        adminGui.open(player);
    }
}
