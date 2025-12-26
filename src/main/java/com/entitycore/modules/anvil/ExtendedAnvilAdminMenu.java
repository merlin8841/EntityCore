package com.entitycore.modules.anvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ExtendedAnvilAdminMenu {

    public static final String TITLE = "ExtendedAnvil Settings";
    public static final int SIZE = 27;

    public static final int SLOT_FIRST_DOWN  = 10;
    public static final int SLOT_FIRST_SHOW  = 11;
    public static final int SLOT_FIRST_UP    = 12;

    public static final int SLOT_SECOND_DOWN = 13;
    public static final int SLOT_SECOND_SHOW = 14;
    public static final int SLOT_SECOND_UP   = 15;

    public static final int SLOT_THIRD_DOWN  = 16;
    public static final int SLOT_THIRD_SHOW  = 17;
    public static final int SLOT_THIRD_UP    = 18;

    public static final int SLOT_RESET = 22;
    public static final int SLOT_CLOSE = 26;

    private final JavaPlugin plugin;

    public ExtendedAnvilAdminMenu(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        render(inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    public void render(Inventory inv) {
        inv.setItem(SLOT_FIRST_DOWN, button(Material.REDSTONE, "§c-5%", List.of()));
        inv.setItem(SLOT_FIRST_SHOW, percentItem("§bFirst Removal", "extendedanvil.refund.first", "§7Default: §f75%"));
        inv.setItem(SLOT_FIRST_UP, button(Material.GLOWSTONE_DUST, "§a+5%", List.of()));

        inv.setItem(SLOT_SECOND_DOWN, button(Material.REDSTONE, "§c-5%", List.of()));
        inv.setItem(SLOT_SECOND_SHOW, percentItem("§bSecond Removal", "extendedanvil.refund.second", "§7Default: §f25%"));
        inv.setItem(SLOT_SECOND_UP, button(Material.GLOWSTONE_DUST, "§a+5%", List.of()));

        inv.setItem(SLOT_THIRD_DOWN, button(Material.REDSTONE, "§c-5%", List.of()));
        inv.setItem(SLOT_THIRD_SHOW, percentItem("§bThird+ Removal", "extendedanvil.refund.thirdPlus", "§7Default: §f0%"));
        inv.setItem(SLOT_THIRD_UP, button(Material.GLOWSTONE_DUST, "§a+5%", List.of()));

        inv.setItem(SLOT_RESET, button(Material.PAPER, "§eReset Defaults", List.of("§775% / 25% / 0%")));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cClose", List.of()));
    }

    public boolean adjust(Inventory inv, String path, double delta) {
        double v = plugin.getConfig().getDouble(path, 0.0);
        v = clamp01(v + delta);
        plugin.getConfig().set(path, v);
        render(inv);
        return true;
    }

    private ItemStack percentItem(String name, String path, String footer) {
        double v = plugin.getConfig().getDouble(path, 0.0);
        int pct = (int) Math.round(v * 100.0);
        return button(Material.COMPARATOR, name, List.of(
                "§7Current: §f" + pct + "%",
                footer,
                "§8Applies immediately."
        ));
    }

    private ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
