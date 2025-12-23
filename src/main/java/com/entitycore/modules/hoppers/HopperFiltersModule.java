package com.entitycore.modules.hoppers;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopperFiltersModule implements Module {

    private JavaPlugin plugin;
    private HopperFiltersListener listener;
    private HopperFiltersCommand command;

    @Override
    public String getName() {
        return "HopperFilters";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        HopperFilterStorage.init(plugin);

        this.listener = new HopperFiltersListener(plugin);
        Bukkit.getPluginManager().registerEvents(this.listener, plugin);

        this.command = new HopperFiltersCommand(plugin, this.listener);
        if (plugin.getCommand("hf") != null) {
            plugin.getCommand("hf").setExecutor(this.command);
            plugin.getCommand("hf").setTabCompleter(this.command);
        } else {
            plugin.getLogger().warning("[HopperFilters] Command 'hf' missing from plugin.yml");
        }

        plugin.getLogger().info("[HopperFilters] Enabled");
    }

    @Override
    public void disable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }

        // Command executors are not listeners; nothing to unregister.
        this.command = null;
        this.plugin = null;
    }
}
