package com.entitycore.modules.extendedanvil;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilGuiModule implements Module {

    private JavaPlugin plugin;

    private ExtendedAnvilConfig config;
    private XpRefundService refundService;
    private EnchantCostService costService;

    private ExtendedAnvilSessionManager sessions;
    private ExtendedAnvilListener listener;

    private ExtendedAnvilCommand playerCommand;
    private ExtendedAnvilAdminCommand adminCommand;

    @Override
    public String getName() {
        return "ExtendedAnvil";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        this.config = new ExtendedAnvilConfig(plugin);
        this.refundService = new XpRefundService(config);
        this.costService = new EnchantCostService(config);

        this.sessions = new ExtendedAnvilSessionManager(plugin, config, costService, refundService);
        this.listener = new ExtendedAnvilListener(sessions);

        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.playerCommand = new ExtendedAnvilCommand(sessions);
        PluginCommand ea = plugin.getCommand("ea");
        if (ea != null) {
            ea.setExecutor(playerCommand);
            ea.setTabCompleter(playerCommand);
        } else {
            plugin.getLogger().warning("[ExtendedAnvil] Command 'ea' missing in plugin.yml");
        }

        this.adminCommand = new ExtendedAnvilAdminCommand(sessions);
        PluginCommand eaAdmin = plugin.getCommand("eaadmin");
        if (eaAdmin != null) {
            eaAdmin.setExecutor(adminCommand);
            eaAdmin.setTabCompleter(adminCommand);
        } else {
            plugin.getLogger().warning("[ExtendedAnvil] Command 'eaadmin' missing in plugin.yml");
        }

        plugin.getLogger().info("[ExtendedAnvil] Enabled.");
    }

    @Override
    public void disable() {
        if (sessions != null) {
            sessions.shutdownAll();
        }

        if (listener != null) HandlerList.unregisterAll(listener);

        plugin = null;
        config = null;
        refundService = null;
        costService = null;
        sessions = null;
        listener = null;
        playerCommand = null;
        adminCommand = null;
    }
}
