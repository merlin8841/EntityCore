package com.entitycore.modules.extendedanvil;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilGuiModule implements Module {

    private JavaPlugin plugin;

    private ExtendedAnvilConfig config;
    private EnchantCostService costService;
    private XpRefundService refundService;
    private ExtendedAnvilSessionManager sessionManager;

    private ExtendedAnvilListener listener;

    private ExtendedAnvilCommand playerCommand;
    private ExtendedAnvilAdminCommand adminCommand;

    @Override
    public String getName() {
        return "ExtendedAnvilGUI";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        // Defaults
        plugin.getConfig().addDefault("extendedanvil.apply.multiplier", 1.0);
        plugin.getConfig().addDefault("extendedanvil.refund.first", 0.75);
        plugin.getConfig().addDefault("extendedanvil.refund.second", 0.25);
        plugin.getConfig().addDefault("extendedanvil.refund.after", 0.0);

        // Priority defaults (string keys like minecraft:sharpness)
        plugin.getConfig().addDefault("extendedanvil.disenchant.priority", java.util.List.of(
                "minecraft:mending",
                "minecraft:unbreaking",
                "minecraft:protection",
                "minecraft:sharpness",
                "minecraft:efficiency",
                "minecraft:fortune",
                "minecraft:looting",
                "minecraft:silk_touch",
                "minecraft:feather_falling"
        ));

        // Caps section (empty = use vanilla max; cap 0 = disabled)
        // plugin.getConfig().addDefault("extendedanvil.caps.minecraft:sharpness", 10);

        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        this.config = new ExtendedAnvilConfig(plugin);
        this.costService = new EnchantCostService(plugin, config);
        this.refundService = new XpRefundService(plugin, config);
        this.sessionManager = new ExtendedAnvilSessionManager(plugin, config, refundService, costService);

        this.listener = new ExtendedAnvilListener(sessionManager);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.playerCommand = new ExtendedAnvilCommand(sessionManager);
        this.adminCommand = new ExtendedAnvilAdminCommand(sessionManager);

        PluginCommand ea = plugin.getCommand("ea");
        if (ea != null) {
            ea.setExecutor(playerCommand);
        } else {
            plugin.getLogger().warning("[ExtendedAnvil] Missing command 'ea' in plugin.yml");
        }

        PluginCommand eaAdmin = plugin.getCommand("eaadmin");
        if (eaAdmin != null) {
            eaAdmin.setExecutor(adminCommand);
        } else {
            plugin.getLogger().warning("[ExtendedAnvil] Missing command 'eaadmin' in plugin.yml");
        }

        plugin.getLogger().info("[ExtendedAnvil] Enabled GUI module.");
    }

    @Override
    public void disable() {
        if (listener != null) HandlerList.unregisterAll(listener);

        plugin = null;
        listener = null;

        playerCommand = null;
        adminCommand = null;

        sessionManager = null;
        refundService = null;
        costService = null;
        config = null;
    }
}
