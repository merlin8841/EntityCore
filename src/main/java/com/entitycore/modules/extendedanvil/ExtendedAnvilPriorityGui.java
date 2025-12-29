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
import java.util.List;

public final class ExtendedAnvilPriorityGui {

    public static final String TITLE = "EA Disenchant Priority";

    private static final int SIZE = 54;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_PAGE = 48;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;

    private ExtendedAnvilPriorityGui() {}

    public static void open(Player player, JavaPlugin plugin, ExtendedAnvilConfig config) {
        Inventory inv = Bukkit.createInventory(new ExtendedAnvilAdminGui.Holder(player), SIZE, TITLE);
        build(inv, config, 0);
        player.openInventory(inv);
    }

    private static void build(Inventory inv, ExtendedAnvilConfig config, int page) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        List<Enchantment> list = config.priorityList();

        int perPage = 45;
        int start = page * perPage;
        int end = Math.min(list.size(), start + perPage);

        for (int i = start; i < end; i++) {
            Enchantment ench = list.get(i);
            int slot = i - start;

            ItemStack it = lore(Material.HOPPER,
                (i + 1) + ". " + ench.getKey(),
                List.of(
                    "Left click: move UP",
                    "Right click: move DOWN"
                )
            );
            inv.setItem(slot, it);
        }

        inv.setItem(SLOT_PREV, named(Material.ARROW, "Prev Page"));
        inv.setItem(SLOT_NEXT, named(Material.ARROW, "Next Page"));
        inv.setItem(SLOT_BACK, named(Material.ARROW, "Back"));
        inv.setItem(SLOT_PAGE, lore(Material.PAPER, "Page", List.of(String.valueOf(page))));
    }

    public static void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin, ExtendedAnvilConfig config) {
        if (!event.getView().getTitle().equals(TITLE)) return;

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        int page = readPage(event.getView().getTopInventory());
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            ExtendedAnvilAdminGui.open(player, plugin, config, new ExtendedAnvilService(plugin, config));
            click(player);
            return;
        }

        if (slot == SLOT_PREV) {
            page = Math.max(0, page - 1);
            build(event.getInventory(), config, page);
            click(player);
            return;
        }

        if (slot == SLOT_NEXT) {
            page = page + 1;
            build(event.getInventory(), config, page);
            click(player);
            return;
        }

        if (slot >= 0 && slot < 45) {
            List<Enchantment> list = new ArrayList<>(config.priorityList());

            int index = page * 45 + slot;
            if (index < 0 || index >= list.size()) return;

            if (event.isLeftClick()) {
                if (index > 0) {
                    Enchantment tmp = list.get(index - 1);
                    list.set(index - 1, list.get(index));
                    list.set(index, tmp);
                }
            } else if (event.isRightClick()) {
                if (index < list.size() - 1) {
                    Enchantment tmp = list.get(index + 1);
                    list.set(index + 1, list.get(index));
                    list.set(index, tmp);
                }
            }

            config.setPriorityList(list);
            config.save();
            build(event.getInventory(), config, page);
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

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
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
