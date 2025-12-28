package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ExtendedAnvilConfig {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public ExtendedAnvilConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "extendedanvil.yml");
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
        } else {
            yaml = YamlConfiguration.loadConfiguration(file);
            applyMissingDefaults();
            save();
        }
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[ExtendedAnvil] Failed to save extendedanvil.yml");
            e.printStackTrace();
        }
    }

    private void writeDefaults() {
        yaml.set("debug", false);

        yaml.set("enchant.cost.base_per_level", 2);
        yaml.set("enchant.cost.add_count_multiplier", 1);

        yaml.set("disenchant.return.first_percent", 0.75);
        yaml.set("disenchant.return.second_same_enchant_percent", 0.25);
        yaml.set("disenchant.return.third_plus_same_enchant_percent", 0.0);

        yaml.set("disenchant.allow_curse_removal", true);

        // Default: vanilla caps
        ConfigurationSection caps = yaml.createSection("caps");
        for (Enchantment ench : Enchantment.values()) {
            if (ench == null || ench.getKey() == null) continue;
            caps.set(keyString(ench), ench.getMaxLevel());
        }

        // Default priority: vanilla registry order (stable enough); admin can reorder
        List<String> priority = new ArrayList<>();
        for (Enchantment ench : Enchantment.values()) {
            if (ench == null || ench.getKey() == null) continue;
            priority.add(keyString(ench));
        }
        yaml.set("disenchant.priority", priority);
    }

    private void applyMissingDefaults() {
        // Minimal defaulting so updates don’t wipe configs.
        if (!yaml.contains("debug")) yaml.set("debug", false);

        if (!yaml.contains("enchant.cost.base_per_level")) yaml.set("enchant.cost.base_per_level", 2);
        if (!yaml.contains("enchant.cost.add_count_multiplier")) yaml.set("enchant.cost.add_count_multiplier", 1);

        if (!yaml.contains("disenchant.return.first_percent")) yaml.set("disenchant.return.first_percent", 0.75);
        if (!yaml.contains("disenchant.return.second_same_enchant_percent")) yaml.set("disenchant.return.second_same_enchant_percent", 0.25);
        if (!yaml.contains("disenchant.return.third_plus_same_enchant_percent")) yaml.set("disenchant.return.third_plus_same_enchant_percent", 0.0);

        if (!yaml.contains("disenchant.allow_curse_removal")) yaml.set("disenchant.allow_curse_removal", true);

        if (!yaml.contains("caps")) {
            ConfigurationSection caps = yaml.createSection("caps");
            for (Enchantment ench : Enchantment.values()) {
                if (ench == null || ench.getKey() == null) continue;
                caps.set(keyString(ench), ench.getMaxLevel());
            }
        } else {
            // Ensure any new enchantments get a cap entry
            ConfigurationSection caps = yaml.getConfigurationSection("caps");
            if (caps != null) {
                for (Enchantment ench : Enchantment.values()) {
                    if (ench == null || ench.getKey() == null) continue;
                    String k = keyString(ench);
                    if (!caps.contains(k)) {
                        caps.set(k, ench.getMaxLevel());
                    }
                }
            }
        }

        if (!yaml.contains("disenchant.priority")) {
            List<String> priority = new ArrayList<>();
            for (Enchantment ench : Enchantment.values()) {
                if (ench == null || ench.getKey() == null) continue;
                priority.add(keyString(ench));
            }
            yaml.set("disenchant.priority", priority);
        }
    }

    public boolean debug() {
        return yaml.getBoolean("debug", false);
    }

    public void setDebug(boolean value) {
        yaml.set("debug", value);
    }

    public int enchantBaseCostPerLevel() {
        return Math.max(0, yaml.getInt("enchant.cost.base_per_level", 2));
    }

    public void setEnchantBaseCostPerLevel(int value) {
        yaml.set("enchant.cost.base_per_level", Math.max(0, value));
    }

    public int enchantAddCountMultiplier() {
        return Math.max(0, yaml.getInt("enchant.cost.add_count_multiplier", 1));
    }

    public void setEnchantAddCountMultiplier(int value) {
        yaml.set("enchant.cost.add_count_multiplier", Math.max(0, value));
    }

    public double firstReturnPercent() {
        return clamp01(yaml.getDouble("disenchant.return.first_percent", 0.75));
    }

    public void setFirstReturnPercent(double value) {
        yaml.set("disenchant.return.first_percent", clamp01(value));
    }

    public double secondSameEnchantReturnPercent() {
        return clamp01(yaml.getDouble("disenchant.return.second_same_enchant_percent", 0.25));
    }

    public void setSecondSameEnchantReturnPercent(double value) {
        yaml.set("disenchant.return.second_same_enchant_percent", clamp01(value));
    }

    public double thirdPlusSameEnchantReturnPercent() {
        return clamp01(yaml.getDouble("disenchant.return.third_plus_same_enchant_percent", 0.0));
    }

    public void setThirdPlusSameEnchantReturnPercent(double value) {
        yaml.set("disenchant.return.third_plus_same_enchant_percent", clamp01(value));
    }

    public boolean allowCurseRemoval() {
        return yaml.getBoolean("disenchant.allow_curse_removal", true);
    }

    public void setAllowCurseRemoval(boolean value) {
        yaml.set("disenchant.allow_curse_removal", value);
    }

    public int capFor(Enchantment ench) {
        if (ench == null || ench.getKey() == null) return 0;
        String k = keyString(ench);
        int def = ench.getMaxLevel();
        return Math.max(0, yaml.getInt("caps." + k, def));
    }

    public void setCapFor(Enchantment ench, int cap) {
        if (ench == null || ench.getKey() == null) return;
        String k = keyString(ench);
        yaml.set("caps." + k, Math.max(0, cap));
    }

    public List<Enchantment> priorityList() {
        List<String> raw = yaml.getStringList("disenchant.priority");
        Map<String, Enchantment> map = new HashMap<>();
        for (Enchantment ench : Enchantment.values()) {
            if (ench == null || ench.getKey() == null) continue;
            map.put(keyString(ench), ench);
        }

        List<Enchantment> result = new ArrayList<>();
        for (String s : raw) {
            Enchantment e = map.get(s);
            if (e != null) result.add(e);
        }

        // Ensure all enchants are present (append missing)
        Set<Enchantment> present = new HashSet<>(result);
        for (Enchantment ench : Enchantment.values()) {
            if (ench == null || ench.getKey() == null) continue;
            if (!present.contains(ench)) result.add(ench);
        }

        return result;
    }

    public void setPriorityList(List<Enchantment> list) {
        List<String> out = new ArrayList<>();
        for (Enchantment ench : list) {
            if (ench == null || ench.getKey() == null) continue;
            out.add(keyString(ench));
        }
        yaml.set("disenchant.priority", out);
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(plugin, value);
    }

    private static String keyString(Enchantment ench) {
        return ench.getKey().getNamespace() + ":" + ench.getKey().getKey();
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
