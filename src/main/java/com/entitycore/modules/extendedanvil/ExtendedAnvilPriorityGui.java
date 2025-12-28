package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/** Bedrock-friendly priority editor (no right/shift click required). */
public final class ExtendedAnvilPriorityGui {

    public static final int SIZE = 54;
    public static final int SLOT_BACK = 49;

    // Edit screen
    public static final int SLOT_EDIT_TITLE = 4;
    public static final int SLOT_MOVE_UP = 20;
    public static final int SLOT_MOVE_DOWN = 24;
    public static final int SLOT_MOVE_TOP = 29;
    public static final int SLOT_MOVE_BOTTOM = 33;
    public static final int SLOT_EDIT_BACK = 49;

    private final ExtendedAnvilConfig config;

    public ExtendedAnvilPriorityGui(org.bukkit.plugin.Plugin plugin, ExtendedAnvilConfig config) {
        this.config = config;
    }

    public void open(Player player) {
        ExtendedAnvilHolder holder = new ExtendedAnvilHolder(ExtendedAnvilHolder.Type.PRIORITY, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, SIZE, ChatColor.DARK_RED + "EA Priority (Operator)");
        holder.setInventory(inv);

        draw(inv);
        player.openInventory(inv);
    }

    public void draw(Inventory inv) {
        ItemStack filler = ExtendedAnvilUtil.filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(4, ExtendedAnvilUtil.info(Material.BOOKSHELF, "Priority order", Arrays.asList(
                "Top-left is highest priority.",
                "Used when 2+ empty books are provided.",
                "",
                "Tap an enchant to edit",
                "Then use move buttons"
        )));

        List<String> list = config.getPriority();
        int max = Math.min(list.size(), 45);
        for (int i = 0; i < max; i++) {
            String key = list.get(i);
            inv.setItem(i, ExtendedAnvilUtil.button(Material.ENCHANTED_BOOK,
                    (i + 1) + ". " + ExtendedAnvilUtil.prettyEnchantName(key),
                    Arrays.asList("Key: " + key, "Tap to edit")));
        }

        inv.setItem(SLOT_BACK, ExtendedAnvilUtil.button(Material.BARRIER, "Back", Arrays.asList("Return to settings")));
    }

    public void openEdit(Player player, String enchantKey) {
        ExtendedAnvilHolder holder = new ExtendedAnvilHolder(ExtendedAnvilHolder.Type.PRIORITY_EDIT, player.getUniqueId());
        holder.setContextKey(enchantKey);
        Inventory inv = Bukkit.createInventory(holder, SIZE, ChatColor.DARK_RED + "EA Priority Edit");
        holder.setInventory(inv);

        drawEdit(inv, enchantKey);
        player.openInventory(inv);
    }

    public void drawEdit(Inventory inv, String enchantKey) {
        ItemStack filler = ExtendedAnvilUtil.filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        int idx = config.getPriority().indexOf(enchantKey);
        inv.setItem(SLOT_EDIT_TITLE, ExtendedAnvilUtil.info(Material.ENCHANTED_BOOK,
                ExtendedAnvilUtil.prettyEnchantName(enchantKey),
                Arrays.asList("Key: " + enchantKey, "Index: " + idx)));

        inv.setItem(SLOT_MOVE_UP, ExtendedAnvilUtil.button(Material.ARROW, "Move Up", Arrays.asList("-1")));
        inv.setItem(SLOT_MOVE_DOWN, ExtendedAnvilUtil.button(Material.ARROW, "Move Down", Arrays.asList("+1")));
        inv.setItem(SLOT_MOVE_TOP, ExtendedAnvilUtil.button(Material.LADDER, "Move To Top", Arrays.asList("Index 0")));
        inv.setItem(SLOT_MOVE_BOTTOM, ExtendedAnvilUtil.button(Material.CHEST, "Move To Bottom", Arrays.asList("Last")));

        inv.setItem(SLOT_EDIT_BACK, ExtendedAnvilUtil.button(Material.BARRIER, "Back", Arrays.asList("Return to list")));
    }
}
