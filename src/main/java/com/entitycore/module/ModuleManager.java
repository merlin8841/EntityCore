package com.entitycore.module;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {

    private final JavaPlugin plugin;
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadModules() {
        // Modules will be manually registered for now
        for (Module module : modules) {
            try {
                module.enable(plugin);
                plugin.getLogger().info("Enabled module: " + module.getName());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to enable module: " + module.getName());
                e.printStackTrace();
            }
        }
    }

    public void disableModules() {
        for (Module module : modules) {
            try {
                module.disable();
                plugin.getLogger().info("Disabled module: " + module.getName());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to disable module: " + module.getName());
                e.printStackTrace();
            }
        }
    }

    public void register(Module module) {
        modules.add(module);
    }
}
