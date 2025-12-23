package com.entitycore;

import com.entitycore.module.ModuleManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class EntityCore extends JavaPlugin {

    private static EntityCore instance;
    private ModuleManager moduleManager;

    public static EntityCore get() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    @Override
    public void onEnable() {
        instance = this;

        moduleManager = new ModuleManager(this);
        moduleManager.loadModules();

        getLogger().info("EntityCore enabled");
    }

    @Override
    public void onDisable() {
        moduleManager.disableModules();
        getLogger().info("EntityCore disabled");
    }
}
