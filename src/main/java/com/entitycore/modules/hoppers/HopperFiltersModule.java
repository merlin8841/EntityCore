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

    @Override
    public String getName() {
        return "HopperFilters";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        this.data = new HopperFilterData(plugin);
        this.menu = new HopperFiltersMenu(data);
        this.listener = new HopperFiltersListener(data, menu);
        this.command = new HopperFiltersCommand(plugin, menu);

        Bukkit.getPluginManager().registerEvents(listener, plugin);

        PluginCommand hf = plugin.getCommand("hf");
        if (hf != null) {
            hf.setExecutor(command);
            hf.setTabCompleter(command);
        } else {
            plugin.getLogger().warning("Command 'hf' missing from plugin.yml (HopperFiltersModule).");
        }

        plugin.getLogger().info("[HopperFilters] Enabled.");
    }

    @Override
    public void disable() {
        if (plugin != null) {
            plugin.getLogger().info("[HopperFilters] Disabling...");
        }

        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }

        data = null;
        menu = null;
        listener = null;
        command = null;
        plugin = null;
    }
}
