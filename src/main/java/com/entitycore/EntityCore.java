package com.entitycore;

import com.entitycore.modules.ModuleManager;
import com.entitycore.modules.hoppers.HopperFiltersModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class EntityCore extends JavaPlugin {

    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        // If you already construct moduleManager elsewhere, keep your way.
        // This is a safe, standard initialization.
        if (this.moduleManager == null) {
            this.moduleManager = new ModuleManager(this);
        }

        // IMPORTANT FIX: HopperFiltersModule requires JavaPlugin in constructor
        moduleManager.register(new HopperFiltersModule(this));
    }

    @Override
    public void onDisable() {
        // If your ModuleManager has a disable-all, call it here.
        // Keeping this safe even if you don't have it.
        if (this.moduleManager != null) {
            this.moduleManager.disableAll();
        }
    }
}
