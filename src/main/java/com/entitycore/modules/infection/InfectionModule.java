package com.entitycore.modules.infection;

import com.entitycore.module.Module;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectionModule implements Module {

    private JavaPlugin plugin;

    private InfectionConfig config;
    private InfectionService service;

    private InfectionCommand command;
    private InfectionListener listener;

    public InfectionModule() {
        // no-arg by design (fits your ModuleManager)
    }

    @Override
    public String getName() {
        return "Infection";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        this.config = new InfectionConfig(plugin);
        this.config.reload();

        this.service = new InfectionService(plugin, config);

        this.command = new InfectionCommand(plugin, config, service);
        this.listener = new InfectionListener(plugin, config, service);

        PluginCommand infectCmd = plugin.getCommand("infect");
        if (infectCmd != null) {
            infectCmd.setExecutor(command);
            infectCmd.setTabCompleter(command);
        } else {
            plugin.getLogger().warning("[Infection] Command 'infect' not found in plugin.yml (add it).");
        }

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        service.ensureTasksRunning();

        plugin.getLogger().info("[Infection] Enabled.");
    }

    @Override
    public void disable() {
        if (service != null) {
            service.shutdown();
        }
    }
}
