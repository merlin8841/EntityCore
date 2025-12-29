package com.entitycore.modules.infection;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectionListener implements Listener {

    private final JavaPlugin plugin;
    private final InfectionConfig config;
    private final InfectionService service;

    public InfectionListener(JavaPlugin plugin, InfectionConfig config, InfectionService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = e.getItem();
        if (!InfectionSeedItem.isSeed(plugin, item)) return;

        if (!e.getPlayer().hasPermission("entitycore.infection.admin")) {
            e.getPlayer().sendMessage(ChatColor.RED + "No permission.");
            return;
        }

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Seed at clicked block
        service.seedAt(clicked);

        // Enable spread (global)
        if (!config.isEnabled()) {
            config.setEnabled(true);
            config.save();
        }

        service.ensureTasksRunning();

        e.getPlayer().sendMessage(ChatColor.GREEN + "Infection seeded. Global spread is now ENABLED.");
    }
}
