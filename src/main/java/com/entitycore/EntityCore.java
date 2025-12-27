package com.entitycore;

import com.entitycore.module.ModuleManager;
import com.entitycore.modules.hoppers.HopperFiltersModule;
import com.entitycore.modules.extendedanvil.ExtendedAnvilGuiModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class EntityCore extends JavaPlugin {

    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        this.moduleManager = new ModuleManager(this);

        // Register modules here (manual registration by design)
        moduleManager.register(new HopperFiltersModule());
        moduleManager.register(new ExtendedAnvilGuiModule(this));

        // Actually enables every registered module
        moduleManager.loadModules();
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableModules();
        }
    }
}
