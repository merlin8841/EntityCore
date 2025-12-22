package com.entitymc.entitycore;

import org.bukkit.plugin.java.JavaPlugin;

public final class EntityCore extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("EntityCore has enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("EntityCore has disabled.");
    }
}
