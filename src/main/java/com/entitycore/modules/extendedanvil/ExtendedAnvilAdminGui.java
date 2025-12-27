package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public final class ExtendedAnvilAdminGui {

    public static final int SIZE = 54;

    public static final int SLOT_REFUND_FIRST = 19;
    public static final int SLOT_REFUND_SECOND = 20;
    public static final int SLOT_REFUND_LATER = 21;
    public static final int SLOT_REFUND_LEVELS_PER = 23;

    public static final int SLOT_CURSES = 28;

    public static final int SLOT_APPLY_BASE = 30;
    public static final int SLOT_APPLY_PER_ENCHANT = 31;
    public static final int SLOT_APPLY_PER_LEVEL = 32;

    public static final int SLOT_PRIORITY = 34;

    public static final int SLOT_SAVE = 49;
    public static final int SLOT_RESET = 50;

    private final ExtendedAnvilConfig config;

    public ExtendedAnvilAdminGui(org.bukkit.plugin.Plugin plugin, ExtendedAnvilConfig config) {
        this.config = config;
    }

    public void open(Player player) {
        ExtendedAnvilHolder holder = new ExtendedAnvilHolder(ExtendedAnvilHolder.Type.ADMIN, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, SIZE, ChatColor.DARK_RED + "EA Settings (Operator)");
        holder.setInventory(inv);

        ItemStack filler = ExtendedAnvilUtil.filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(4, ExtendedAnvilUtil.info(Material.NETHER_STAR, "Click controls", clickLore()));

        refresh(inv);

        inv.setItem(SLOT_PRIORITY, ExtendedAnvilUtil.button(Material.WRITABLE_BOOK, "Edit priority list", Arrays.asList(
                "Controls which enchant is removed first",
                "when 2+ empty books are provided."
        )));

        inv.setItem(SLOT_SAVE, ExtendedAnvilUtil.button(Material.LIME_CONCRETE, "Save", Arrays.asList("Writes extendedanvil.yml")));
        inv.setItem(SLOT_RESET, ExtendedAnvilUtil.button(Material.RED_CONCRETE, "Reset", Arrays.asList("Resets defaults then saves")));

        player.openInventory(inv);
    }

    public void refresh(Inventory inv) {
        inv.setItem(SLOT_REFUND_FIRST, pctButton("Refund % (1st removal)", config.getRefundPercentFirst()));
        inv.setItem(SLOT_REFUND_SECOND, pctButton("Refund % (2nd removal)", config.getRefundPercentSecond()));
        inv.setItem(SLOT_REFUND_LATER, pctButton("Refund % (later)", config.getRefundPercentLater()));
        inv.setItem(SLOT_REFUND_LEVELS_PER, intButton("Refund levels per enchant level", config.getRefundLevelsPerEnchantLevel()));

        inv.setItem(SLOT_CURSES, toggleButton("Allow curse removal", config.isAllowCurseRemoval()));

        inv.setItem(SLOT_APPLY_BASE, intButton("Apply cost: base levels", config.getApplyCostBaseLevels()));
        inv.setItem(SLOT_APPLY_PER_ENCHANT, intButton("Apply cost: +levels per enchant", config.getApplyCostPerEnchant()));
        inv.setItem(SLOT_APPLY_PER_LEVEL, intButton("Apply cost: +levels per stored level", config.getApplyCostPerStoredLevel()));
    }

    private ItemStack pctButton(String name, int pct) {
        return ExtendedAnvilUtil.button(Material.EXPERIENCE_BOTTLE, name,
                Arrays.asList(
                        "Current: " + pct + "%",
                        "Left click: +5%",
                        "Right click: -5%",
                        "Shift-left: +1%",
                        "Shift-right: -1%"
                ));
    }

    private ItemStack intButton(String name, int val) {
        return ExtendedAnvilUtil.button(Material.COMPARATOR, name,
                Arrays.asList(
                        "Current: " + val,
                        "Left click: +5",
                        "Right click: -5",
                        "Shift-left: +1",
                        "Shift-right: -1"
                ));
    }

    private ItemStack toggleButton(String name, boolean on) {
        Material mat = on ? Material.LIME_DYE : Material.GRAY_DYE;
        String state = on ? "ON" : "OFF";
        return ExtendedAnvilUtil.button(mat, name, Arrays.asList(
                "Current: " + state,
                "Click to toggle"
        ));
    }

    private List<String> clickLore() {
        return Arrays.asList(
                "Left / Right click to adjust",
                "Shift-click = smaller steps",
                "Save writes to disk",
                "Priority list controls disenchant order",
                "",
                "Apply cost scales with book size:",
                "base + per enchant + per stored level"
        );
    }
}
