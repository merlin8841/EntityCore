package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilKeys {

    private final JavaPlugin plugin;

    public ExtendedAnvilKeys(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public NamespacedKey addCountKey(Enchantment ench) {
        return new NamespacedKey(plugin, "ea_add_" + safeKey(ench));
    }

    public NamespacedKey removeCountKey(Enchantment ench) {
        return new NamespacedKey(plugin, "ea_rem_" + safeKey(ench));
    }

    /** Per-item repair count (drives repair cost multiplier). */
    public NamespacedKey repairCountKey() {
        return new NamespacedKey(plugin, "ea_repair_count");
    }

    private static String safeKey(Enchantment ench) {
        if (ench == null || ench.getKey() == null) return "unknown";
        // namespace:key -> namespace__key
        return ench.getKey().getNamespace() + "__" + ench.getKey().getKey();
    }
}
