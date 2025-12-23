package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class HopperListener implements Listener {

    private final JavaPlugin plugin;

    private final NamespacedKey LOCKED_KEY;
    private final NamespacedKey FILTER_KEY;

    public HopperListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.LOCKED_KEY = new NamespacedKey(plugin, "hopper_slot_locked");
        this.FILTER_KEY = new NamespacedKey(plugin, "hopper_filter_item");
    }

    /* ------------------------------------------------------
       SLOT LOCK / UNLOCK (CLICK INSIDE HOPPER GUI)
       ------------------------------------------------------ */
    @EventHandler
    public void onHopperClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= hopper.getInventory().getSize()) return;

        Block block = hopper.getBlock();
        PersistentDataContainer pdc = block.getState().getPersistentDataContainer();

        boolean locked = isSlotLocked(pdc, slot);

        // Toggle lock when clicking empty slot
        if (event.getCursor() == null || event.getCursor().getType().isAir()) {
            if (!locked) {
                setSlotLocked(pdc, slot, true);
                block.getState().update();

                event.getWhoClicked().sendMessage("§7Slot locked. Insert an item to filter.");
            } else {
                clearSlot(pdc, slot);
                block.getState().update();

                event.getWhoClicked().sendMessage("§7Slot unlocked.");
            }
        }
    }

    /* ------------------------------------------------------
       FILTER ITEM INSERTION
       ------------------------------------------------------ */
    @EventHandler
    public void onFilterInsert(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= hopper.getInventory().getSize()) return;

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;

        Block block = hopper.getBlock();
        PersistentDataContainer pdc = block.getState().getPersistentDataContainer();

        if (!isSlotLocked(pdc, slot)) return;
        if (hasFilter(pdc, slot)) return;

        // Store filter snapshot
        ItemStack filter = cursor.clone();
        filter.setAmount(1);

        storeFilter(pdc, slot, filter);
        block.getState().update();

        // Normalize item
        cursor.setAmount(cursor.getAmount() - 1);
        event.setCancelled(false);
    }

    /* ------------------------------------------------------
       HOPPER TRANSFER FILTERING
       ------------------------------------------------------ */
    @EventHandler
    public void onItemMove(InventoryMoveItemEvent event) {
        if (!(event.getDestination().getHolder() instanceof Hopper hopper)) return;

        Block block = hopper.getBlock();
        PersistentDataContainer pdc = block.getState().getPersistentDataContainer();

        if (!hasAnyLockedSlot(pdc)) return;

        ItemStack moving = event.getItem();

        boolean allowed = false;
        for (int i = 0; i < 5; i++) {
            if (!hasFilter(pdc, i)) continue;

            ItemStack filter = getFilter(pdc, i);
            if (matchesFilter(filter, moving)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            event.setCancelled(true);
        }
    }

    /* ------------------------------------------------------
       FILTER MATCHING RULES (LOCKED)
       ------------------------------------------------------ */
    private boolean matchesFilter(ItemStack filter, ItemStack item) {
        if (filter == null || item == null) return false;

        // Shulker boxes: match by material only
        if (filter.getType().name().endsWith("SHULKER_BOX")) {
            return filter.getType() == item.getType();
        }

        // Enchanted books: single enchant only
        if (filter.getType() == Material.ENCHANTED_BOOK
                && item.getType() == Material.ENCHANTED_BOOK) {

            EnchantmentStorageMeta fMeta = (EnchantmentStorageMeta) filter.getItemMeta();
            EnchantmentStorageMeta iMeta = (EnchantmentStorageMeta) item.getItemMeta();

            if (fMeta.getStoredEnchants().size() != 1
                    || iMeta.getStoredEnchants().size() != 1) return false;

            Map.Entry<Enchantment, Integer> f = fMeta.getStoredEnchants().entrySet().iterator().next();
            Map.Entry<Enchantment, Integer> i = iMeta.getStoredEnchants().entrySet().iterator().next();

            return f.getKey().equals(i.getKey()) && f.getValue().equals(i.getValue());
        }

        // All other items: material only (ignore name/lore)
        return filter.getType() == item.getType();
    }

    /* ------------------------------------------------------
       METADATA HELPERS
       ------------------------------------------------------ */
    private boolean isSlotLocked(PersistentDataContainer pdc, int slot) {
        return pdc.has(key(slot, "locked"), PersistentDataType.BYTE);
    }

    private void setSlotLocked(PersistentDataContainer pdc, int slot, boolean locked) {
        if (locked) {
            pdc.set(key(slot, "locked"), PersistentDataType.BYTE, (byte) 1);
        }
    }

    private boolean hasFilter(PersistentDataContainer pdc, int slot) {
        return pdc.has(key(slot, "filter"), PersistentDataType.BYTE_ARRAY);
    }

    private ItemStack getFilter(PersistentDataContainer pdc, int slot) {
        byte[] data = pdc.get(key(slot, "filter"), PersistentDataType.BYTE_ARRAY);
        return data == null ? null : ItemStack.deserializeBytes(data);
    }

    private void storeFilter(PersistentDataContainer pdc, int slot, ItemStack item) {
        pdc.set(key(slot, "filter"),
                PersistentDataType.BYTE_ARRAY,
                item.serializeAsBytes());
    }

    private void clearSlot(PersistentDataContainer pdc, int slot) {
        pdc.remove(key(slot, "locked"));
        pdc.remove(key(slot, "filter"));
    }

    private boolean hasAnyLockedSlot(PersistentDataContainer pdc) {
        for (int i = 0; i < 5; i++) {
            if (isSlotLocked(pdc, i)) return true;
        }
        return false;
    }

    private NamespacedKey key(int slot, String type) {
        return new NamespacedKey(plugin, "hopper_" + slot + "_" + type);
    }
}
