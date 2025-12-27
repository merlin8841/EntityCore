package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilConfig {

    private final JavaPlugin plugin;

    public ExtendedAnvilConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public double getApplyMultiplier() {
        return clamp(plugin.getConfig().getDouble("extendedanvil.apply.multiplier", 1.0), 0.1, 10.0);
    }

    public double getRefundFirst() {
        return clamp(plugin.getConfig().getDouble("extendedanvil.refund.first", 0.75), 0.0, 1.0);
    }

    public double getRefundSecond() {
        return clamp(plugin.getConfig().getDouble("extendedanvil.refund.second", 0.25), 0.0, 1.0);
    }

    public double getRefundAfter() {
        return clamp(plugin.getConfig().getDouble("extendedanvil.refund.after", 0.0), 0.0, 1.0);
    }

    public List<String> getPriorityKeys() {
        List<String> list = plugin.getConfig().getStringList("extendedanvil.disenchant.priority");
        if (list == null) return Collections.emptyList();
        List<String> cleaned = new ArrayList<>();
        for (String s : list) {
            if (s == null) continue;
            String k = normalizeKey(s);
            if (!k.isBlank()) cleaned.add(k);
        }
        return cleaned;
    }

    /**
     * Choose next enchantment to remove when 2+ books are provided.
     * Uses configured priority first; falls back to sorted key order.
     */
    public Enchantment chooseNextDisenchant(Set<Enchantment> present) {
        if (present == null || present.isEmpty()) return null;

        Map<String, Enchantment> byKey = new HashMap<>();
        for (Enchantment e : present) {
            if (e == null) continue;
            byKey.put(e.getKey().toString(), e);
        }

        for (String p : getPriorityKeys()) {
            Enchantment hit = byKey.get(p);
            if (hit != null) return hit;
        }

        // fallback: alphabetical by key
        List<String> keys = new ArrayList<>(byKey.keySet());
        Collections.sort(keys);
        return byKey.get(keys.get(0));
    }

    /**
     * Cap lookup:
     * - If "extendedanvil.caps.<minecraft:key>" exists, use it.
     * - If not, use vanilla max passed in.
     * - If cap is 0 => disabled
     */
    public int getCapFor(String enchantKey, int vanillaMax) {
        String k = "extendedanvil.caps." + normalizeKey(enchantKey);
        if (!plugin.getConfig().contains(k)) return vanillaMax;

        int cap = plugin.getConfig().getInt(k, vanillaMax);
        if (cap < 0) cap = 0;
        if (cap > 255) cap = 255;
        return cap;
    }

    public void adjustRefund(String path, double delta) {
        double v = plugin.getConfig().getDouble(path, 0.0);
        v = clamp(v + delta, 0.0, 1.0);
        plugin.getConfig().set(path, v);
        plugin.saveConfig();
    }

    public void adjustMultiplier(double delta) {
        double v = getApplyMultiplier();
        v = clamp(v + delta, 0.1, 10.0);
        plugin.getConfig().set("extendedanvil.apply.multiplier", v);
        plugin.saveConfig();
    }

    public void resetPriority() {
        plugin.getConfig().set("extendedanvil.disenchant.priority", java.util.List.of(
                "minecraft:mending",
                "minecraft:unbreaking",
                "minecraft:protection",
                "minecraft:sharpness",
                "minecraft:efficiency",
                "minecraft:fortune",
                "minecraft:looting",
                "minecraft:silk_touch",
                "minecraft:feather_falling"
        ));
        plugin.saveConfig();
    }

    public void movePriorityUp(String key) {
        List<String> list = getPriorityKeys();
        int idx = list.indexOf(normalizeKey(key));
        if (idx <= 0) return;
        Collections.swap(list, idx, idx - 1);
        plugin.getConfig().set("extendedanvil.disenchant.priority", list);
        plugin.saveConfig();
    }

    public void movePriorityDown(String key) {
        List<String> list = getPriorityKeys();
        int idx = list.indexOf(normalizeKey(key));
        if (idx < 0 || idx >= list.size() - 1) return;
        Collections.swap(list, idx, idx + 1);
        plugin.getConfig().set("extendedanvil.disenchant.priority", list);
        plugin.saveConfig();
    }

    public void movePriorityToTop(String key) {
        List<String> list = getPriorityKeys();
        String k = normalizeKey(key);
        if (!list.remove(k)) return;
        list.add(0, k);
        plugin.getConfig().set("extendedanvil.disenchant.priority", list);
        plugin.saveConfig();
    }

    public void movePriorityToBottom(String key) {
        List<String> list = getPriorityKeys();
        String k = normalizeKey(key);
        if (!list.remove(k)) return;
        list.add(k);
        plugin.getConfig().set("extendedanvil.disenchant.priority", list);
        plugin.saveConfig();
    }

    public void adjustCap(String enchantKey, int delta) {
        String k = "extendedanvil.caps." + normalizeKey(enchantKey);
        int current = plugin.getConfig().getInt(k, -1);
        if (current < 0) {
            // set from vanilla max if unknown
            Enchantment e = Enchantment.getByKey(NamespacedKey.fromString(normalizeKey(enchantKey)));
            current = (e != null) ? e.getMaxLevel() : 1;
        }
        int next = current + delta;
        if (next < 0) next = 0;
        if (next > 255) next = 255;

        plugin.getConfig().set(k, next);
        plugin.saveConfig();
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String normalizeKey(String s) {
        if (s == null) return "";
        String x = s.trim().toLowerCase(Locale.ROOT);
        if (x.isEmpty()) return "";
        if (!x.contains(":")) x = "minecraft:" + x;
        return x;
    }
}
