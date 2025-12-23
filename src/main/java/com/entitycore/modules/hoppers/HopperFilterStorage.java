package com.entitycore.modules.hoppers;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

final class HopperFilterStorage {
    private HopperFilterStorage() {}

    private static NamespacedKey KEY_VERSION;
    private static NamespacedKey KEY_ENABLED;        // boolean byte
    private static NamespacedKey[] KEY_RULE;         // 0..4 (String)

    static void init(JavaPlugin plugin) {
        KEY_VERSION = new NamespacedKey(plugin, "hopper_filter_v2");
        KEY_ENABLED = new NamespacedKey(plugin, "hopper_filter_enabled");
        KEY_RULE = new NamespacedKey[5];
        for (int i = 0; i < 5; i++) {
            KEY_RULE[i] = new NamespacedKey(plugin, "hopper_filter_rule_" + i);
        }
    }

    static Optional<TileState> getTile(Block block) {
        if (block == null) return Optional.empty();
        BlockState st = block.getState();
        if (st instanceof TileState ts) return Optional.of(ts);
        return Optional.empty();
    }

    static boolean isEnabled(TileState ts) {
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        Byte b = pdc.get(KEY_ENABLED, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    static void setEnabled(TileState ts, boolean enabled) {
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        pdc.set(KEY_VERSION, PersistentDataType.INTEGER, 2);
        if (enabled) pdc.set(KEY_ENABLED, PersistentDataType.BYTE, (byte) 1);
        else pdc.remove(KEY_ENABLED);
        ts.update(true, false);
    }

    static Optional<String> getRule(TileState ts, int slot) {
        if (slot < 0 || slot > 4) return Optional.empty();
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(KEY_RULE[slot], PersistentDataType.STRING));
    }

    static void setRule(TileState ts, int slot, String ruleOrNull) {
        if (slot < 0 || slot > 4) return;
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        pdc.set(KEY_VERSION, PersistentDataType.INTEGER, 2);
        if (ruleOrNull == null || ruleOrNull.isBlank()) pdc.remove(KEY_RULE[slot]);
        else pdc.set(KEY_RULE[slot], PersistentDataType.STRING, ruleOrNull);
        ts.update(true, false);
    }

    static void clearAll(TileState ts) {
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        pdc.remove(KEY_VERSION);
        pdc.remove(KEY_ENABLED);
        for (int i = 0; i < 5; i++) pdc.remove(KEY_RULE[i]);
        ts.update(true, false);
    }

    static boolean hasAnyRule(TileState ts) {
        for (int i = 0; i < 5; i++) {
            if (getRule(ts, i).orElse(null) != null) return true;
        }
        return false;
    }
}
