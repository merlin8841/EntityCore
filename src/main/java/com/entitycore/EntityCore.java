package com.entitycore;

import com.entitycore.module.ModuleManager;
import com.entitycore.modules.extendedanvil.ExtendedAnvilGuiModule;
import com.entitycore.modules.flyingallowed.FlyingAllowedModule;
import com.entitycore.modules.flyingallowed.FlyingAllowedWorldGuardBridge;
import com.entitycore.modules.hoppers.HopperFiltersModule;
import com.entitycore.modules.infection.InfectionModule;
import com.entitycore.modules.questbuilder.QuestBuilderModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class EntityCore extends JavaPlugin {

    private ModuleManager moduleManager;

    @Override
    public void onLoad() {
        // WorldGuard custom flags must be registered during onLoad() (before WorldGuard enables)
        FlyingAllowedWorldGuardBridge.tryRegisterFlags(this);
    }

    @Override
    public void onEnable() {
        this.moduleManager = new ModuleManager(this);

        // Register modules here (manual registration by design)
        moduleManager.register(new HopperFiltersModule());
        moduleManager.register(new ExtendedAnvilGuiModule());

        // Infection (Operator-only)
        moduleManager.register(new InfectionModule());

        // FlyingAllowed (WorldGuard-driven flight control)
        moduleManager.register(new FlyingAllowedModule());

        // QuestBuilder (Operator-only tool + triggers)
        moduleManager.register(new QuestBuilderModule());

        // Enable every registered module
        moduleManager.loadModules();
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableModules();
        }
    }
}
