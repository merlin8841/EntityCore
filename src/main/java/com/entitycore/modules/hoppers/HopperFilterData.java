package com.entitycore.modules.hoppers;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HopperFilterData {

    public static final int FILTER_SLOTS = 25;

    private final NamespacedKey keyEnabled;
    private final NamespacedKey keyFilters; // stored as 25 entries joined by ';'

    public HopperFilterData(JavaPlugin plugin) {
        this.keyEnabled = new NamespacedKey(plugin, "hf_enabled");
        this.keyFilters = new NamespacedKey(plugin, "hf_filters");
    }

    public boolean isHopperBlock(Block block) {
        return block != null && block.getType().name().equals("HOPPER");
    }

    public boolean isEnabled(Block hopperBlock) {
        TileState tile = getTile(hopperBlock);
        if (tile == null) return false;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        Byte b = pdc.get(keyEnabled, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    public void setEnabled(Block hopperBlock, boolean enabled) {
        TileState tile = getTile(hopperBlock);
        if (tile == null) return;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        pdc.set(keyEnabled, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
        tile.update(true);
    }

    /**
     * Returns 25 entries (size always = FILTER_SLOTS).
     * Each entry is either "" (empty) or a material key string like "minecraft:stone".
     */
    public List<String> getFilters(Block hopperBlock) {
        TileState tile = getTile(hopperBlock);
        if (tile == null) return emptyFilters();

        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        String raw = pdc.get(keyFilters, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return emptyFilters();

        String[] parts = raw.split(";", -1);
        List<String> out = new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));
        for (int i = 0; i < Math.min(parts.length, FILTER_SLOTS); i++) {
            out.set(i, parts[i] == null ? "" : parts[i]);
        }
        return out;
    }

    public void setFilters(Block hopperBlock, List<String> filters25) {
        TileState tile = getTile(hopperBlock);
        if (tile == null) return;

        List<String> fixed = new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));
        for (int i = 0; i < Math.min(filters25.size(), FILTER_SLOTS); i++) {
            fixed.set(i, filters25.get(i) == null ? "" : filters25.get(i));
        }

        String joined = String.join(";", fixed);
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        pdc.set(keyFilters, PersistentDataType.STRING, joined);
        tile.update(true);
    }

    /**
     * Whitelist behavior:
     * - If enabled=false -> allow everything
     * - If enabled=true and filter list empty -> allow everything
     * - If enabled=true and filter list has items -> allow only those materials
     */
    public boolean allows(Block hopperBlock, String materialKey) {
        if (materialKey == null || materialKey.isBlank()) return true;

        if (!isEnabled(hopperBlock)) return true;

        List<String> filters = getFilters(hopperBlock);
        boolean any = filters.stream().anyMatch(s -> s != null && !s.isBlank());
        if (!any) return true;

        for (String f : filters) {
            if (f != null && !f.isBlank() && f.equals(materialKey)) return true;
        }
        return false;
    }

    private TileState getTile(Block block) {
        if (block == null) return null;
        if (!(block.getState() instanceof TileState tile)) return null;
        return tile;
    }

    private List<String> emptyFilters() {
        return new ArrayList<>(Collections.nCopies(FILTER_SLOTS, ""));
    }
}
