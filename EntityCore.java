package com.yourname.entitycore;

import org.bukkit.plugin.java.JavaPlugin;

public final class EntityCore extends JavaPlugin {

    private static EntityCore instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        getLogger().info("EntityCore enabled.");

        // Bootstrap core systems
        // Command framework
        // Permissions bridge
        // Grim companion layer
        // etc.
    }

    @Override
    public void onDisable() {
        getLogger().info("EntityCore disabled.");
    }

    public static EntityCore getInstance() {
        return instance;
    }
}
