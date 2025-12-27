package com.entitycore.modules.extendedanvil;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public final class ExtendedAnvilConfig {

    public ExtendedAnvilConfig(JavaPlugin plugin) {
        // later: load/save YAML settings
    }

    public Enchantment chooseNextDisenchant(Set<Enchantment> enchants) {
        return enchants.stream().findFirst().orElse(null);
    }
}
