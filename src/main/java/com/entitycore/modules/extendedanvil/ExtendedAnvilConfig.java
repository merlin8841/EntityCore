package com.entitycore.modules.extendedanvil;

import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilConfig {

    private final JavaPlugin plugin;

    private boolean refundsEnabled;

    private final Map<String, Integer> caps = new HashMap<>();
    private final List<String> priority = new ArrayList<>();

    public ExtendedAnvilConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.getConfig().addDefault("extendedanvil.refunds-enabled", true);
        plugin.getConfig().addDefault("extendedanvil.default-cap", 10);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        this.refundsEnabled = plugin.getConfig().getBoolean("extendedanvil.refunds-enabled", true);

        int defaultCap = plugin.getConfig().getInt("extendedanvil.default-cap", 10);
        if (defaultCap < 0) defaultCap = 0;

        // load caps
        caps.clear();
        if (plugin.getConfig().isConfigurationSection("extendedanvil.caps")) {
            for (String k : plugin.getConfig().getConfigurationSection("extendedanvil.caps").getKeys(false)) {
                int v = plugin.getConfig().getInt("extendedanvil.caps." + k, defaultCap);
                caps.put(k, v);
            }
        }

        // load priority
        priority.clear();
        List<String> list = plugin.getConfig().getStringList("extendedanvil.priority");
        if (list != null && !list.isEmpty()) {
            priority.addAll(list);
        } else {
            // default: alphabetical of all enchants
            List<String> keys = new ArrayList<>();
            for (Enchantment e : Registry.ENCHANTMENT) {
                keys.add(e.getKey().toString());
            }
            keys.sort(String::compareTo);
            priority.addAll(keys);
        }

        // ensure caps exist for all enchants (lazy default)
        for (Enchantment e : Registry.ENCHANTMENT) {
            String key = e.getKey().toString();
            if (!caps.containsKey(key)) caps.put(key, defaultCap);
        }

        save();
    }

    public void save() {
        plugin.getConfig().set("extendedanvil.refunds-enabled", refundsEnabled);
        plugin.getConfig().set("extendedanvil.priority", new ArrayList<>(priority));
        for (Map.Entry<String, Integer> e : caps.entrySet()) {
            plugin.getConfig().set("extendedanvil.caps." + e.getKey(), e.getValue());
        }
        plugin.saveConfig();
    }

    public boolean isRefundsEnabled() {
        return refundsEnabled;
    }

    public void setRefundsEnabled(boolean enabled) {
        this.refundsEnabled = enabled;
    }

    public int getCap(String enchantKey) {
        Integer v = caps.get(enchantKey);
        return v == null ? 10 : v;
    }

    public void adjustCap(String enchantKey, int delta) {
        int cur = getCap(enchantKey);
        int next = cur + delta;
        if (next < 0) next = 0;
        if (next > 255) next = 255;
        caps.put(enchantKey, next);
    }

    public List<String> getPriorityKeys() {
        return new ArrayList<>(priority);
    }

    public void movePriority(int index, int delta) {
        int to = index + delta;
        if (index < 0 || index >= priority.size()) return;
        if (to < 0 || to >= priority.size()) return;
        String v = priority.remove(index);
        priority.add(to, v);
    }

    public Enchantment chooseNextDisenchant(Set<Enchantment> enchantsOnItem) {
        if (enchantsOnItem == null || enchantsOnItem.isEmpty()) return null;

        // prefer configured order
        for (String key : priority) {
            for (Enchantment e : enchantsOnItem) {
                if (e != null && e.getKey().toString().equalsIgnoreCase(key)) return e;
            }
        }

        // fallback
        return enchantsOnItem.iterator().next();
    }
}
