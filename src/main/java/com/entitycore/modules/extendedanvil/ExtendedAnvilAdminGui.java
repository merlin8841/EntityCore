package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Operator-only GUI to edit ExtendedAnvil settings.
 */
public final class ExtendedAnvilAdminGui {

    public static final int SIZE = 54;

    public static final int SLOT_REFUND_FIRST = 20;
    public static final int SLOT_REFUND_SECOND = 21;
    public static final int SLOT_REFUND_LATER = 22;
    public static final int SLOT_XP_PER_LVL = 24;
    public static final int SLOT_CURSES = 29;
    public static final int SLOT_APPLY_COST = 30;
    public static final int SLOT_PRIORITY = 32;
    public static final int SLOT_SAVE = 49;
    public static final int SLOT_RESET = 50;

    private final ExtendedAnvilConfig config;

    public ExtendedAnvilAdminGui(org.bukkit.plugin.Plugin plugin, ExtendedAnvilConfig config) {
        this.config = config;
    }

    public void open(Player player) {
        ExtendedAnvilHolder holder = new ExtendedAnvilHolder(ExtendedAnvilHolder.Type.ADMIN, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, SIZE, ChatColor.DARK_RED + "EA Settings (Operator)" );
        holder.setInventory(inv);

        ItemStack filler = ExtendedAnvilUtil.filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_REFUND_FIRST, pctButton("Refund % (1st removal)", config.getRefundPercentFirst()));
        inv.setItem(SLOT_REFUND_SECOND, pctButton("Refund % (2nd removal)", config.getRefundPercentSecond()));
        inv.setItem(SLOT_REFUND_LATER, pctButton("Refund % (later)", config.getRefundPercentLater()));
        inv.setItem(SLOT_XP_PER_LVL, intButton("XP per enchant level", config.getXpPerEnchantLevel()));
        inv.setItem(SLOT_CURSES, toggleButton("Allow curse removal", config.isAllowCurseRemoval()));
        inv.setItem(SLOT_APPLY_COST, intButton("Apply-book level cost", config.getApplyBookLevelCost()));

        inv.setItem(SLOT_PRIORITY, ExtendedAnvilUtil.button(Material.WRITABLE_BOOK, "Edit priority list", Arrays.asList(
                "Controls which enchant is removed first",
                "when 2+ books are provided."
        )));

        inv.setItem(SLOT_SAVE, ExtendedAnvilUtil.button(Material.LIME_CONCRETE, "Save", Arrays.asList(
                "Writes extendedanvil.yml"
        )));
        inv.setItem(SLOT_RESET, ExtendedAnvilUtil.button(Material.RED_CONCRETE, "Reset", Arrays.asList(
                "Resets values to defaults",
                "then saves."
        )));

        inv.setItem(4, ExtendedAnvilUtil.info(Material.NETHER_STAR, "Click controls", clickLore()));

        player.openInventory(inv);
    }

    public void refresh(Inventory inv) {
        inv.setItem(SLOT_REFUND_FIRST, pctButton("Refund % (1st removal)", config.getRefundPercentFirst()));
        inv.setItem(SLOT_REFUND_SECOND, pctButton("Refund % (2nd removal)", config.getRefundPercentSecond()));
        inv.setItem(SLOT_REFUND_LATER, pctButton("Refund % (later)", config.getRefundPercentLater()));
        inv.setItem(SLOT_XP_PER_LVL, intButton("XP per enchant level", config.getXpPerEnchantLevel()));
        inv.setItem(SLOT_CURSES, toggleButton("Allow curse removal", config.isAllowCurseRemoval()));
        inv.setItem(SLOT_APPLY_COST, intButton("Apply-book level cost", config.getApplyBookLevelCost()));
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
                "Priority list controls disenchant order"
        );
    }
}
