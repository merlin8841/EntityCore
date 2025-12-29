package com.entitycore.modules.infection;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectionModule {

    private final JavaPlugin plugin;

    private InfectionConfig config;
    private InfectionService service;
    private InfectionCommand command;
    private InfectionListener listener;

    public InfectionModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
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
            plugin.getLogger().warning("[Infection] Command 'infect' not found in plugin.yml (add later).");
        }

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        service.ensureTasksRunning();

        plugin.getLogger().info("[Infection] Module enabled.");
    }

    public void disable() {
        if (service != null) service.shutdown();
        plugin.getLogger().info("[Infection] Module disabled.");
    }
}
