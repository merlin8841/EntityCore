package com.entitycore.modules.extendedanvil;

import com.entitycore.module.Module;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilGuiModule implements Module {

    private JavaPlugin plugin;
    private ExtendedAnvilConfig config;
    private ExtendedAnvilService service;
    private ExtendedAnvilListener listener;

    @Override
    public String getName() {
        return "ExtendedAnvil";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        this.config = new ExtendedAnvilConfig(plugin);
        this.config.load();

        this.service = new ExtendedAnvilService(plugin, config);
        this.listener = new ExtendedAnvilListener(plugin, config, service);

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        PluginCommand ea = plugin.getCommand("ea");
        if (ea != null) {
            ea.setExecutor(new ExtendedAnvilCommand(plugin, config, service));
        } else {
            plugin.getLogger().severe("[ExtendedAnvil] Command /ea missing from plugin.yml");
        }

        PluginCommand eaadmin = plugin.getCommand("eaadmin");
        if (eaadmin != null) {
            eaadmin.setExecutor(new ExtendedAnvilAdminCommand(plugin, config, service));
        } else {
            plugin.getLogger().severe("[ExtendedAnvil] Command /eaadmin missing from plugin.yml");
        }

        plugin.getLogger().info("[ExtendedAnvil] Enabled.");
    }

    @Override
    public void disable() {
        if (config != null) {
            config.save();
        }
        if (plugin != null) {
            plugin.getLogger().info("[ExtendedAnvil] Disabled.");
        }
    }
}
