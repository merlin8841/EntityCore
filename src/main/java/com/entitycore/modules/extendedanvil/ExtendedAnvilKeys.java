package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** NamespacedKeys used by ExtendedAnvil. */
public final class ExtendedAnvilKeys {

    private ExtendedAnvilKeys() {}

    /** Tracks how many times a specific enchant has been removed from an item. */
    public static NamespacedKey removalCount(Plugin plugin, String enchantKey) {
        String safe = safeKey(enchantKey);
        return new NamespacedKey(plugin, "ea_removed_" + safe);
    }

    /** Vanilla-like "prior work" counter stored on items that are modified via /ea. */
    public static NamespacedKey priorWork(Plugin plugin) {
        return new NamespacedKey(plugin, "ea_prior_work");
    }

    /** Stored per-enchant intrinsic cost on enchanted books (so combining/reapplying stays consistent). */
    public static NamespacedKey bookEnchantCost(Plugin plugin, String enchantKey) {
        String safe = safeKey(enchantKey);
        return new NamespacedKey(plugin, "ea_cost_" + safe);
    }

    private static String safeKey(String enchantKey) {
        if (enchantKey == null) return "unknown";
        return enchantKey.toLowerCase().replace(':', '_');
    }
}
