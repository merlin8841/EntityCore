package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class HopperFiltersListener implements Listener {

    private final JavaPlugin plugin;
    private final HopperFilterData data;
    private final HopperFiltersMenu menu;

    // One-tick gate per hopper block (prevents double scheduling same tick)
    private final Map<String, Long> hopperTickGate = new HashMap<>();

    public HopperFiltersListener(JavaPlugin plugin, HopperFilterData data, HopperFiltersMenu menu) {
        this.plugin = plugin;
        this.data = data;
        this.menu = menu;
    }

    /* ===============================================================
       GUI HANDLERS (COMMAND-OPENED ONLY)
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!HopperFiltersMenu.TITLE.equals(event.getView().getTitle())) return;

        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();

        if (slot == HopperFiltersMenu.SLOT_TOGGLE) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player p) {
                menu.toggle(p, top);
            }
            return;
        }

        if (slot == HopperFiltersMenu.SLOT_CLOSE) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }

        if (slot > HopperFiltersMenu.FILTER_END && slot < top.getSize()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        if (!HopperFiltersMenu.TITLE.equals(event.getView().getTitle())) return;

        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == HopperFiltersMenu.SLOT_TOGGLE || rawSlot == HopperFiltersMenu.SLOT_CLOSE) {
                event.setCancelled(true);
                return;
            }
            if (rawSlot > HopperFiltersMenu.FILTER_END && rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuClose(InventoryCloseEvent event) {
        if (!HopperFiltersMenu.TITLE.equals(event.getView().getTitle())) return;

        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        if (event.getPlayer() instanceof org.bukkit.entity.Player p) {
            menu.save(p, top);
        }
    }

    /* ===============================================================
       HOPPER CONTROL (NO RESTORE, NO EVENT-STACK MUTATION)
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // Initiator must be a hopper (this is the "moving" hopper)
        if (!(event.getInitiator().getHolder() instanceof Hopper initiatorHopper)) return;

        Block hopperBlock = initiatorHopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Gate per tick per hopper
        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        // Always cancel vanilla movement. We will perform a controlled 1-item push next tick
        // based on the hopper's own inventory state.
        event.setCancelled(true);

        Inventory hopperInv = initiatorHopper.getInventory();
        Inventory dest = event.getDestination();
        if (hopperInv == null || dest == null) return;

        // Next tick: push exactly 1 item from hopper inventory into destination.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (hopperBlock.getType() != Material.HOPPER) return;

            // Re-resolve holder in case chunk unloaded/reloaded
            if (!(hopperBlock.getState() instanceof org.bukkit.block.Hopper liveHopper)) return;

            Inventory liveHopperInv = liveHopper.getInventory();
            if (liveHopperInv == null) return;

            // Find first movable item in hopper inventory that passes filter rules
            int srcSlot = -1;
            ItemStack srcStack = null;

            for (int i = 0; i < liveHopperInv.getSize(); i++) {
                ItemStack it = liveHopperInv.getItem(i);
                if (it == null || it.getType() == Material.AIR) continue;

                String key = it.getType().getKey().toString();

                // Initiator filter: what this hopper may move (whitelist logic)
                if (!data.allows(hopperBlock, key)) continue;

                // Destination hopper intake filter (if destination is hopper)
                if (dest.getHolder() instanceof Hopper destHopper) {
                    Block destBlock = destHopper.getBlock();
                    if (!data.allows(destBlock, key)) continue;
                }

                srcSlot = i;
                srcStack = it;
                break;
            }

            if (srcSlot < 0 || srcStack == null) return;

            // Move exactly 1 item, safely: add to dest first, then remove from hopper
            ItemStack one = srcStack.clone();
            one.setAmount(1);

            Map<Integer, ItemStack> leftovers = dest.addItem(one);
            if (!leftovers.isEmpty()) return; // can't fit; do nothing

            int amt = srcStack.getAmount();
            if (amt <= 1) {
                liveHopperInv.setItem(srcSlot, null);
            } else {
                srcStack.setAmount(amt - 1);
                liveHopperInv.setItem(srcSlot, srcStack);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;

        Block hopperBlock = hopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Gate per tick per hopper
        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        if (event.getItem() == null) return;
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        String key = stack.getType().getKey().toString();
        if (!data.allows(hopperBlock, key)) {
            event.setCancelled(true);
            return;
        }

        // Force 1 item pickup (we control it)
        event.setCancelled(true);

        ItemStack one = stack.clone();
        one.setAmount(1);

        Map<Integer, ItemStack> leftovers = event.getInventory().addItem(one);
        if (!leftovers.isEmpty()) return;

        int amt = stack.getAmount();
        if (amt <= 1) {
            event.getItem().remove();
        } else {
            stack.setAmount(amt - 1);
            event.getItem().setItemStack(stack);
        }
    }

    /* ===============================================================
       Gate helpers
       =============================================================== */

    private String gateKey(Block hopperBlock) {
        return hopperBlock.getWorld().getUID() + ":" + hopperBlock.getX() + ":" + hopperBlock.getY() + ":" + hopperBlock.getZ();
    }

    private boolean isGated(Block hopperBlock) {
        Long tick = hopperTickGate.get(gateKey(hopperBlock));
        long now = hopperBlock.getWorld().getFullTime();
        return tick != null && tick == now;
    }

    private void gate(Block hopperBlock) {
        hopperTickGate.put(gateKey(hopperBlock), hopperBlock.getWorld().getFullTime());
    }
}
