package com.entitycore.modules.hoppers;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class HoppersModule implements Module {

    @Override
    public String getName() {
        return "Hoppers";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(
                new HopperListener(plugin),
                plugin
        );

        plugin.getLogger().info("Hoppers module enabled");
    }

    @Override
    public void disable() {
        // No persistent tasks to stop
    }
}
