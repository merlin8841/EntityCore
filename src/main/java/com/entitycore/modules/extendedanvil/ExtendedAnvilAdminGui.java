package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Operator settings GUI (Bedrock-friendly: no right/shift click). */
public final class ExtendedAnvilAdminGui {

    public static final int SIZE = 54;

    // Refund row
    public static final int SLOT_REFUND_FIRST_VALUE = 10;
    public static final int SLOT_REFUND_SECOND_VALUE = 11;
    public static final int SLOT_REFUND_LATER_VALUE = 12;
    public static final int SLOT_REFUND_FALLBACK_VALUE = 14;

    // Apply/cost row
    public static final int SLOT_GLOBAL_BASE_VALUE = 28;
    public static final int SLOT_ADD_PER_ENCHANT_VALUE = 29;
    public static final int SLOT_ADD_PER_STORED_LEVEL_VALUE = 30;
    public static final int SLOT_PRIOR_WORK_COST_VALUE = 32;
    public static final int SLOT_PRIOR_WORK_INC_VALUE = 33;

    public static final int SLOT_CURSE_TOGGLE = 16;

    public static final int SLOT_EDIT_PRIORITY = 34;
    public static final int SLOT_EDIT_ENCHANT_COSTS = 35;

    public static final int SLOT_SAVE = 49;
    public static final int SLOT_RESET = 50;

    // Generic +/- button layout around a value slot:
    // [value-10]=valueSlot-18, [value-1]=valueSlot-9, [value+1]=valueSlot+9, [value+10]=valueSlot+18
    public static final int DELTA_MINUS_10 = -10;
    public static final int DELTA_MINUS_1 = -1;
    public static final int DELTA_PLUS_1 = 1;
    public static final int DELTA_PLUS_10 = 10;

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

        inv.setItem(4, ExtendedAnvilUtil.info(Material.NETHER_STAR, "Tap +/- buttons", List.of(
                "Bedrock-friendly controls",
                "Use the red/green buttons",
                "Save writes extendedanvil.yml"
        )));

        // Labels
        inv.setItem(1, ExtendedAnvilUtil.info(Material.EXPERIENCE_BOTTLE, "Refund %", List.of("First / Second / Later")));
        inv.setItem(19, ExtendedAnvilUtil.info(Material.ANVIL, "Apply Cost Adders", List.of("Global adders (optional)")));

        // Static buttons
        inv.setItem(SLOT_CURSE_TOGGLE, ExtendedAnvilUtil.button(Material.GRAY_DYE, "Allow curse removal", List.of("Tap to toggle")));

        inv.setItem(SLOT_EDIT_PRIORITY, ExtendedAnvilUtil.button(Material.WRITABLE_BOOK, "Edit priority list", List.of(
                "Controls disenchant order",
                "Bedrock-friendly editor"
        )));

        inv.setItem(SLOT_EDIT_ENCHANT_COSTS, ExtendedAnvilUtil.button(Material.ENCHANTED_BOOK, "Edit enchant base costs", List.of(
                "Per-enchant Base + Per-level costs",
                "This is the main balancing knob"
        )));

        inv.setItem(SLOT_SAVE, ExtendedAnvilUtil.button(Material.LIME_CONCRETE, "Save", List.of("Write extendedanvil.yml")));
        inv.setItem(SLOT_RESET, ExtendedAnvilUtil.button(Material.RED_CONCRETE, "Reset", List.of("Reset defaults then save")));

        refresh(inv);
        player.openInventory(inv);
    }

    public void refresh(Inventory inv) {
        // Values
        inv.setItem(SLOT_REFUND_FIRST_VALUE, ExtendedAnvilUtil.info(Material.EXPERIENCE_BOTTLE, "First: " + config.getRefundPercentFirst() + "%", List.of("")));
        inv.setItem(SLOT_REFUND_SECOND_VALUE, ExtendedAnvilUtil.info(Material.EXPERIENCE_BOTTLE, "Second: " + config.getRefundPercentSecond() + "%", List.of("")));
        inv.setItem(SLOT_REFUND_LATER_VALUE, ExtendedAnvilUtil.info(Material.EXPERIENCE_BOTTLE, "Later: " + config.getRefundPercentLater() + "%", List.of("")));
        inv.setItem(SLOT_REFUND_FALLBACK_VALUE, ExtendedAnvilUtil.info(Material.COMPARATOR, "Fallback: " + config.getRefundLevelsPerEnchantLevel(), List.of("Levels per enchant level")));

        inv.setItem(SLOT_GLOBAL_BASE_VALUE, ExtendedAnvilUtil.info(Material.ANVIL, "Global base: " + config.getApplyCostGlobalBaseLevels(), List.of("Added once per apply")));
        inv.setItem(SLOT_ADD_PER_ENCHANT_VALUE, ExtendedAnvilUtil.info(Material.CANDLE, "+/enchant: " + config.getApplyCostPerEnchantAdd(), List.of("Added per enchant applied")));
        inv.setItem(SLOT_ADD_PER_STORED_LEVEL_VALUE, ExtendedAnvilUtil.info(Material.CANDLE, "+/lvl: " + config.getApplyCostPerStoredLevelAdd(), List.of("Added per enchant level")));
        inv.setItem(SLOT_PRIOR_WORK_COST_VALUE, ExtendedAnvilUtil.info(Material.IRON_PICKAXE, "Work cost: " + config.getPriorWorkCostPerStep(), List.of("Levels per prior-work step")));
        inv.setItem(SLOT_PRIOR_WORK_INC_VALUE, ExtendedAnvilUtil.info(Material.IRON_NUGGET, "Work +: " + config.getPriorWorkIncrementPerApply(), List.of("Steps added per apply")));

        inv.setItem(SLOT_CURSE_TOGGLE,
                ExtendedAnvilUtil.button(config.isAllowCurseRemoval() ? Material.LIME_DYE : Material.GRAY_DYE,
                        "Allow curse removal: " + (config.isAllowCurseRemoval() ? "ON" : "OFF"), List.of("Tap to toggle")));

        // +/- buttons
        placeDeltaButtons(inv, SLOT_REFUND_FIRST_VALUE);
        placeDeltaButtons(inv, SLOT_REFUND_SECOND_VALUE);
        placeDeltaButtons(inv, SLOT_REFUND_LATER_VALUE);
        placeDeltaButtons(inv, SLOT_REFUND_FALLBACK_VALUE);

        placeDeltaButtons(inv, SLOT_GLOBAL_BASE_VALUE);
        placeDeltaButtons(inv, SLOT_ADD_PER_ENCHANT_VALUE);
        placeDeltaButtons(inv, SLOT_ADD_PER_STORED_LEVEL_VALUE);
        placeDeltaButtons(inv, SLOT_PRIOR_WORK_COST_VALUE);
        placeDeltaButtons(inv, SLOT_PRIOR_WORK_INC_VALUE);
    }

    private void placeDeltaButtons(Inventory inv, int valueSlot) {
        inv.setItem(valueSlot - 18, ExtendedAnvilUtil.button(Material.RED_CONCRETE, "-10", List.of("")));
        inv.setItem(valueSlot - 9, ExtendedAnvilUtil.button(Material.RED_TERRACOTTA, "-1", List.of("")));
        inv.setItem(valueSlot + 9, ExtendedAnvilUtil.button(Material.LIME_TERRACOTTA, "+1", List.of("")));
        inv.setItem(valueSlot + 18, ExtendedAnvilUtil.button(Material.LIME_CONCRETE, "+10", List.of("")));
    }
}
