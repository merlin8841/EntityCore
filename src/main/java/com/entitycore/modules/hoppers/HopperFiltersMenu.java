package com.entitycore.modules.hoppers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public final class HopperFiltersMenu {

    public static final int SIZE = 27;
    public static final int SLOT_TOGGLE = 25;
    public static final int SLOT_CLOSE = 26;
    public static final int FILTER_START = 0;
    public static final int FILTER_END = 24;
    public static final String TITLE = "Hopper Filter";

    private final HopperFilterData data;

    private final Map<UUID, HopperRef> openEditors = new HashMap<>();

    public HopperFiltersMenu(HopperFilterData data) {
        this.data = data;
    }

    public Block getEditingHopper(Player player) {
        if (player == null) return null;
        HopperRef ref = openEditors.get(player.getUniqueId());
        return ref == null ? null : ref.resolve();
    }

    public void open(Player player, Block hopperBlock) {
        if (player == null || hopperBlock == null) return;
        if (!data.isHopperBlock(hopperBlock)) return;

        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);

        List<String> filters = data.getFilters(hopperBlock);
        for (int i = FILTER_START; i <= FILTER_END; i++) {
            String key = (i < filters.size()) ? filters.get(i) : "";
            if (key == null || key.isBlank()) {
                inv.setItem(i, null);
                continue;
            }

            Material mat = materialFromKey(key);
            if (mat == null) {
                inv.setItem(i, null);
                continue;
            }

            inv.setItem(i, new ItemStack(mat, 1));
        }

        inv.setItem(SLOT_TOGGLE, toggleItem(data.isEnabled(hopperBlock)));
        inv.setItem(SLOT_CLOSE, closeItem());

        openEditors.put(player.getUniqueId(), HopperRef.of(hopperBlock));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    public void persist(Player player, Inventory inv) {
        if (player == null || inv == null) return;

        Block hopper = getEditingHopper(player);
        if (hopper == null || !data.isHopperBlock(hopper)) return;

        List<String> filters = new ArrayList<>(Collections.nCopies(HopperFilterData.FILTER_SLOTS, ""));

        for (int i = FILTER_START; i <= FILTER_END; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == Material.AIR) {
                filters.set(i, "");
                continue;
            }
            filters.set(i, it.getType().getKey().toString());
        }

        data.setFilters(hopper, filters);
    }

    public void saveAndClose(Player player, Inventory inv) {
        if (player == null || inv == null) return;
        persist(player, inv);
        openEditors.remove(player.getUniqueId());
    }

    public void toggle(Player player, Inventory inv) {
        if (player == null || inv == null) return;

        Block hopper = getEditingHopper(player);
        if (hopper == null) return;

        // Persist current filters first
        persist(player, inv);

        boolean now = !data.isEnabled(hopper);
        data.setEnabled(hopper, now);

        inv.setItem(SLOT_TOGGLE, toggleItem(now));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        player.sendMessage(now ? "§aHopper filter enabled." : "§7Hopper filter disabled (vanilla).");
    }

    private ItemStack toggleItem(boolean enabled) {
        ItemStack it = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(enabled ? "§aFilter: ON" : "§7Filter: OFF");
            meta.setLore(List.of(
                    "§fON: strict whitelist (only items in slots 1-25).",
                    "§7OFF: vanilla hopper behavior."
            ));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack closeItem() {
        ItemStack it = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cClose");
            it.setItemMeta(meta);
        }
        return it;
    }

    private Material materialFromKey(String key) {
        if (key == null || key.isBlank()) return null;

        String s = key;
        if (s.startsWith("minecraft:")) s = s.substring("minecraft:".length());
        s = s.toUpperCase(Locale.ROOT);

        return Material.matchMaterial(s);
    }

    private static final class HopperRef {
        private final UUID worldId;
        private final int x, y, z;

        private HopperRef(UUID worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static HopperRef of(Block b) {
            return new HopperRef(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }

        Block resolve() {
            var world = Bukkit.getWorld(worldId);
            return world == null ? null : world.getBlockAt(x, y, z);
        }
    }
}
