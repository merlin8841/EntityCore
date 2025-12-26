package com.entitycore.modules.anvil;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilModule implements Module {

    private JavaPlugin plugin;

    private XpRefundService refundService;
    private ExtendedAnvilListener listener;

    private ExtendedAnvilAdminMenu adminMenu;
    private ExtendedAnvilAdminCommand adminCommand;
    private ExtendedAnvilAdminListener adminListener;

    @Override
    public String getName() {
        return "ExtendedAnvil";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        // Defaults
        plugin.getConfig().addDefault("extendedanvil.enabled", true);

        plugin.getConfig().addDefault("extendedanvil.refund.first", 0.75);
        plugin.getConfig().addDefault("extendedanvil.refund.second", 0.25);
        plugin.getConfig().addDefault("extendedanvil.refund.thirdPlus", 0.0);

        plugin.getConfig().addDefault("extendedanvil.refund.max-levels-per-op", 30);
        plugin.getConfig().addDefault("extendedanvil.refund.base-levels-per-enchant", 2.0);
        plugin.getConfig().addDefault("extendedanvil.refund.level-multiplier", 1.0);

        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        if (!plugin.getConfig().getBoolean("extendedanvil.enabled", true)) {
            plugin.getLogger().info("[ExtendedAnvil] Disabled by config.");
            return;
        }

        this.refundService = new XpRefundService(plugin);
        this.listener = new ExtendedAnvilListener(plugin, refundService);

        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // Admin GUI wiring (OP-only)
        this.adminMenu = new ExtendedAnvilAdminMenu(plugin);
        this.adminCommand = new ExtendedAnvilAdminCommand(adminMenu);
        this.adminListener = new ExtendedAnvilAdminListener(plugin, adminMenu);

        Bukkit.getPluginManager().registerEvents(adminListener, plugin);

        PluginCommand eaAdmin = plugin.getCommand("eaadmin");
        if (eaAdmin != null) {
            eaAdmin.setExecutor(adminCommand);
            eaAdmin.setTabCompleter(adminCommand);
        } else {
            plugin.getLogger().warning("Command 'eaadmin' missing from plugin.yml (ExtendedAnvilModule).");
        }

        plugin.getLogger().info("[ExtendedAnvil] Enabled.");
    }

    @Override
    public void disable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (adminListener != null) {
            HandlerList.unregisterAll(adminListener);
        }

        refundService = null;
        listener = null;

        adminMenu = null;
        adminCommand = null;
        adminListener = null;

        plugin = null;
    }
}
