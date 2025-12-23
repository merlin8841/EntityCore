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
    private static NamespacedKey KEY_LOCK_MASK;
    private static NamespacedKey[] KEY_SLOT_RULE; // 0..4

    static void init(JavaPlugin plugin) {
        KEY_VERSION = new NamespacedKey(plugin, "hopper_filter_v");
        KEY_LOCK_MASK = new NamespacedKey(plugin, "hopper_filter_lock_mask");
        KEY_SLOT_RULE = new NamespacedKey[5];
        for (int i = 0; i < 5; i++) {
            KEY_SLOT_RULE[i] = new NamespacedKey(plugin, "hopper_filter_rule_" + i);
        }
    }

    static Optional<TileState> getTileState(Block hopperBlock) {
        if (hopperBlock == null) return Optional.empty();
        BlockState state = hopperBlock.getState();
        if (!(state instanceof TileState ts)) return Optional.empty();
        return Optional.of(ts);
    }

    static int getLockMask(TileState ts) {
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        Integer mask = pdc.get(KEY_LOCK_MASK, PersistentDataType.INTEGER);
        return mask == null ? 0 : mask;
    }

    static void setLockMask(TileState ts, int mask) {
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        pdc.set(KEY_VERSION, PersistentDataType.INTEGER, 1);
        if (mask == 0) {
            pdc.remove(KEY_LOCK_MASK);
        } else {
            pdc.set(KEY_LOCK_MASK, PersistentDataType.INTEGER, mask);
        }
        ts.update(true, false);
    }

    static Optional<String> getRule(TileState ts, int slot) {
        if (slot < 0 || slot > 4) return Optional.empty();
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(KEY_SLOT_RULE[slot], PersistentDataType.STRING));
    }

    static void setRule(TileState ts, int slot, String rule) {
        if (slot < 0 || slot > 4) return;
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        if (rule == null || rule.isBlank()) {
            pdc.remove(KEY_SLOT_RULE[slot]);
        } else {
            pdc.set(KEY_VERSION, PersistentDataType.INTEGER, 1);
            pdc.set(KEY_SLOT_RULE[slot], PersistentDataType.STRING, rule);
        }
        ts.update(true, false);
    }

    static void clearAll(TileState ts) {
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        pdc.remove(KEY_VERSION);
        pdc.remove(KEY_LOCK_MASK);
        for (int i = 0; i < 5; i++) pdc.remove(KEY_SLOT_RULE[i]);
        ts.update(true, false);
    }

    static boolean isLocked(int mask, int slot) {
        return (mask & (1 << slot)) != 0;
    }

    static int lock(int mask, int slot) {
        return mask | (1 << slot);
    }

    static int unlock(int mask, int slot) {
        return mask & ~(1 << slot);
    }
}
