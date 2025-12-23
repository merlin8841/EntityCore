package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class HopperFilterListener implements Listener {

    private final JavaPlugin plugin;

    public HopperFilterListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------
    // CLICK / TOUCH LOCKING
    // -------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.HOPPER) return;

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Hopper hopperState)) return; // BlockState holder

        // Only handle clicks within top hopper inventory
        int raw = event.getRawSlot();
        if (raw < 0 || raw > 4) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTileState(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();
        int mask = HopperFilterStorage.getLockMask(ts);
        boolean filterMode = mask != 0;

        boolean slotLocked = HopperFilterStorage.isLocked(mask, raw);
        ItemStack slotItem = top.getItem(raw);
        ItemStack cursor = event.getCursor();

        // In filter mode:
        // - Locked slot item is frozen (can only unlock via click)
        // - Unlocked slots are disabled (no placing/taking)
        if (filterMode) {
            if (slotLocked) {
                // Clicking a locked slot toggles unlock, returning the frozen item
                event.setCancelled(true);

                ItemStack frozen = slotItem == null ? null : slotItem.clone();
                if (frozen != null && frozen.getType() != Material.AIR) {
                    // Remove frozen from slot
                    top.setItem(raw, null);

                    // Give back to cursor if empty, else into player inventory
                    if (cursor == null || cursor.getType() == Material.AIR) {
                        event.getView().setCursor(frozen);
                    } else {
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(frozen);
                        for (ItemStack over : overflow.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), over);
                        }
                    }
                }

                // Clear rule + unlock
                HopperFilterStorage.setRule(ts, raw, null);
                int newMask = HopperFilterStorage.unlock(mask, raw);

                if (newMask == 0) {
                    // No more locked slots -> back to vanilla
                    HopperFilterStorage.clearAll(ts);
                } else {
                    HopperFilterStorage.setLockMask(ts, newMask);
                }

                player.updateInventory();
                return;
            }

            // Slot is disabled in filter mode; allow locking it ONLY if empty and cursor has an item
            if ((slotItem == null || slotItem.getType() == Material.AIR)
                    && cursor != null && cursor.getType() != Material.AIR) {

                String rule = toRule(cursor);
                if (rule == null) {
                    // Unsupported item to define a filter
                    event.setCancelled(true);
                    return;
                }

                // Freeze ONE item in slot
                event.setCancelled(true);

                ItemStack frozen = cursor.clone();
                frozen.setAmount(1);
                top.setItem(raw, frozen);

                // Reduce cursor by 1
                if (cursor.getAmount() <= 1) {
                    event.getView().setCursor(null);
                } else {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.getView().setCursor(cursor);
                }

                HopperFilterStorage.setRule(ts, raw, rule);
                HopperFilterStorage.setLockMask(ts, HopperFilterStorage.lock(mask, raw));

                // Enforce disabled slots are empty
                clearUnlockedSlots(top, HopperFilterStorage.getLockMask(ts));

                player.updateInventory();
                return;
            }

            // Otherwise disabled: block any interaction
            event.setCancelled(true);
            return;
        }

        // Not in filter mode (vanilla hopper):
        // If player places an item into a slot, do nothing special (vanilla works).
        // BUT if player clicks an empty slot with an item on cursor, we treat that as "start filter mode".
        if ((slotItem == null || slotItem.getType() == Material.AIR)
                && cursor != null && cursor.getType() != Material.AIR) {

            String rule = toRule(cursor);
            if (rule == null) return; // allow vanilla if unsupported

            event.setCancelled(true);

            ItemStack frozen = cursor.clone();
            frozen.setAmount(1);
            top.setItem(raw, frozen);

            if (cursor.getAmount() <= 1) {
                event.getView().setCursor(null);
            } else {
                cursor.setAmount(cursor.getAmount() - 1);
                event.getView().setCursor(cursor);
            }

            HopperFilterStorage.setRule(ts, raw, rule);
            HopperFilterStorage.setLockMask(ts, HopperFilterStorage.lock(0, raw));

            clearUnlockedSlots(top, HopperFilterStorage.getLockMask(ts));

            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.HOPPER) return;

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Hopper hopperState)) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTileState(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();
        int mask = HopperFilterStorage.getLockMask(ts);
        if (mask == 0) return; // vanilla mode

        // Any drag into top inventory slots 0..4 is blocked, except we could allow
        // dragging into an unlocked empty slot to lock it (bedrock usually doesn't drag anyway).
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot <= 4) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private static void clearUnlockedSlots(Inventory hopperInv, int mask) {
        for (int i = 0; i < 5; i++) {
            if (!HopperFilterStorage.isLocked(mask, i)) {
                hopperInv.setItem(i, null);
            }
        }
    }

    // -------------------------
    // ROUTING / FILTERING
    // -------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveIntoHopper(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        InventoryHolder holder = dest.getHolder();
        if (!(holder instanceof Hopper hopperState)) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTileState(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();
        int mask = HopperFilterStorage.getLockMask(ts);
        if (mask == 0) return; // vanilla mode

        // Filter mode: never allow vanilla insertion into hopper inventory
        event.setCancelled(true);

        ItemStack moving = event.getItem();
        if (moving == null || moving.getType() == Material.AIR) return;

        if (!matchesAny(ts, mask, moving)) {
            return; // blocked: stays in source
        }

        Inventory out = getHopperOutputInventory(hopperBlock);
        if (out == null) {
            return;
        }

        // Try push into output
        ItemStack toInsert = moving.clone();
        Map<Integer, ItemStack> remainder = out.addItem(toInsert);

        int inserted = moving.getAmount();
        if (!remainder.isEmpty()) {
            int rem = remainder.values().iterator().next().getAmount();
            inserted = moving.getAmount() - rem;
        }

        if (inserted <= 0) return;

        // Remove inserted amount from source safely (anti-dupe)
        removeSimilar(event.getSource(), moving, inserted);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (!(holder instanceof Hopper hopperState)) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTileState(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();
        int mask = HopperFilterStorage.getLockMask(ts);
        if (mask == 0) return;

        event.setCancelled(true);

        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        if (!matchesAny(ts, mask, stack)) return;

        Inventory out = getHopperOutputInventory(hopperBlock);
        if (out == null) return;

        ItemStack toInsert = stack.clone();
        Map<Integer, ItemStack> remainder = out.addItem(toInsert);

        if (remainder.isEmpty()) {
            entity.remove();
            return;
        }

        // Partial
        ItemStack rem = remainder.values().iterator().next();
        entity.setItemStack(rem);
    }

    private Inventory getHopperOutputInventory(Block hopperBlock) {
        if (!(hopperBlock.getBlockData() instanceof Directional dir)) return null;
        Block face = hopperBlock.getRelative(dir.getFacing());

        BlockState st = face.getState();
        if (st instanceof Container c) {
            return c.getInventory();
        }
        if (st instanceof InventoryHolder ih) {
            return ih.getInventory();
        }
        return null;
    }

    private static void removeSimilar(Inventory source, ItemStack like, int amount) {
        int remaining = amount;
        ItemStack[] contents = source.getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() == Material.AIR) continue;
            if (!it.isSimilar(like)) continue;

            int take = Math.min(remaining, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) contents[i] = null;
            remaining -= take;

            if (remaining <= 0) break;
        }

        source.setContents(contents);
    }

    // -------------------------
    // RULES / MATCHING
    // -------------------------

    private boolean matchesAny(TileState ts, int mask, ItemStack item) {
        // Tools/armor: custom named OR enchanted => unsortable (ignored)
        if (isToolOrArmor(item.getType())) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) return false;
                if (meta.hasEnchants() && !meta.getEnchants().isEmpty()) return false;
            }
        }

        for (int slot = 0; slot < 5; slot++) {
            if (!HopperFilterStorage.isLocked(mask, slot)) continue;
            String rule = HopperFilterStorage.getRule(ts, slot).orElse(null);
            if (rule == null) continue;

            if (matchesRule(rule, item)) return true;
        }
        return false;
    }

    private boolean matchesRule(String rule, ItemStack item) {
        if (rule.startsWith("MAT:")) {
            Material mat = Material.matchMaterial(rule.substring("MAT:".length()));
            return mat != null && item.getType() == mat;
        }

        if (rule.startsWith("ENCH:")) {
            // Match enchantment type on enchanted books
            if (item.getType() != Material.ENCHANTED_BOOK) return false;
            String keyStr = rule.substring("ENCH:".length());

            Enchantment ench = Enchantment.getByKey(org.bukkit.NamespacedKey.fromString(keyStr));
            if (ench == null) return false;

            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof EnchantmentStorageMeta esm)) return false;

            return esm.hasStoredEnchant(ench);
        }

        return false;
    }

    private String toRule(ItemStack cursor) {
        if (cursor == null || cursor.getType() == Material.AIR) return null;

        // Enchanted book => ENCH:<key> (first stored enchant)
        if (cursor.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta meta = cursor.getItemMeta();
            if (!(meta instanceof EnchantmentStorageMeta esm)) return null;
            Map<Enchantment, Integer> stored = esm.getStoredEnchants();
            if (stored.isEmpty()) return null;

            Enchantment first = stored.keySet().iterator().next();
            if (first.getKey() == null) return null;
            return "ENCH:" + first.getKey().toString();
        }

        // Everything else: material match (includes shulker boxes by color/material)
        return "MAT:" + cursor.getType().name();
    }

    private static boolean isToolOrArmor(Material mat) {
        String n = mat.name();

        // Armor
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) return true;

        // Common tools/weapons
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) return true;
        if (n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT")) return true;
        if (n.equals("SHIELD") || n.equals("ELYTRA")) return true;

        return false;
    }
}
