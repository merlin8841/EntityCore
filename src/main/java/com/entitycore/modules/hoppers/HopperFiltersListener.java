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

    // One-tick gate per hopper block
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

        // Block interaction in button area
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
       HOPPER MOVE CONTROL (LOCKED MODE)
       - We cancel vanilla hopper transfers and do our own 1-item transfer.
       - Works for:
         * container -> hopper (intake)
         * hopper -> container (outflow)
         * hopper -> hopper (chains)
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (!(event.getInitiator().getHolder() instanceof Hopper initiatorHopper)) return;

        Block hopperBlock = initiatorHopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Only "lock" behavior if this hopper is enabled (otherwise let vanilla run)
        if (!data.isEnabled(hopperBlock)) return;

        // Gate per tick per hopper
        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        // Always cancel vanilla behavior for enabled hoppers
        event.setCancelled(true);

        Inventory source = event.getSource();
        Inventory dest = event.getDestination();
        if (source == null || dest == null) return;

        // Schedule next tick for stable inventory operations
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (hopperBlock.getType() != Material.HOPPER) return;

            // Live hopper state
            if (!(hopperBlock.getState() instanceof Hopper liveHopper)) return;
            Inventory hopperInv = liveHopper.getInventory();
            if (hopperInv == null) return;

            boolean destIsThisHopper = isSameHopper(dest, hopperBlock);

            if (destIsThisHopper) {
                // INTAKE: container -> THIS hopper
                pullOneAllowedIntoHopper(source, hopperInv, hopperBlock);
            } else {
                // OUTFLOW: THIS hopper -> destination (container or another hopper)
                pushOneAllowedOutOfHopper(hopperInv, dest, hopperBlock);
            }
        });
    }

    /* ===============================================================
       ITEM ENTITY PICKUP CONTROL (LOCKED MODE)
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;

        Block hopperBlock = hopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Only lock behavior if enabled; otherwise vanilla pickup
        if (!data.isEnabled(hopperBlock)) return;

        // Gate per tick per hopper
        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        if (event.getItem() == null) return;
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        String key = stack.getType().getKey().toString();
        if (!data.allows(hopperBlock, key)) {
            // Not allowed into hopper
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
       Intake / Outflow helpers
       =============================================================== */

    private void pullOneAllowedIntoHopper(Inventory source, Inventory hopperInv, Block hopperBlock) {
        if (source == null || hopperInv == null) return;

        // Find first allowed item in source inventory
        for (int i = 0; i < source.getSize(); i++) {
            ItemStack it = source.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;

            String key = it.getType().getKey().toString();
            if (!data.allows(hopperBlock, key)) continue;

            // Try add 1 to hopper first
            ItemStack one = it.clone();
            one.setAmount(1);

            Map<Integer, ItemStack> leftovers = hopperInv.addItem(one);
            if (!leftovers.isEmpty()) {
                // Hopper full
                return;
            }

            // Then decrement source by 1
            int amt = it.getAmount();
            if (amt <= 1) {
                source.setItem(i, null);
            } else {
                it.setAmount(amt - 1);
                source.setItem(i, it);
            }
            return;
        }
    }

    private void pushOneAllowedOutOfHopper(Inventory hopperInv, Inventory dest, Block hopperBlock) {
        if (hopperInv == null || dest == null) return;

        // Find first allowed item in hopper inventory
        for (int i = 0; i < hopperInv.getSize(); i++) {
            ItemStack it = hopperInv.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;

            String key = it.getType().getKey().toString();
            if (!data.allows(hopperBlock, key)) continue;

            // Destination hopper intake filter (if destination is a hopper)
            if (dest.getHolder() instanceof Hopper destHopper) {
                Block destBlock = destHopper.getBlock();
                if (!data.allows(destBlock, key)) continue;
            }

            // Add 1 to destination first
            ItemStack one = it.clone();
            one.setAmount(1);

            Map<Integer, ItemStack> leftovers = dest.addItem(one);
            if (!leftovers.isEmpty()) {
                // Can't fit
                return;
            }

            // Then decrement hopper by 1
            int amt = it.getAmount();
            if (amt <= 1) {
                hopperInv.setItem(i, null);
            } else {
                it.setAmount(amt - 1);
                hopperInv.setItem(i, it);
            }
            return;
        }
    }

    private boolean isSameHopper(Inventory inv, Block hopperBlock) {
        if (inv == null || hopperBlock == null) return false;
        if (!(inv.getHolder() instanceof Hopper h)) return false;
        Block b = h.getBlock();
        return b.getWorld().equals(hopperBlock.getWorld())
                && b.getX() == hopperBlock.getX()
                && b.getY() == hopperBlock.getY()
                && b.getZ() == hopperBlock.getZ();
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
