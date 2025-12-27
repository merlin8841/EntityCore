package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
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

    /** Fallback refund value when a book/item has no stored cost metadata. */
    private int refundLevelsPerEnchantLevel = 4;

    private boolean allowCurseRemoval = true;

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

        // Priority list
        priority.clear();
        List<String> raw = yml.getStringList("disenchant.priority");
        if (raw != null) {
            for (String s : raw) {
                if (s == null || s.trim().isEmpty()) continue;
                priority.add(s.trim().toLowerCase());
            }
        }

        // Seed missing entries so both YAML + GUI can manage everything
        ensureAllKnownEnchantsPresent();
        if (priority.isEmpty()) {
            seedPriorityWithAllEnchants();
        } else {
            ensureAllKnownEnchantsInPriority();
        }

        save();
    }

    public void save() {
        if (yml == null || file == null) return;

        yml.set("refund.percent.first", refundPercentFirst);
        yml.set("refund.percent.second", refundPercentSecond);
        yml.set("refund.percent.later", refundPercentLater);
        yml.set("refund.levelsPerEnchantLevel", refundLevelsPerEnchantLevel);

        yml.set("disenchant.allowCurseRemoval", allowCurseRemoval);

        yml.set("enchant.applyCost.globalBaseLevels", applyCostGlobalBaseLevels);
        yml.set("enchant.applyCost.addPerEnchant", applyCostPerEnchantAdd);
        yml.set("enchant.applyCost.addPerStoredLevel", applyCostPerStoredLevelAdd);

        yml.set("enchant.priorWork.costPerStep", priorWorkCostPerStep);
        yml.set("enchant.priorWork.incrementPerApply", priorWorkIncrementPerApply);

        yml.set("enchant.baseCost", new LinkedHashMap<>(enchantBaseCost));
        yml.set("enchant.perLevelCost", new LinkedHashMap<>(enchantPerLevelCost));

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

    public int getRefundPercentLater() { return refundPercentLater; }
    public void setRefundPercentLater(int v) { refundPercentLater = clampInt(v, 0, 100); }

    public int getRefundLevelsPerEnchantLevel() { return refundLevelsPerEnchantLevel; }
    public void setRefundLevelsPerEnchantLevel(int v) { refundLevelsPerEnchantLevel = clampInt(v, 0, 1000); }

    public boolean isAllowCurseRemoval() { return allowCurseRemoval; }
    public void setAllowCurseRemoval(boolean v) { allowCurseRemoval = v; }

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

    // --- Priority ---

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

    private void ensureAllKnownEnchantsInPriority() {
        Set<String> set = new LinkedHashSet<>(priority);
        for (Enchantment e : Enchantment.values()) {
            NamespacedKey key = e.getKey();
            if (key == null) continue;
            set.add(key.toString().toLowerCase());
        }
        priority.clear();
        priority.addAll(set);
    }

    private void ensureAllKnownEnchantsPresent() {
        // Seed default costs for all known enchants if missing.
        // These defaults are intentionally low; you can tune in GUI/YAML.
        for (Enchantment e : Enchantment.values()) {
            NamespacedKey k = e.getKey();
            if (k == null) continue;
            String key = k.toString().toLowerCase();
            enchantBaseCost.putIfAbsent(key, defaultBaseCostFor(key));
            enchantPerLevelCost.putIfAbsent(key, defaultPerLevelCostFor(key));
        }
    }

    private int defaultBaseCostFor(String key) {
        // Sensible "vanilla-ish" starting points, not perfect parity.
        if (key == null) return 0;
        if (key.endsWith("mending")) return 2;
        if (key.endsWith("unbreaking")) return 2;
        if (key.endsWith("efficiency")) return 2;
        if (key.endsWith("sharpness") || key.endsWith("protection")) return 2;
        if (key.endsWith("fortune") || key.endsWith("looting")) return 3;
        if (key.endsWith("silk_touch")) return 3;
        if (key.endsWith("thorns")) return 4;
        if (key.endsWith("swift_sneak")) return 4;
        return 1;
    }

    private int defaultPerLevelCostFor(String key) {
        if (key == null) return 0;
        if (key.endsWith("mending") || key.endsWith("silk_touch")) return 0;
        if (key.endsWith("fortune") || key.endsWith("looting")) return 2;
        if (key.endsWith("efficiency") || key.endsWith("sharpness") || key.endsWith("protection")) return 1;
        if (key.endsWith("unbreaking")) return 1;
        return 1;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
