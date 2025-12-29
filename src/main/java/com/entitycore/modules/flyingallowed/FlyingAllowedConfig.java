package com.entitycore.modules.flyingallowed;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class FlyingAllowedConfig {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public FlyingAllowedConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "flyingallowed.yml");
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

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[FlyingAllowed] Failed to save flyingallowed.yml");
            e.printStackTrace();
        }
    }

    private void writeDefaults() {
        yaml.set("debug", false);

        // How often to re-check players (region boundaries, teleports, etc.)
        yaml.set("tick_interval_ticks", 10);

        // When flight is revoked, cancel fall damage for this long
        yaml.set("fall_grace_seconds", 4);

        // If true, module will set player.flying=true when entering allowed region (Survival/Adventure only)
        yaml.set("auto_enable_flight", true);

        // Optional message defaults (used if region doesn't set ec-fly-message)
        yaml.set("message.denied", "§cYou can’t fly here.");
        yaml.set("message.denied_vip", "§cYou need VIP to fly here.");
        yaml.set("message.denied_admin", "§cYou need Admin to fly here.");
    }

    private void applyMissingDefaults() {
        if (!yaml.contains("debug")) yaml.set("debug", false);
        if (!yaml.contains("tick_interval_ticks")) yaml.set("tick_interval_ticks", 10);
        if (!yaml.contains("fall_grace_seconds")) yaml.set("fall_grace_seconds", 4);
        if (!yaml.contains("auto_enable_flight")) yaml.set("auto_enable_flight", true);

        if (!yaml.contains("message.denied")) yaml.set("message.denied", "§cYou can’t fly here.");
        if (!yaml.contains("message.denied_vip")) yaml.set("message.denied_vip", "§cYou need VIP to fly here.");
        if (!yaml.contains("message.denied_admin")) yaml.set("message.denied_admin", "§cYou need Admin to fly here.");
    }

    public boolean debug() { return yaml.getBoolean("debug", false); }
    public int tickIntervalTicks() { return Math.max(1, yaml.getInt("tick_interval_ticks", 10)); }
    public int fallGraceSeconds() { return Math.max(0, yaml.getInt("fall_grace_seconds", 4)); }
    public boolean autoEnableFlight() { return yaml.getBoolean("auto_enable_flight", true); }

    public String msgDenied() { return yaml.getString("message.denied", "§cYou can’t fly here."); }
    public String msgDeniedVip() { return yaml.getString("message.denied_vip", "§cYou need VIP to fly here."); }
    public String msgDeniedAdmin() { return yaml.getString("message.denied_admin", "§cYou need Admin to fly here."); }
}
