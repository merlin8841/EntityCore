package com.entitycore.modules.provenance;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class ProvenanceConfig {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public ProvenanceConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "provenance.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            yaml = new YamlConfiguration();
            writeDefaults();
            save();
            return;
        }

        yaml = YamlConfiguration.loadConfiguration(file);
        applyMissingDefaults();
        save();
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[Provenance] Failed to save provenance.yml");
            e.printStackTrace();
        }
    }

    private void writeDefaults() {
        yaml.set("debug", false);

        // /give and /item hooks
        yaml.set("hook_give_commands", true);

        // placement tracking/alerts (we’ll implement storage later; this file holds policy now)
        yaml.set("placements.track", true);
        yaml.set("placements.ignore_ops", true);     // your request: OP placements do NOT store/notify
        yaml.set("alerts.enabled", true);
        yaml.set("alerts.ignore_ops", true);         // your request: OP actions don’t spam ops
        yaml.set("alerts.permission", "entitycore.provenance.alerts");

        // “shop” blocking rules (generic; no decompile)
        yaml.set("shop_block.enabled", true);
        yaml.set("shop_block.title_contains", List.of("shop", "ultimateshop", "sell", "buy"));
        yaml.set("shop_block.holder_class_contains", List.of("ultimateshop", "shop"));
    }

    private void applyMissingDefaults() {
        if (!yaml.contains("debug")) yaml.set("debug", false);
        if (!yaml.contains("hook_give_commands")) yaml.set("hook_give_commands", true);

        if (!yaml.contains("placements.track")) yaml.set("placements.track", true);
        if (!yaml.contains("placements.ignore_ops")) yaml.set("placements.ignore_ops", true);

        if (!yaml.contains("alerts.enabled")) yaml.set("alerts.enabled", true);
        if (!yaml.contains("alerts.ignore_ops")) yaml.set("alerts.ignore_ops", true);
        if (!yaml.contains("alerts.permission")) yaml.set("alerts.permission", "entitycore.provenance.alerts");

        if (!yaml.contains("shop_block.enabled")) yaml.set("shop_block.enabled", true);
        if (!yaml.contains("shop_block.title_contains")) yaml.set("shop_block.title_contains", List.of("shop", "ultimateshop", "sell", "buy"));
        if (!yaml.contains("shop_block.holder_class_contains")) yaml.set("shop_block.holder_class_contains", List.of("ultimateshop", "shop"));
    }

    public boolean debug() { return yaml.getBoolean("debug", false); }

    public boolean hookGiveCommands() { return yaml.getBoolean("hook_give_commands", true); }

    public boolean trackPlacements() { return yaml.getBoolean("placements.track", true); }
    public boolean ignoreOpsPlacements() { return yaml.getBoolean("placements.ignore_ops", true); }

    public boolean alertsEnabled() { return yaml.getBoolean("alerts.enabled", true); }
    public boolean alertsIgnoreOps() { return yaml.getBoolean("alerts.ignore_ops", true); }
    public String alertsPermission() { return yaml.getString("alerts.permission", "entitycore.provenance.alerts"); }

    public boolean shopBlockEnabled() { return yaml.getBoolean("shop_block.enabled", true); }
    public List<String> shopTitleContains() { return yaml.getStringList("shop_block.title_contains"); }
    public List<String> shopHolderClassContains() { return yaml.getStringList("shop_block.holder_class_contains"); }
}
