package com.entitycore.modules.hoppers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class HopperFiltersAdminMenu {

    public static final String TITLE = "HopperFilters Settings";
    public static final int SIZE = 27;

    public static final int SLOT_DECREASE = 11;
    public static final int SLOT_STATUS   = 13;
    public static final int SLOT_INCREASE = 15;
    public static final int SLOT_RESET    = 22;
    public static final int SLOT_CLOSE    = 26;

    private final HopperFiltersListener listener;
    private final int defaultInterval;

    public HopperFiltersAdminMenu(HopperFiltersListener listener, int defaultInterval) {
        this.listener = listener;
        this.defaultInterval = Math.max(1, Math.min(20, defaultInterval));
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);
        render(inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    public void render(Inventory inv) {
        inv.setItem(SLOT_DECREASE, button(Material.REDSTONE, "§cSlower (-1 tick)", List.of("§7Increases delay between moves.")));
        inv.setItem(SLOT_INCREASE, button(Material.GLOWSTONE_DUST, "§aFaster (+1 tick)", List.of("§7Decreases delay between moves.")));
        inv.setItem(SLOT_STATUS, statusItem(listener.getTickInterval()));
        inv.setItem(SLOT_RESET, button(Material.PAPER, "§eReset to Default", List.of("§7Default: §f" + defaultInterval + " tick(s)")));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cClose", List.of()));
    }

    public boolean isMenu(Inventory inv) {
        if (inv == null) return false;
        // Inventory title is accessed via view in events; this helper isn't used there.
        return true;
    }

    public void slower(Player player, Inventory inv) {
        int now = listener.getTickInterval();
        listener.setTickInterval(now + 1); // slower = bigger interval
        render(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
    }

    public void faster(Player player, Inventory inv) {
        int now = listener.getTickInterval();
        listener.setTickInterval(now - 1); // faster = smaller interval
        render(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);
    }

    public void reset(Player player, Inventory inv) {
        listener.setTickInterval(defaultInterval);
        render(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.0f);
    }

    private ItemStack statusItem(int interval) {
        ItemStack it = new ItemStack(Material.COMPARATOR, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bCurrent Speed");
            meta.setLore(List.of(
                    "§7Tick interval: §f" + interval,
                    "§7(1 = fastest, 20 = slowest)",
                    "§8Applies immediately."
            ));
            it.setItemMeta(meta);
        }
        return it;
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
}
