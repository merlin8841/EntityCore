package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** NamespacedKeys used by ExtendedAnvil. */
public final class ExtendedAnvilKeys {

    private ExtendedAnvilKeys() {}

    /** Tracks how many times a specific enchant has been removed from an item. */
    public static NamespacedKey removalCount(Plugin plugin, String enchantKey) {
        String safe = enchantKey.toLowerCase().replace(':', '_');
        return new NamespacedKey(plugin, "ea_removed_" + safe);
    }
}
