package com.entitycore.modules.infection;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectionKeys {

    private InfectionKeys() {}

    public static NamespacedKey seedKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "infection_seed");
    }
}
