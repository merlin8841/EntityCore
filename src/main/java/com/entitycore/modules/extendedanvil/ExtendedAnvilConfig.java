package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ExtendedAnvil config backed by plugins/EntityCore/extendedanvil.yml.
 *
 * Operator GUI edits this file.
 */
public final class ExtendedAnvilConfig {

    private static final String FILE_NAME = "extendedanvil.yml";

    private final JavaPlugin plugin;
    private File file;
    private YamlConfiguration yml;

    // Defaults (can be tuned in Operator GUI)
    private int refundPercentFirst = 75;
    private int refundPercentSecond = 25;
    private int refundPercentLater = 0;
    private int xpPerEnchantLevel = 12;
    private boolean allowCurseRemoval = true;
    private int applyBookLevelCost = 0;

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
        xpPerEnchantLevel = clampInt(yml.getInt("refund.xpPerEnchantLevel", xpPerEnchantLevel), 0, 1000);
        allowCurseRemoval = yml.getBoolean("disenchant.allowCurseRemoval", allowCurseRemoval);
        applyBookLevelCost = clampInt(yml.getInt("enchant.applyBookLevelCost", applyBookLevelCost), 0, 1000);

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
        yml.set("refund.xpPerEnchantLevel", xpPerEnchantLevel);
        yml.set("disenchant.allowCurseRemoval", allowCurseRemoval);
        yml.set("enchant.applyBookLevelCost", applyBookLevelCost);
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

    public int getXpPerEnchantLevel() { return xpPerEnchantLevel; }
    public void setXpPerEnchantLevel(int v) { xpPerEnchantLevel = clampInt(v, 0, 1000); }

    public boolean isAllowCurseRemoval() { return allowCurseRemoval; }
    public void setAllowCurseRemoval(boolean v) { allowCurseRemoval = v; }

    public int getApplyBookLevelCost() { return applyBookLevelCost; }
    public void setApplyBookLevelCost(int v) { applyBookLevelCost = clampInt(v, 0, 1000); }

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
