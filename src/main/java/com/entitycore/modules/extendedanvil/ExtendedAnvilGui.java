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
 * Player-facing chest GUI for enchanting / disenchanting.
 */
public final class ExtendedAnvilGui {

    public static final int SIZE = 54;

    // Working slots
    public static final int SLOT_ITEM = 20;
    public static final int SLOT_OUTPUT = 24;
    public static final int SLOT_HELP = 49;

    // Books area (empty books for disenchant / enchanted book for apply)
    public static final int BOOKS_FROM = 37;
    public static final int BOOKS_TO = 43;

    private final ExtendedAnvilConfig config;
    private final ExtendedAnvilService service;

    public ExtendedAnvilGui(org.bukkit.plugin.Plugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        this.config = config;
        this.service = service;
    }

    public void open(Player player) {
        ExtendedAnvilHolder holder = new ExtendedAnvilHolder(ExtendedAnvilHolder.Type.PLAYER, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, SIZE, ChatColor.DARK_PURPLE + "Extended Anvil");
        holder.setInventory(inv);

        // Fill borders
        ItemStack filler = ExtendedAnvilUtil.filler();
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Clear working slots
        inv.setItem(SLOT_ITEM, null);
        inv.setItem(SLOT_OUTPUT, ExtendedAnvilUtil.button(Material.ANVIL, "Process", Arrays.asList(
                "Click to enchant or disenchant",
                "",
                "Item slot: " + SLOT_ITEM,
                "Book slots: bottom row",
                "Output: " + SLOT_OUTPUT
        )));

        // Book slots open
        for (int i = BOOKS_FROM; i <= BOOKS_TO; i++) {
            inv.setItem(i, null);
        }

        // Help
        inv.setItem(SLOT_HELP, ExtendedAnvilUtil.info(Material.BOOK, "How it works", helpLore()));

        player.openInventory(inv);
    }

    private List<String> helpLore() {
        return Arrays.asList(
                "Put an item in the Item slot.",
                "",
                "Disenchant:",
                "- Add 1 empty book: removes ALL enchants -> 1 book",
                "- Add 2+ empty books: removes ONE enchant per click (priority order)",
                "",
                "Enchant:",
                "- Add an enchanted book to apply to the item",
                "- Vanilla conflicts are respected",
                "",
                "XP refund follows server settings."
        );
    }

    /** Recompute / refresh UI info items (does NOT change player items). */
    public void refresh(Inventory inv) {
        inv.setItem(SLOT_HELP, ExtendedAnvilUtil.info(Material.BOOK, "How it works", helpLore()));
    }
}
