package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExtendedAnvilConfig {

    private static final String FILE_NAME = "extendedanvil.yml";

    private final JavaPlugin plugin;
    private File file;
    private YamlConfiguration yml;

    // Refund tuning (LEVELS, not raw XP)
    private int refundPercentFirst = 75;
    private int refundPercentSecond = 25;
    private int refundPercentLast = 0;

    /** Fallback refund value when a book/item has no stored cost metadata. */
    private int refundLevelsPerEnchantLevel = 4;

    private boolean allowCurseRemoval = true;
    private boolean debug = false;

    // Global adders (keep simple defaults; most balance comes from per-enchant cost)
    private int applyCostGlobalBaseLevels = 0;
    private int applyCostPerEnchantAdd = 0;
    private int applyCostPerStoredLevelAdd = 0;

    // Vanilla-like "prior work" penalty
    private int priorWorkCostPerStep = 1;
    private int priorWorkIncrementPerApply = 1;

    /** Per-enchant base costs (LEVELS). */
    private final Map<String, Integer> enchantBaseCost = new LinkedHashMap<>();
    /** Per-enchant per-level add (LEVELS per level above 1). */
    private final Map<String, Integer> enchantPerLevelCost = new LinkedHashMap<>();
    /** Per-enchant max level (cap). Defaults to vanilla. */
    private final Map<String, Integer> enchantMaxLevel = new LinkedHashMap<>();

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
        int last = yml.getInt("refund.percent.last", refundPercentLast);
        if (!yml.contains("refund.percent.last")) {
            // Backward-compat: old key was .later
            last = yml.getInt("refund.percent.later", refundPercentLast);
        }
        refundPercentLast = clampInt(last, 0, 100);

        refundLevelsPerEnchantLevel = clampInt(yml.getInt("refund.levelsPerEnchantLevel", refundLevelsPerEnchantLevel), 0, 1000);
        allowCurseRemoval = yml.getBoolean("disenchant.allowCurseRemoval", allowCurseRemoval);

        debug = yml.getBoolean("debug", debug);

        applyCostGlobalBaseLevels = clampInt(yml.getInt("enchant.applyCost.globalBaseLevels", applyCostGlobalBaseLevels), 0, 1000);
        applyCostPerEnchantAdd = clampInt(yml.getInt("enchant.applyCost.addPerEnchant", applyCostPerEnchantAdd), 0, 1000);
        applyCostPerStoredLevelAdd = clampInt(yml.getInt("enchant.applyCost.addPerStoredLevel", applyCostPerStoredLevelAdd), 0, 1000);

        priorWorkCostPerStep = clampInt(yml.getInt("enchant.priorWork.costPerStep", priorWorkCostPerStep), 0, 1000);
        priorWorkIncrementPerApply = clampInt(yml.getInt("enchant.priorWork.incrementPerApply", priorWorkIncrementPerApply), 0, 100);

        // Per-enchant costs
        enchantBaseCost.clear();
        enchantPerLevelCost.clear();
        ConfigurationSection baseSec = yml.getConfigurationSection("enchant.baseCost");
        ConfigurationSection perLvlSec = yml.getConfigurationSection("enchant.perLevelCost");
        if (baseSec != null) {
            for (String k : baseSec.getKeys(false)) {
                int v = clampInt(baseSec.getInt(k), 0, 1000);
                enchantBaseCost.put(k.toLowerCase(), v);
            }
        }
        if (perLvlSec != null) {
            for (String k : perLvlSec.getKeys(false)) {
                int v = clampInt(perLvlSec.getInt(k), 0, 1000);
                enchantPerLevelCost.put(k.toLowerCase(), v);
            }
        }

        // Caps
        enchantMaxLevel.clear();
        ConfigurationSection capSec = yml.getConfigurationSection("enchant.maxLevel");
        if (capSec != null) {
            for (String k : capSec.getKeys(false)) {
                int v = clampInt(capSec.getInt(k), 1, 1000);
                enchantMaxLevel.put(k.toLowerCase(), v);
            }
        }

        // Priority
        priority.clear();
        List<String> list = yml.getStringList("disenchant.priority");
        if (list != null) {
            for (String s : list) {
                if (s == null || s.isBlank()) continue;
                priority.add(s.toLowerCase());
            }
        }

        // Ensure we always have keys for known enchants (vanilla defaults)
        ensureAllKnownEnchantsPresent();

        // Save once so new keys appear
        save();
    }

    public void save() {
        if (yml == null || file == null) return;

        yml.set("refund.percent.first", refundPercentFirst);
        yml.set("refund.percent.second", refundPercentSecond);
        yml.set("refund.percent.last", refundPercentLast);
        // Backward-compat: keep writing old key too so older builds don't break
        yml.set("refund.percent.later", refundPercentLast);

        yml.set("refund.levelsPerEnchantLevel", refundLevelsPerEnchantLevel);
        yml.set("disenchant.allowCurseRemoval", allowCurseRemoval);

        yml.set("debug", debug);

        yml.set("enchant.applyCost.globalBaseLevels", applyCostGlobalBaseLevels);
        yml.set("enchant.applyCost.addPerEnchant", applyCostPerEnchantAdd);
        yml.set("enchant.applyCost.addPerStoredLevel", applyCostPerStoredLevelAdd);

        yml.set("enchant.priorWork.costPerStep", priorWorkCostPerStep);
        yml.set("enchant.priorWork.incrementPerApply", priorWorkIncrementPerApply);

        yml.set("enchant.baseCost", new LinkedHashMap<>(enchantBaseCost));
        yml.set("enchant.perLevelCost", new LinkedHashMap<>(enchantPerLevelCost));
        yml.set("enchant.maxLevel", new LinkedHashMap<>(enchantMaxLevel));

        yml.set("disenchant.priority", new ArrayList<>(priority));

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[ExtendedAnvil] Failed to save " + FILE_NAME + ": " + e.getMessage());
        }
    }

    // --- Keys ---

    public NamespacedKey keyForRemovalCount(String enchantKey) {
        return ExtendedAnvilKeys.removalCount(plugin, enchantKey);
    }

    // --- Refund ---

    public int getRefundPercentFirst() { return refundPercentFirst; }
    public void setRefundPercentFirst(int v) { refundPercentFirst = clampInt(v, 0, 100); }

    public int getRefundPercentSecond() { return refundPercentSecond; }
    public void setRefundPercentSecond(int v) { refundPercentSecond = clampInt(v, 0, 100); }

    public int getRefundPercentLast() { return refundPercentLast; }
    public void setRefundPercentLast(int v) { refundPercentLast = clampInt(v, 0, 100); }

    /** Backward compat: old name was "Later". */
    public int getRefundPercentLater() { return refundPercentLast; }
    /** Backward compat: old name was "Later". */
    public void setRefundPercentLater(int v) { setRefundPercentLast(v); }

    public int getRefundLevelsPerEnchantLevel() { return refundLevelsPerEnchantLevel; }
    public void setRefundLevelsPerEnchantLevel(int v) { refundLevelsPerEnchantLevel = clampInt(v, 0, 1000); }

    public boolean isAllowCurseRemoval() { return allowCurseRemoval; }
    public void setAllowCurseRemoval(boolean v) { allowCurseRemoval = v; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean v) { debug = v; }

    // --- Global apply cost adders ---

    public int getApplyCostGlobalBaseLevels() { return applyCostGlobalBaseLevels; }
    public void setApplyCostGlobalBaseLevels(int v) { applyCostGlobalBaseLevels = clampInt(v, 0, 1000); }

    public int getApplyCostPerEnchantAdd() { return applyCostPerEnchantAdd; }
    public void setApplyCostPerEnchantAdd(int v) { applyCostPerEnchantAdd = clampInt(v, 0, 1000); }

    public int getApplyCostPerStoredLevelAdd() { return applyCostPerStoredLevelAdd; }
    public void setApplyCostPerStoredLevelAdd(int v) { applyCostPerStoredLevelAdd = clampInt(v, 0, 1000); }

    // --- Prior work ---

    public int getPriorWorkCostPerStep() { return priorWorkCostPerStep; }
    public void setPriorWorkCostPerStep(int v) { priorWorkCostPerStep = clampInt(v, 0, 1000); }

    public int getPriorWorkIncrementPerApply() { return priorWorkIncrementPerApply; }
    public void setPriorWorkIncrementPerApply(int v) { priorWorkIncrementPerApply = clampInt(v, 0, 100); }

    // --- Per-enchant costs ---

    public Map<String, Integer> getEnchantBaseCostMap() { return enchantBaseCost; }
    public Map<String, Integer> getEnchantPerLevelCostMap() { return enchantPerLevelCost; }

    public int getEnchantBaseCost(String enchantKey) {
        if (enchantKey == null) return 0;
        return enchantBaseCost.getOrDefault(enchantKey.toLowerCase(), 0);
    }

    public void setEnchantBaseCost(String enchantKey, int v) {
        if (enchantKey == null) return;
        enchantBaseCost.put(enchantKey.toLowerCase(), clampInt(v, 0, 1000));
    }

    public int getEnchantPerLevelCost(String enchantKey) {
        if (enchantKey == null) return 0;
        return enchantPerLevelCost.getOrDefault(enchantKey.toLowerCase(), 0);
    }

    public void setEnchantPerLevelCost(String enchantKey, int v) {
        if (enchantKey == null) return;
        enchantPerLevelCost.put(enchantKey.toLowerCase(), clampInt(v, 0, 1000));
    }

    // --- Caps ---

    public Map<String, Integer> getEnchantMaxLevelMap() { return enchantMaxLevel; }

    public int getEnchantMaxLevel(String enchantKey) {
        if (enchantKey == null) return 1;
        int def = 1;
        try {
            Enchantment e = Enchantment.getByKey(NamespacedKey.fromString(enchantKey));
            if (e != null) def = Math.max(1, e.getMaxLevel());
        } catch (Throwable ignored) {}
        return clampInt(enchantMaxLevel.getOrDefault(enchantKey.toLowerCase(), def), 1, 1000);
    }

    public void setEnchantMaxLevel(String enchantKey, int v) {
        if (enchantKey == null) return;
        enchantMaxLevel.put(enchantKey.toLowerCase(), clampInt(v, 1, 1000));
    }

    // --- Priority ---

    public List<String> getPriority() { return priority; }

    public void movePriority(String key, int toIndex) {
        if (key == null) return;
        int from = priority.indexOf(key);
        if (from < 0) return;
        if (toIndex < 0) toIndex = 0;
        if (toIndex >= priority.size()) toIndex = priority.size() - 1;
        if (from == toIndex) return;
        priority.remove(from);
        priority.add(toIndex, key);
    }

    // --- Internal helpers ---

    private void ensureAllKnownEnchantsPresent() {
        Map<String, Enchantment> known = new HashMap<>();
        for (Enchantment e : Enchantment.values()) {
            if (e == null || e.getKey() == null) continue;
            String key = e.getKey().toString().toLowerCase();
            known.putIfAbsent(key, e);
        }

        // Default costs + caps to vanilla-ish
        for (Map.Entry<String, Enchantment> en : known.entrySet()) {
            String key = en.getKey();
            Enchantment e = en.getValue();
            enchantBaseCost.putIfAbsent(key, 1);
            enchantPerLevelCost.putIfAbsent(key, 1);
            enchantMaxLevel.putIfAbsent(key, Math.max(1, e.getMaxLevel()));
        }

        // Priority: if missing or empty, seed with sorted keys
        if (priority.isEmpty()) {
            List<String> keys = new ArrayList<>(known.keySet());
            keys.sort(String::compareTo);
            priority.addAll(keys);
        }
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
