package com.entitycore.modules.hoppers;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopperFiltersModule implements Module {

    private JavaPlugin plugin;

    private HopperFilterData data;
    private HopperFiltersMenu menu;
    private HopperFiltersListener listener;
    private HopperFiltersCommand command;

    private HopperFiltersAdminMenu adminMenu;
    private HopperFiltersAdminCommand adminCommand;
    private HopperFiltersAdminListener adminListener;

    @Override
    public String getName() {
        return "HopperFilters";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        // Config defaults
        plugin.getConfig().addDefault("hopperfilters.tick-interval", 4);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        int interval = plugin.getConfig().getInt("hopperfilters.tick-interval", 4);
        if (interval < 1) interval = 1;
        if (interval > 20) interval = 20;

        this.data = new HopperFilterData(plugin);
        this.menu = new HopperFiltersMenu(data);

        this.listener = new HopperFiltersListener(plugin, data, menu);
        this.listener.setTickInterval(interval);

        this.command = new HopperFiltersCommand(plugin, menu);

        // Register main listener (includes menu events + hopper logic)
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        PluginCommand hf = plugin.getCommand("hf");
        if (hf != null) {
            hf.setExecutor(command);
            hf.setTabCompleter(command);
        } else {
            plugin.getLogger().warning("Command 'hf' missing from plugin.yml (HopperFiltersModule).");
        }

        // Admin GUI wiring
        int defaultInterval = plugin.getConfig().getInt("hopperfilters.tick-interval", 4);
        this.adminMenu = new HopperFiltersAdminMenu(listener, defaultInterval);
        this.adminCommand = new HopperFiltersAdminCommand(adminMenu);
        this.adminListener = new HopperFiltersAdminListener(plugin, adminMenu, listener);

        Bukkit.getPluginManager().registerEvents(adminListener, plugin);

        PluginCommand hfAdmin = plugin.getCommand("hfadmin");
        if (hfAdmin != null) {
            hfAdmin.setExecutor(adminCommand);
            hfAdmin.setTabCompleter(adminCommand);
        } else {
            plugin.getLogger().warning("Command 'hfadmin' missing from plugin.yml (HopperFiltersModule).");
        }

        // Start mover/purge loop
        listener.start();

        plugin.getLogger().info("[HopperFilters] Enabled. tick-interval=" + listener.getTickInterval());
    }

    @Override
    public void disable() {
        if (listener != null) {
            listener.stop();
        }

        if (listener != null) HandlerList.unregisterAll(listener);
        if (adminListener != null) HandlerList.unregisterAll(adminListener);

        data = null;
        menu = null;
        listener = null;
        command = null;

        adminMenu = null;
        adminCommand = null;
        adminListener = null;

        plugin = null;
    }
}
