package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class AdminMenu {

    public static final String TITLE = "Extended Anvil Settings";
    public static final int SIZE = 27;

    public static final int SLOT_REFUND_FIRST_MINUS = 10;
    public static final int SLOT_REFUND_FIRST_PLUS  = 12;

    public static final int SLOT_REFUND_SECOND_MINUS = 13;
    public static final int SLOT_REFUND_SECOND_PLUS  = 15;

    public static final int SLOT_REFUND_AFTER_MINUS = 16;
    public static final int SLOT_REFUND_AFTER_PLUS  = 18;

    public static final int SLOT_COST_MULT_MINUS = 19;
    public static final int SLOT_COST_MULT_PLUS  = 21;

    public static final int SLOT_PRIORITY = 22;
    public static final int SLOT_CAPS     = 23;

    public static final int SLOT_CLOSE = 26;

    public static Inventory create(org.bukkit.entity.Player player, JavaPlugin plugin, ExtendedAnvilConfig cfg) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        render(inv, plugin, cfg);
        return inv;
    }

    public static void render(Inventory inv, JavaPlugin plugin, ExtendedAnvilConfig cfg) {
        inv.setItem(SLOT_REFUND_FIRST_MINUS, button(Material.REDSTONE, "§cRefund First -5%", List.of("§7Now: §f" + pct(cfg.getRefundFirst()))));
        inv.setItem(SLOT_REFUND_FIRST_PLUS,  button(Material.GLOWSTONE_DUST, "§aRefund First +5%", List.of("§7Now: §f" + pct(cfg.getRefundFirst()))));

        inv.setItem(SLOT_REFUND_SECOND_MINUS, button(Material.REDSTONE, "§cRefund Second -5%", List.of("§7Now: §f" + pct(cfg.getRefundSecond()))));
        inv.setItem(SLOT_REFUND_SECOND_PLUS,  button(Material.GLOWSTONE_DUST, "§aRefund Second +5%", List.of("§7Now: §f" + pct(cfg.getRefundSecond()))));

        inv.setItem(SLOT_REFUND_AFTER_MINUS, button(Material.REDSTONE, "§cRefund After -5%", List.of("§7Now: §f" + pct(cfg.getRefundAfter()))));
        inv.setItem(SLOT_REFUND_AFTER_PLUS,  button(Material.GLOWSTONE_DUST, "§aRefund After +5%", List.of("§7Now: §f" + pct(cfg.getRefundAfter()))));

        inv.setItem(SLOT_COST_MULT_MINUS, button(Material.REDSTONE, "§cApply Cost Mult -0.1", List.of("§7Now: §f" + fmt(cfg.getApplyMultiplier()))));
        inv.setItem(SLOT_COST_MULT_PLUS,  button(Material.GLOWSTONE_DUST, "§aApply Cost Mult +0.1", List.of("§7Now: §f" + fmt(cfg.getApplyMultiplier()))));

        inv.setItem(SLOT_PRIORITY, button(Material.BOOK, "§bDisenchant Priority", List.of("§7Set which enchant removes first.")));
        inv.setItem(SLOT_CAPS, button(Material.ENCHANTING_TABLE, "§dEnchantment Caps", List.of("§7Set max levels per enchant.")));

        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cClose", List.of()));
    }

    public static boolean isThis(InventoryView view) {
        return view != null && TITLE.equals(view.getTitle());
    }

    private static String pct(double v) {
        return (int) Math.round(v * 100.0) + "%";
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private AdminMenu() {}
}
