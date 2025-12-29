package com.entitycore.modules.flyingallowed;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class FlyingAllowedModule implements Module {

    private FlyingAllowedConfig config;
    private FlyingAllowedService service;

    @Override
    public String getName() {
        return "FlyingAllowed";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.config = new FlyingAllowedConfig(plugin);
        this.config.load();

        this.service = new FlyingAllowedService(plugin, config);

        Bukkit.getPluginManager().registerEvents(
                new FlyingAllowedListener(service),
                plugin
        );

        service.start();

        plugin.getLogger().info("[FlyingAllowed] Enabled (tickInterval=" + config.tickIntervalTicks()
                + ", fallGraceSeconds=" + config.fallGraceSeconds()
                + ", autoEnable=" + config.autoEnableFlight() + ")");
    }

    @Override
    public void disable() {
        if (service != null) {
            service.stop();
        }
    }
}
