package com.entitycore.modules.provenance;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProvenanceModule implements Module {

    private ProvenanceConfig config;
    private ProvenanceService service;

    @Override
    public String getName() {
        return "Provenance";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.config = new ProvenanceConfig(plugin);
        this.config.load();

        this.service = new ProvenanceService(plugin, config);

        // Listener
        Bukkit.getPluginManager().registerEvents(
                new ProvenanceListener(plugin, config, service),
                plugin
        );

        // /pdc
        PluginCommand pdc = plugin.getCommand("pdc");
        if (pdc != null) {
            pdc.setExecutor(new PdcInspectCommand(service));
        } else {
            plugin.getLogger().warning("[Provenance] Command '/pdc' not found in plugin.yml");
        }

        // /prov
        PluginCommand prov = plugin.getCommand("prov");
        if (prov != null) {
            prov.setExecutor(new ProvCommand(service));
        } else {
            plugin.getLogger().warning("[Provenance] Command '/prov' not found in plugin.yml");
        }

        plugin.getLogger().info("[Provenance] Enabled (debug=" + config.debug() + ")");
    }

    @Override
    public void disable() {
        // Nothing persistent yet
    }
}
