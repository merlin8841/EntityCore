package com.entitycore.modules.anvil;

import org.bukkit.enchantments.Enchantment;

import java.util.*;

public final class DisenchantPriority {

    // Fixed priority, no player selection.
    // If an enchant isn't listed, it falls back to alphabetical by key.
    private static final List<String> PRIORITY = List.of(
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
            // curses included and removable
            "minecraft:binding_curse",
            "minecraft:vanishing_curse"
    );

    private DisenchantPriority() {}

    public static Enchantment chooseOne(Set<Enchantment> present) {
        if (present == null || present.isEmpty()) return null;

        Map<String, Enchantment> byKey = new HashMap<>();
        for (Enchantment e : present) {
            if (e == null) continue;
            byKey.put(e.getKey().toString(), e);
        }

        for (String k : PRIORITY) {
            Enchantment e = byKey.get(k);
            if (e != null) return e;
        }

        // Fallback deterministic pick
        return present.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .findFirst()
                .orElse(null);
    }
}
