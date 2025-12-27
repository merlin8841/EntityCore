package com.entitycore.modules.anvil;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class DisenchantPriority {

    private DisenchantPriority() {}

    // Default fallback order if config is empty.
    private static final List<String> DEFAULT_PRIORITY = Arrays.asList(
            "minecraft:mending",
            "minecraft:unbreaking",
            "minecraft:efficiency",
            "minecraft:fortune",
            "minecraft:looting",
            "minecraft:sharpness",
            "minecraft:protection",
            "minecraft:power",
            "minecraft:infinity",
            "minecraft:silk_touch",
            "minecraft:thorns",
            "minecraft:respiration",
            "minecraft:aqua_affinity",
            "minecraft:feather_falling",
            "minecraft:depth_strider",
            "minecraft:frost_walker",
            "minecraft:fire_aspect",
            "minecraft:knockback",
            "minecraft:punch",
            "minecraft:flame",
            "minecraft:impaling",
            "minecraft:channeling",
            "minecraft:loyalty",
            "minecraft:riptide",
            "minecraft:binding_curse",
            "minecraft:vanishing_curse"
    );

    public static Enchantment chooseOne(JavaPlugin plugin, Set<Enchantment> present) {
        if (present == null || present.isEmpty()) return null;

        Map<String, Enchantment> byKey = new HashMap<String, Enchantment>();
        for (Enchantment e : present) {
            if (e == null) continue;
            byKey.put(e.getKey().toString(), e);
        }

        List<String> configured = plugin.getConfig().getStringList("extendedanvil.disenchant.priority");
        List<String> order = (configured != null && !configured.isEmpty()) ? configured : DEFAULT_PRIORITY;

        for (String k : order) {
            if (k == null) continue;
            Enchantment match = byKey.get(k.trim().toLowerCase(Locale.ROOT));
            if (match != null) return match;
        }

        // Fallback deterministic: alphabetical by key
        List<Enchantment> list = new ArrayList<Enchantment>(present);
        Collections.sort(list, new Comparator<Enchantment>() {
            @Override
            public int compare(Enchantment a, Enchantment b) {
                return a.getKey().toString().compareTo(b.getKey().toString());
            }
        });

        return list.isEmpty() ? null : list.get(0);
    }
}
