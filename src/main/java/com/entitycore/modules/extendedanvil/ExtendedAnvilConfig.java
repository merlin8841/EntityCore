package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ExtendedAnvilConfig {

    private static final String FILE_NAME = "extendedanvil.yml";

    private final JavaPlugin plugin;
    private File file;
    private YamlConfiguration yml;

    // Refund tuning (LEVELS, not raw XP)
    private int refundPercentFirst = 75;
    private int refundPercentSecond = 25;
    private int refundPercentLater = 0;

    /** How many "value levels" each enchant level is worth for refund math. */
    private int refundLevelsPerEnchantLevel = 4;

    private boolean allowCurseRemoval = true;

    // Apply-book cost tuning (LEVELS, scales with book size)
    private int applyCostBaseLevels = 2;
    private int applyCostPerEnchant = 1;
    private int applyCostPerStoredLevel = 1;

    /** Enchant priority list (top = removed first). Stored as namespaced keys. */
    private final List<String> priority = new ArrayList<>();

    public ExtendedAnvilConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.yml = YamlConfiguration.loadConfiguration(file);

        refundPercentFirst = clampInt(yml.getInt("refund.percent.first", refundPercentFirst), 0, 100);
        refundPercentSecond = clampInt(yml.getInt("refund.percent.second", refundPercentSecond), 0, 100);
        refundPercentLater = clampInt(yml.getInt("refund.percent.later", refundPercentLater), 0, 100);

        refundLevelsPerEnchantLevel = clampInt(yml.getInt("refund.levelsPerEnchantLevel", refundLevelsPerEnchantLevel), 0, 1000);
        allowCurseRemoval = yml.getBoolean("disenchant.allowCurseRemoval", allowCurseRemoval);

        applyCostBaseLevels = clampInt(yml.getInt("enchant.applyCost.baseLevels", applyCostBaseLevels), 0, 1000);
        applyCostPerEnchant = clampInt(yml.getInt("enchant.applyCost.levelsPerEnchant", applyCostPerEnchant), 0, 1000);
        applyCostPerStoredLevel = clampInt(yml.getInt("enchant.applyCost.levelsPerStoredLevel", applyCostPerStoredLevel), 0, 1000);

        priority.clear();
        List<String> raw = yml.getStringList("disenchant.priority");
        if (raw != null) {
            for (String s : raw) {
                if (s == null || s.trim().isEmpty()) continue;
                priority.add(s.trim().toLowerCase());
            }
        }

        if (priority.isEmpty()) {
            seedPriorityWithAllEnchants();
            save();
        } else {
            ensureAllKnownEnchantsPresent();
            save();
        }
    }

    public void save() {
        if (yml == null || file == null) return;

        yml.set("refund.percent.first", refundPercentFirst);
        yml.set("refund.percent.second", refundPercentSecond);
        yml.set("refund.percent.later", refundPercentLater);
        yml.set("refund.levelsPerEnchantLevel", refundLevelsPerEnchantLevel);

        yml.set("disenchant.allowCurseRemoval", allowCurseRemoval);

        yml.set("enchant.applyCost.baseLevels", applyCostBaseLevels);
        yml.set("enchant.applyCost.levelsPerEnchant", applyCostPerEnchant);
        yml.set("enchant.applyCost.levelsPerStoredLevel", applyCostPerStoredLevel);

        yml.set("disenchant.priority", new ArrayList<>(priority));

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[ExtendedAnvil] Failed to save " + FILE_NAME + ": " + e.getMessage());
        }
    }

    public NamespacedKey keyForRemovalCount(String enchantKey) {
        return ExtendedAnvilKeys.removalCount(plugin, enchantKey);
    }

    public int getRefundPercentFirst() { return refundPercentFirst; }
    public void setRefundPercentFirst(int v) { refundPercentFirst = clampInt(v, 0, 100); }

    public int getRefundPercentSecond() { return refundPercentSecond; }
    public void setRefundPercentSecond(int v) { refundPercentSecond = clampInt(v, 0, 100); }

    public int getRefundPercentLater() { return refundPercentLater; }
    public void setRefundPercentLater(int v) { refundPercentLater = clampInt(v, 0, 100); }

    public int getRefundLevelsPerEnchantLevel() { return refundLevelsPerEnchantLevel; }
    public void setRefundLevelsPerEnchantLevel(int v) { refundLevelsPerEnchantLevel = clampInt(v, 0, 1000); }

    public boolean isAllowCurseRemoval() { return allowCurseRemoval; }
    public void setAllowCurseRemoval(boolean v) { allowCurseRemoval = v; }

    public int getApplyCostBaseLevels() { return applyCostBaseLevels; }
    public void setApplyCostBaseLevels(int v) { applyCostBaseLevels = clampInt(v, 0, 1000); }

    public int getApplyCostPerEnchant() { return applyCostPerEnchant; }
    public void setApplyCostPerEnchant(int v) { applyCostPerEnchant = clampInt(v, 0, 1000); }

    public int getApplyCostPerStoredLevel() { return applyCostPerStoredLevel; }
    public void setApplyCostPerStoredLevel(int v) { applyCostPerStoredLevel = clampInt(v, 0, 1000); }

    public List<String> getPriority() { return priority; }

    public void movePriority(String key, int newIndex) {
        if (key == null) return;
        key = key.toLowerCase();
        int old = priority.indexOf(key);
        if (old < 0) return;

        newIndex = Math.max(0, Math.min(priority.size() - 1, newIndex));
        if (old == newIndex) return;

        priority.remove(old);
        priority.add(newIndex, key);
    }

    private void seedPriorityWithAllEnchants() {
        priority.clear();
        for (Enchantment e : Enchantment.values()) {
            NamespacedKey key = e.getKey();
            if (key == null) continue;
            priority.add(key.toString().toLowerCase());
        }
    }

    private void ensureAllKnownEnchantsPresent() {
        Set<String> set = new LinkedHashSet<>(priority);
        for (Enchantment e : Enchantment.values()) {
            NamespacedKey key = e.getKey();
            if (key == null) continue;
            set.add(key.toString().toLowerCase());
        }
        priority.clear();
        priority.addAll(set);
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
