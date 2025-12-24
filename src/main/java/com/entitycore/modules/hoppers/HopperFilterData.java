package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class HopperFilterData {

    public static final int FILTER_SLOTS = 25;

    private final NamespacedKey keyEnabled;
    private final NamespacedKey keyFilters;

    /**
     * Runtime cache to avoid PDC timing/desync.
     * Key: worldUUID:x:y:z
     */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public HopperFilterData(JavaPlugin plugin) {
        this.keyEnabled = new NamespacedKey(plugin, "hf_enabled");
        this.keyFilters = new NamespacedKey(plugin, "hf_filters");
    }

    public boolean isHopperBlock(Block block) {
        return block != null
                && block.getType() == Material.HOPPER
                && block.getState() instanceof TileState;
    }

    /* ===============================================================
       Enabled (toggle)
       =============================================================== */

    public boolean isEnabled(Block hopperBlock) {
        if (!isHopperBlock(hopperBlock)) return false;

        String k = locKey(hopperBlock);
        CacheEntry ce = cache.get(k);
        if (ce != null) return ce.enabled;

        CacheEntry loaded = loadFromPdc(hopperBlock);
        cache.put(k, loaded);
        return loaded.enabled;
    }

    public void setEnabled(Block hopperBlock, boolean enabled) {
        if (!isHopperBlock(hopperBlock)) return;

        String k = locKey(hopperBlock);
        CacheEntry ce = cache.computeIfAbsent(k, kk -> new CacheEntry());
        ce.enabled = enabled;
        ce.recomputeAllowed();

        TileState state = (TileState) hopperBlock.getState();
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(keyEnabled, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
        state.update(true, false);
    }

    /* ===============================================================
       Filters
       =============================================================== */

    /**
     * Returns 25-length list of namespaced keys ("minecraft:dirt") or "".
     */
    public List<String> getFilters(Block hopperBlock) {
        if (!isHopperBlock(hopperBlock)) {
            return new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));
        }

        String k = locKey(hopperBlock);
        CacheEntry ce = cache.get(k);
        if (ce != null && ce.filters != null) {
            return new ArrayList<>(ce.filters);
        }

        CacheEntry loaded = loadFromPdc(hopperBlock);
        cache.put(k, loaded);
        return new ArrayList<>(loaded.filters);
    }

    public void setFilters(Block hopperBlock, List<String> filters) {
        if (!isHopperBlock(hopperBlock)) return;

        List<String> normalized = new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));
        if (filters != null) {
            for (int i = 0; i < FILTER_SLOTS && i < filters.size(); i++) {
                normalized.set(i, normalizeKey(filters.get(i)));
            }
        }

        String k = locKey(hopperBlock);
        CacheEntry ce = cache.computeIfAbsent(k, kk -> new CacheEntry());
        ce.filters = normalized;
        ce.recomputeAllowed();

        TileState state = (TileState) hopperBlock.getState();
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(keyFilters, PersistentDataType.STRING, String.join("|", normalized));
        state.update(true, false);
    }

    /* ===============================================================
       Allow logic
       =============================================================== */

    /**
     * Allow logic:
     * - Toggle OFF: allow everything (and in practice listener should not intercept).
     * - Toggle ON: STRICT whitelist.
     *   - If whitelist empty: allow NOTHING.
     */
    public boolean allows(Block hopperBlock, String materialKey) {
        if (!isHopperBlock(hopperBlock)) return true;

        String k = locKey(hopperBlock);
        CacheEntry ce = cache.get(k);
        if (ce == null) {
            ce = loadFromPdc(hopperBlock);
            cache.put(k, ce);
        }

        // Toggle off => allow all
        if (!ce.enabled) return true;

        String key = normalizeKey(materialKey);
        if (key.isBlank()) return false;

        // Strict: empty whitelist means allow nothing
        if (ce.allowedSet.isEmpty()) return false;

        return ce.allowedSet.contains(key);
    }

    /* ===============================================================
       Internal helpers
       =============================================================== */

    private CacheEntry loadFromPdc(Block hopperBlock) {
        CacheEntry ce = new CacheEntry();

        TileState state = (TileState) hopperBlock.getState();
        PersistentDataContainer pdc = state.getPersistentDataContainer();

        Byte b = pdc.get(keyEnabled, PersistentDataType.BYTE);
        ce.enabled = (b != null && b == (byte) 1);

        String raw = pdc.get(keyFilters, PersistentDataType.STRING);
        List<String> list = new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));

        if (raw != null && !raw.isBlank()) {
            String[] parts = raw.split("\\|", -1);
            for (int i = 0; i < FILTER_SLOTS && i < parts.length; i++) {
                list.set(i, normalizeKey(parts[i]));
            }
        }

        ce.filters = list;
        ce.recomputeAllowed();
        return ce;
    }

    private String locKey(Block b) {
        // Correct key format: worldUUID:x:y:z
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private String normalizeKey(String key) {
        if (key == null) return "";
        String s = key.trim();
        if (s.isEmpty()) return "";
        s = s.toLowerCase(Locale.ROOT);
        if (!s.contains(":")) s = "minecraft:" + s;
        return s;
    }

    private static final class CacheEntry {
        boolean enabled = false;
        List<String> filters = new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));
        Set<String> allowedSet = new HashSet<>();

        void recomputeAllowed() {
            allowedSet.clear();
            if (filters == null) return;
            for (String f : filters) {
                if (f == null) continue;
                String s = f.trim();
                if (!s.isEmpty()) allowedSet.add(s);
            }
        }
    }
}
