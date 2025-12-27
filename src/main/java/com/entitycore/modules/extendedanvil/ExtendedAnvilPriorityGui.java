package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Operator GUI to reorder the disenchant priority list.
 */
public final class ExtendedAnvilPriorityGui {

    public static final int SIZE = 54;
    public static final int SLOT_BACK = 49;

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
                "Left click: move up",
                "Right click: move down",
                "Shift-left: to top",
                "Shift-right: to bottom"
        )));

        List<String> list = config.getPriority();
        int max = Math.min(list.size(), 45);
        for (int i = 0; i < max; i++) {
            String key = list.get(i);
            inv.setItem(i, enchantEntry(i, key));
        }

        inv.setItem(SLOT_BACK, ExtendedAnvilUtil.button(Material.ARROW, "Back", Arrays.asList("Return to settings")));
    }

    private ItemStack enchantEntry(int index, String key) {
        List<String> lore = new ArrayList<>();
        lore.add("Key: " + key);
        lore.add("Index: " + index);
        lore.add("");
        lore.add("Left click: up");
        lore.add("Right click: down");
        lore.add("Shift-left: top");
        lore.add("Shift-right: bottom");
        return ExtendedAnvilUtil.button(Material.ENCHANTED_BOOK, (index + 1) + ". " + ExtendedAnvilUtil.prettyEnchantName(key), lore);
    }
}
