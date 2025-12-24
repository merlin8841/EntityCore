package com.entitycore.modules.hoppers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class HopperFilterData {

    public static final int FILTER_SLOTS = 25;

    private final JavaPlugin plugin;
    private final NamespacedKey keyEnabled;
    private final NamespacedKey keyFilters;

    /**
     * Runtime cache (prevents PDC timing issues).
     * Key: worldUUID:x:y:z
     */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Runtime enabled set so we don't scan chunks every tick.
     * Key: worldUUID:x:y:z
     */
    private final Set<String> enabled = ConcurrentHashMap.newKeySet();

    public HopperFilterData(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyEnabled = new NamespacedKey(plugin, "hf_enabled");
        this.keyFilters = new NamespacedKey(plugin, "hf_filters");
    }

    public boolean isHopperBlock(Block block) {
        return block != null
                && block.getType() == Material.HOPPER
                && block.getState() instanceof TileState;
    }

    public boolean isEnabled(Block hopperBlock) {
        if (!isHopperBlock(hopperBlock)) return false;

        String k = locKey(hopperBlock);

        // Fast path: enabled set knows
        if (enabled.contains(k)) return true;

        CacheEntry ce = cache.get(k);
        if (ce != null) return ce.enabled;

        CacheEntry loaded = loadFromPdc(hopperBlock);
        cache.put(k, loaded);

        if (loaded.enabled) enabled.add(k);
        return loaded.enabled;
    }

    public void setEnabled(Block hopperBlock, boolean enabledNow) {
        if (!isHopperBlock(hopperBlock)) return;

        String k = locKey(hopperBlock);

        CacheEntry ce = cache.computeIfAbsent(k, kk -> new CacheEntry());
        ce.enabled = enabledNow;
        ce.recomputeAllowed();

        if (enabledNow) enabled.add(k);
        else enabled.remove(k);

        TileState state = (TileState) hopperBlock.getState();
        PersistentDataContainer pdc = state.getPersistentDataContainer();
        pdc.set(keyEnabled, PersistentDataType.BYTE, enabledNow ? (byte) 1 : (byte) 0);
        state.update(true, false);
    }

    /**
     * 25-length list of namespaced keys ("minecraft:dirt") or "".
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

        if (loaded.enabled) enabled.add(k);
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

    /**
     * Allow logic:
     * - Toggle OFF: allow all (and listener will not intercept).
     * - Toggle ON: STRICT whitelist.
     *   - Empty whitelist => allow NOTHING.
     */
    public boolean allows(Block hopperBlock, String materialKey) {
        if (!isHopperBlock(hopperBlock)) return true;

        String k = locKey(hopperBlock);

        CacheEntry ce = cache.get(k);
        if (ce == null) {
            ce = loadFromPdc(hopperBlock);
            cache.put(k, ce);
            if (ce.enabled) enabled.add(k);
        }

        if (!ce.enabled) return true; // OFF => allow all

        String key = normalizeKey(materialKey);
        if (key.isBlank()) return false;

        if (ce.allowedSet.isEmpty()) return false; // strict: empty = allow nothing

        return ce.allowedSet.contains(key);
    }

    /**
     * Snapshot of enabled hopper keys for safe iteration.
     */
    public List<String> enabledKeysSnapshot() {
        return new ArrayList<>(enabled);
    }

    /**
     * Resolve a key back to a Block (may return null if world/chunk unloaded).
     */
    public Block resolveKey(String key) {
        if (key == null) return null;
        String[] parts = key.split(":");
        if (parts.length != 4) return null;

        UUID worldId;
        try {
            worldId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        World w = Bukkit.getWorld(worldId);
        if (w == null) return null;

        int x, y, z;
        try {
            x = Integer.parseInt(parts[1]);
            y = Integer.parseInt(parts[2]);
            z = Integer.parseInt(parts[3]);
        } catch (NumberFormatException ex) {
            return null;
        }

        return w.getBlockAt(x, y, z);
    }

    /**
     * Optional: one-time bootstrap to populate enabled set from currently loaded tile entities.
     * Call once at module enable to avoid "needs first toggle after reboot".
     */
    public void bootstrapEnabledFromLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                for (var state : chunk.getTileEntities()) {
                    if (!(state instanceof TileState ts)) continue;
                    Block b = ts.getBlock();
                    if (b.getType() != Material.HOPPER) continue;

                    PersistentDataContainer pdc = ts.getPersistentDataContainer();
                    Byte enabledByte = pdc.get(keyEnabled, PersistentDataType.BYTE);
                    boolean en = enabledByte != null && enabledByte == (byte) 1;

                    if (!en) continue;

                    String k = locKey(b);
                    enabled.add(k);

                    CacheEntry ce = cache.computeIfAbsent(k, kk -> new CacheEntry());
                    ce.enabled = true;

                    String raw = pdc.get(keyFilters, PersistentDataType.STRING);
                    List<String> list = new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));
                    if (raw != null && !raw.isBlank()) {
                        String[] f = raw.split("\\|", -1);
                        for (int i = 0; i < FILTER_SLOTS && i < f.length; i++) {
                            list.set(i, normalizeKey(f[i]));
                        }
                    }
                    ce.filters = list;
                    ce.recomputeAllowed();
                }
            }
        }
        plugin.getLogger().info("[HopperFilters] Bootstrapped enabled filtered hoppers: " + enabled.size());
    }

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
