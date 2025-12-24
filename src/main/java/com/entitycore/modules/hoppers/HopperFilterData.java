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

    private final NamespacedKey keyFilters;

    /**
     * Runtime cache to prevent any PDC timing/desync issues.
     * Key: worldUUID:x:y:z
     */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public HopperFilterData(JavaPlugin plugin) {
        this.keyFilters = new NamespacedKey(plugin, "hf_filters");
    }

    public boolean isHopperBlock(Block block) {
        return block != null
                && block.getType() == Material.HOPPER
                && block.getState() instanceof TileState;
    }

    /**
     * Always treated as enabled by design now.
     */
    public boolean isEnabled(Block hopperBlock) {
        return isHopperBlock(hopperBlock);
    }

    /**
     * Disabled on purpose (strict design): filter cannot be toggled off.
     */
    public void setEnabled(Block hopperBlock, boolean enabled) {
        // no-op by design
    }

    /**
     * Returns 25-length list of namespaced keys ("minecraft:dirt") or "".
     */
    public List<String> getFilters(Block hopperBlock) {
        if (!isHopperBlock(hopperBlock)) {
            return new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));
        }

        CacheEntry ce = cache.get(locKey(hopperBlock));
        if (ce != null && ce.filters != null) {
            return new ArrayList<>(ce.filters);
        }

        CacheEntry loaded = loadFromPdc(hopperBlock);
        cache.put(locKey(hopperBlock), loaded);
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

        // Persist to PDC for restarts
        TileState state = (TileState) hopperBlock.getState();
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(keyFilters, PersistentDataType.STRING, String.join("|", normalized));
        state.update(true, false);
    }

    /**
     * STRICT whitelist:
     * - If item is not in the filter slots, it is NOT allowed.
     * - Empty filter list => NOTHING is allowed.
     */
    public boolean allows(Block hopperBlock, String materialKey) {
        if (!isHopperBlock(hopperBlock)) return true;

        CacheEntry ce = cache.get(locKey(hopperBlock));
        if (ce == null) {
            ce = loadFromPdc(hopperBlock);
            cache.put(locKey(hopperBlock), ce);
        }

        String key = normalizeKey(materialKey);
        if (key.isBlank()) return false;

        // STRICT: empty allowedSet means allow nothing
        if (ce.allowedSet.isEmpty()) return false;

        return ce.allowedSet.contains(key);
    }

    private CacheEntry loadFromPdc(Block hopperBlock) {
        CacheEntry ce = new CacheEntry();

        TileState state = (TileState) hopperBlock.getState();
        PersistentDataContainer pdc = state.getPersistentDataContainer();

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
