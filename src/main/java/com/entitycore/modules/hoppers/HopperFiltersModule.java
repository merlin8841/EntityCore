package com.entitycore.modules.hoppers;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class HopperFiltersModule implements Module {

    private JavaPlugin plugin;
    private Listener listener;

    @Override
    public String getName() {
        return "HopperFilters";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        HopperFilterStorage.init(plugin);
        this.listener = new HopperFilterListener(plugin);

        Bukkit.getPluginManager().registerEvents(this.listener, plugin);
    }

    @Override
    public void disable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
        this.plugin = null;
    }
}
