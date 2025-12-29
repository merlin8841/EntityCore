package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilAdminGui {

    public static final String TITLE = "EA Admin";
    private static final int SIZE = 27;

    private static final int SLOT_DEBUG = 10;
    private static final int SLOT_COSTS = 12;
    private static final int SLOT_CAPS = 14;
    private static final int SLOT_PRIORITY = 16;
    private static final int SLOT_CLOSE = 22;

    private ExtendedAnvilAdminGui() {}

    public static final class Holder implements InventoryHolder {
        private final Player owner;
        private Inventory inv;

        public Holder(Player owner) {
            this.owner = owner;
        }

        public Player owner() {
            return owner;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        public void setInventory(Inventory inv) {
            this.inv = inv;
        }
    }

    public static void open(Player player, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        Holder holder = new Holder(player);
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inv);

        build(inv, config);

        player.openInventory(inv);
        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] Opened EA Admin for " + player.getName());
        }
    }

    private static void build(Inventory inv, ExtendedAnvilConfig config) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_DEBUG, named(Material.REDSTONE_TORCH, "Debug: " + (config.debug() ? "ON" : "OFF")));
        inv.setItem(SLOT_COSTS, named(Material.ANVIL, "Costs (Enchant / Disenchant / Repair)"));
        inv.setItem(SLOT_CAPS, named(Material.ENCHANTED_BOOK, "Enchant Caps"));
        inv.setItem(SLOT_PRIORITY, named(Material.HOPPER, "Disenchant Priority"));
        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, "Close"));
    }

    public static void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;

        if (!holder.owner().getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (slot == SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (slot == SLOT_DEBUG) {
            boolean next = !config.debug();
            config.setDebug(next);
            config.save();
            build(event.getInventory(), config);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            plugin.getLogger().info("[ExtendedAnvil] Debug set to " + (next ? "ON" : "OFF") + " by " + player.getName());
            return;
        }

        if (slot == SLOT_COSTS) {
            ExtendedAnvilCostsGui.open(player, plugin, config);
            if (config.debug()) plugin.getLogger().info("[ExtendedAnvil][DEBUG] Opened Costs & Returns for " + player.getName());
            return;
        }

        if (slot == SLOT_CAPS) {
            ExtendedAnvilCapsGui.open(player, plugin, config);
            if (config.debug()) plugin.getLogger().info("[ExtendedAnvil][DEBUG] Opened Enchant Caps for " + player.getName());
            return;
        }

        if (slot == SLOT_PRIORITY) {
            ExtendedAnvilPriorityGui.open(player, plugin, config);
            if (config.debug()) plugin.getLogger().info("[ExtendedAnvil][DEBUG] Opened Disenchant Priority for " + player.getName());
        }
    }

    public static void handleClose(Player player, InventoryCloseEvent event, JavaPlugin plugin, ExtendedAnvilConfig config) {
        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] Closed EA Admin for " + player.getName());
        }
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
}
