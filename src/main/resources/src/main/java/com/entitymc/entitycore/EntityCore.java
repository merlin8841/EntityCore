package com.entitymc.entitycore;

import org.bukkit.plugin.java.JavaPlugin;

public final class EntityCore extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("EntityCore enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("EntityCore disabled");
    }
}
