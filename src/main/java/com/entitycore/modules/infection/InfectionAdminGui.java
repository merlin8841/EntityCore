package com.entitycore.modules.infection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class InfectionAdminGui {

    private InfectionAdminGui() {}

    public static void open(Player player, JavaPlugin plugin, InfectionConfig config, InfectionService service) {
        Inventory inv = Bukkit.createInventory(player, 27, ChatColor.DARK_GREEN + "Infection Control");

        // Fill background
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // Toggle
        inv.setItem(11, toggleItem(config.isEnabled()));

        // Rate controls
        inv.setItem(13, rateItem(config));

        inv.setItem(12, item(Material.REDSTONE, ChatColor.RED + "- Speed", List.of(ChatColor.GRAY + "Decrease infection rate")));
        inv.setItem(14, item(Material.GLOWSTONE_DUST, ChatColor.GREEN + "+ Speed", List.of(ChatColor.GRAY + "Increase infection rate")));

        // Damage toggle
        inv.setItem(15, damageItem(config.isDamageEnabled()));

        // Info
        inv.setItem(22, item(Material.BOOK, ChatColor.AQUA + "Status",
                List.of(
                        ChatColor.GRAY + "Frontier: " + service.frontierSize(),
                        ChatColor.GRAY + "Infected chunks: " + service.infectedChunkCount(),
                        ChatColor.GRAY + "Turns blocks into: " + config.getInfectionMaterial().name()
                )));

        // Seed
        inv.setItem(26, item(Material.DIRT, ChatColor.GREEN + "Get Infection Seed",
                List.of(ChatColor.GRAY + "Gives you the seed item.", ChatColor.DARK_GRAY + "/infect give")));

        GuiSession session = new GuiSession(plugin, config, service, inv, player);
        plugin.getServer().getPluginManager().registerEvents(session, plugin);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    private static ItemStack toggleItem(boolean enabled) {
        return item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? ChatColor.GREEN + "Spread: ENABLED" : ChatColor.YELLOW + "Spread: PAUSED",
                List.of(ChatColor.GRAY + "Click to toggle global spread."));
    }

    private static ItemStack damageItem(boolean enabled) {
        return item(enabled ? Material.SPIDER_EYE : Material.FERMENTED_SPIDER_EYE,
                enabled ? ChatColor.DARK_RED + "Poison Dirt: ON" : ChatColor.GRAY + "Poison Dirt: OFF",
                List.of(ChatColor.GRAY + "Applies effect when standing on infected dirt."));
    }

    private static ItemStack rateItem(InfectionConfig config) {
        return item(Material.CLOCK,
                ChatColor.GOLD + "Infection Rate",
                List.of(
                        ChatColor.GRAY + "Blocks/cycle: " + config.getInfectionsPerCycle(),
                        ChatColor.GRAY + "Cycle ticks: " + config.getCycleTicks(),
                        ChatColor.DARK_GRAY + "Lower ticks = more often"
                ));
    }

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static final class GuiSession implements Listener {
        private final JavaPlugin plugin;
        private final InfectionConfig config;
        private final InfectionService service;
        private final Inventory inv;
        private final Player viewer;

        private GuiSession(JavaPlugin plugin, InfectionConfig config, InfectionService service, Inventory inv, Player viewer) {
            this.plugin = plugin;
            this.config = config;
            this.service = service;
            this.inv = inv;
            this.viewer = viewer;
        }

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (e.getWhoClicked() != viewer) return;
            if (e.getInventory() != inv) return;

            e.setCancelled(true);

            int slot = e.getRawSlot();
            if (slot < 0 || slot >= inv.getSize()) return;

            if (slot == 11) {
                config.setEnabled(!config.isEnabled());
                config.save();
                service.ensureTasksRunning();
                inv.setItem(11, toggleItem(config.isEnabled()));
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                return;
            }

            if (slot == 15) {
                config.setDamageEnabled(!config.isDamageEnabled());
                config.save();
                inv.setItem(15, damageItem(config.isDamageEnabled()));
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                return;
            }

            if (slot == 12) {
                // Decrease speed: lower perCycle and/or increase cycleTicks slightly
                int per = config.getInfectionsPerCycle();
                int ticks = config.getCycleTicks();

                per = Math.max(1, (int) Math.floor(per * 0.8));
                ticks = Math.min(200, ticks + 1);

                config.setInfectionsPerCycle(per);
                config.setCycleTicks(ticks);
                config.save();
                service.restartSpreadTask();

                inv.setItem(13, rateItem(config));
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
                return;
            }

            if (slot == 14) {
                // Increase speed: raise perCycle and/or decrease cycleTicks slightly
                int per = config.getInfectionsPerCycle();
                int ticks = config.getCycleTicks();

                per = Math.min(50000, (int) Math.ceil(per * 1.25));
                ticks = Math.max(1, ticks - 1);

                config.setInfectionsPerCycle(per);
                config.setCycleTicks(ticks);
                config.save();
                service.restartSpreadTask();

                inv.setItem(13, rateItem(config));
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
                return;
            }

            if (slot == 26) {
                viewer.getInventory().addItem(InfectionSeedItem.create(plugin, config));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (e.getPlayer() != viewer) return;
            if (e.getInventory() != inv) return;
            HandlerList.unregisterAll(this);
        }
    }
}
