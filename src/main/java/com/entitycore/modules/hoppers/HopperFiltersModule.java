package com.entitycore.modules.hoppers;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopperFiltersModule {

    private final JavaPlugin plugin;

    private HopperFilterData data;
    private HopperFiltersMenu menu;
    private HopperFiltersListener listener;
    private HopperFiltersCommand command;

    public HopperFiltersModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
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
            plugin.getLogger().warning("Command 'hf' missing from plugin.yml");
        }
    }

    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        data = null;
        menu = null;
        listener = null;
        command = null;
    }
}
