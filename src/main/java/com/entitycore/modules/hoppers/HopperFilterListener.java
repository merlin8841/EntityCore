package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
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
    // CLICK / TOUCH LOCKING (Cross-play)
    // -------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.HOPPER) return;

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Hopper hopperState)) return;

        // Only handle clicks within top hopper inventory slots 0..4
        int raw = event.getRawSlot();
        if (raw < 0 || raw > 4) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTileState(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();
        int mask = HopperFilterStorage.getLockMask(ts);

        boolean slotLocked = HopperFilterStorage.isLocked(mask, raw);
        ItemStack slotItem = top.getItem(raw);
        ItemStack cursor = event.getCursor();
        ItemStack hand = player.getInventory().getItemInMainHand();

        boolean cursorEmpty = (cursor == null || cursor.getType() == Material.AIR);
        boolean handEmpty = (hand == null || hand.getType() == Material.AIR);
        boolean slotEmpty = (slotItem == null || slotItem.getType() == Material.AIR);

        boolean filterMode = (mask != 0);

        // -------------------------
        // VANILLA MODE (mask == 0)
        // -------------------------
        if (!filterMode) {
            // Cross-play activation:
            // Tap empty slot with EMPTY hand/cursor -> toggles slot into "locked (armed)" state
            // (No placeholder item. Slot stays visually empty.)
            if (slotEmpty && cursorEmpty && handEmpty) {
                event.setCancelled(true);
                int newMask = HopperFilterStorage.lock(0, raw);
                HopperFilterStorage.setLockMask(ts, newMask);
                // rule stays null until they set it
                player.updateInventory();
                return;
            }

            // Optional Java convenience:
            // Clicking empty slot with an item on CURSOR (or in HAND) will directly arm+set rule (freeze 1 item)
            // This is what you asked to keep for Java players.
            ItemStack offered = !cursorEmpty ? cursor : (!handEmpty ? hand : null);
            if (slotEmpty && offered != null && offered.getType() != Material.AIR) {
                String rule = toRule(offered);
                if (rule == null) return; // if unsupported, let vanilla handle it

                event.setCancelled(true);
                // Arm + freeze 1 item
                int newMask = HopperFilterStorage.lock(0, raw);
                HopperFilterStorage.setLockMask(ts, newMask);
                freezeOneIntoSlot(top, raw, event, player, offered, !cursorEmpty);

                HopperFilterStorage.setRule(ts, raw, rule);

                // Disable all other slots (keep empty)
                clearUnlockedSlots(top, HopperFilterStorage.getLockMask(ts));
                player.updateInventory();
                return;
            }

            // Otherwise: vanilla interaction
            return;
        }

        // -------------------------
        // FILTER MODE (mask != 0)
        // -------------------------

        // If slot is locked:
        if (slotLocked) {
            // If slot currently has a frozen item:
            if (!slotEmpty) {
                // Unlock only when player has empty cursor+hand (Bedrock friendly)
                // (prevents accidental removal while trying to place items)
                if (!cursorEmpty || !handEmpty) {
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);

                // Return frozen item
                ItemStack frozen = slotItem.clone();
                top.setItem(raw, null);

                Map<Integer, ItemStack> overflow = player.getInventory().addItem(frozen);
                for (ItemStack over : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), over);
                }

                HopperFilterStorage.setRule(ts, raw, null);
                int newMask = HopperFilterStorage.unlock(mask, raw);

                if (newMask == 0) {
                    // Back to vanilla
                    HopperFilterStorage.clearAll(ts);
                } else {
                    HopperFilterStorage.setLockMask(ts, newMask);
                    clearUnlockedSlots(top, newMask);
                }

                player.updateInventory();
                return;
            }

            // Locked but empty = "armed slot".
            // If player offers an item (cursor preferred, else hand), set rule + freeze 1.
            ItemStack offered = !cursorEmpty ? cursor : (!handEmpty ? hand : null);
            if (slotEmpty && offered != null && offered.getType() != Material.AIR) {
                String rule = toRule(offered);
                if (rule == null) {
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);

                freezeOneIntoSlot(top, raw, event, player, offered, !cursorEmpty);
                HopperFilterStorage.setRule(ts, raw, rule);

                clearUnlockedSlots(top, mask);
                player.updateInventory();
                return;
            }

            // Locked empty + empty hand/cursor -> toggle unlock (disarm)
            if (slotEmpty && cursorEmpty && handEmpty) {
                event.setCancelled(true);

                HopperFilterStorage.setRule(ts, raw, null);
                int newMask = HopperFilterStorage.unlock(mask, raw);

                if (newMask == 0) {
                    HopperFilterStorage.clearAll(ts);
                } else {
                    HopperFilterStorage.setLockMask(ts, newMask);
                    clearUnlockedSlots(top, newMask);
                }
                player.updateInventory();
                return;
            }

            // Otherwise block
            event.setCancelled(true);
            return;
        }

        // Slot is NOT locked in filter mode -> disabled.
        // Allow: tap empty disabled slot with empty hand/cursor => lock (arm) it.
        if (slotEmpty && cursorEmpty && handEmpty) {
            event.setCancelled(true);
            int newMask = HopperFilterStorage.lock(mask, raw);
            HopperFilterStorage.setLockMask(ts, newMask);
            clearUnlockedSlots(top, newMask);
            player.updateInventory();
            return;
        }

        // Block any other interaction with disabled slots
        event.setCancelled(true);
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

        int mask = HopperFilterStorage.getLockMask(tsOpt.get());
        if (mask == 0) return; // vanilla mode

        // Any drag affecting top slots 0..4 is blocked
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot <= 4) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private static void freezeOneIntoSlot(
            Inventory hopperInv,
            int slot,
            InventoryClickEvent event,
            Player player,
            ItemStack offered,
            boolean fromCursor
    ) {
        ItemStack frozen = offered.clone();
        frozen.setAmount(1);
        hopperInv.setItem(slot, frozen);

        if (fromCursor) {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) return;

            if (cursor.getAmount() <= 1) event.setCursor(null);
            else {
                cursor.setAmount(cursor.getAmount() - 1);
                event.setCursor(cursor);
            }
        } else {
            // from main hand
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) return;

            if (hand.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
            else {
                hand.setAmount(hand.getAmount() - 1);
                player.getInventory().setItemInMainHand(hand);
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
        if (mask == 0) return; // vanilla hopper

        ItemStack moving = event.getItem();
        if (moving == null || moving.getType().isAir()) return;

        // Never allow items to enter hopper inventory in filter mode.
        // If it doesn't match, block it.
        if (!matchesAny(ts, mask, moving)) {
            event.setCancelled(true);
            return;
        }

        // Must have a valid output container to move anything.
        Inventory out = getHopperOutputInventory(hopperBlock);
        if (out == null) {
            // "If there's no container attached, it just doesn't filter items."
            // Cancel so it doesn't enter hopper buffer.
            event.setCancelled(true);
            return;
        }

        // Cancel vanilla insert into hopper; we route directly to output
        event.setCancelled(true);

        ItemStack toInsert = moving.clone();
        Map<Integer, ItemStack> remainder = out.addItem(toInsert);

        int inserted = moving.getAmount();
        if (!remainder.isEmpty()) {
            int rem = remainder.values().iterator().next().getAmount();
            inserted = moving.getAmount() - rem;
        }

        if (inserted <= 0) return;

        // Remove from source only what actually inserted
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

        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (stack == null || stack.getType().isAir()) return;

        if (!matchesAny(ts, mask, stack)) {
            event.setCancelled(true);
            return;
        }

        Inventory out = getHopperOutputInventory(hopperBlock);
        if (out == null) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        ItemStack toInsert = stack.clone();
        Map<Integer, ItemStack> remainder = out.addItem(toInsert);

        if (remainder.isEmpty()) {
            entity.remove();
            return;
        }

        // partial insert
        ItemStack rem = remainder.values().iterator().next();
        entity.setItemStack(rem);
    }

    /**
     * Prevent the hopper from pushing its frozen template items out.
     * Vanilla will try to move items from hopper -> container. In filter mode, cancel it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPushOut(InventoryMoveItemEvent event) {
        Inventory src = event.getSource();
        InventoryHolder holder = src.getHolder();
        if (!(holder instanceof Hopper hopperState)) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTileState(hopperBlock);
        if (tsOpt.isEmpty()) return;

        int mask = HopperFilterStorage.getLockMask(tsOpt.get());
        if (mask == 0) return;

        // In filter mode, hopper inventory only holds frozen templates; never push anything out.
        event.setCancelled(true);
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
            if (it == null || it.getType().isAir()) continue;
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

            // Locked slot may be "armed" with no rule yet; ignore until rule set.
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
            if (item.getType() != Material.ENCHANTED_BOOK) return false;
            String keyStr = rule.substring("ENCH:".length());

            Enchantment ench = Enchantment.getByKey(NamespacedKey.fromString(keyStr));
            if (ench == null) return false;

            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof EnchantmentStorageMeta esm)) return false;

            return esm.hasStoredEnchant(ench);
        }

        return false;
    }

    private String toRule(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;

        // Enchanted book => ENCH:<key> (first stored enchant)
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof EnchantmentStorageMeta esm)) return null;
            Map<Enchantment, Integer> stored = esm.getStoredEnchants();
            if (stored.isEmpty()) return null;

            Enchantment first = stored.keySet().iterator().next();
            if (first.getKey() == null) return null;
            return "ENCH:" + first.getKey();
        }

        // Everything else: material match (includes shulker boxes by color/material)
        return "MAT:" + stack.getType().name();
    }

    private static boolean isToolOrArmor(Material mat) {
        String n = mat.name();

        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) return true;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) return true;
        if (n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT")) return true;
        if (n.equals("SHIELD") || n.equals("ELYTRA")) return true;

        return false;
    }
}
