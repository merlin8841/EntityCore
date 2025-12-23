package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class HopperFiltersListener implements Listener {

    private final HopperFilterData data;
    private final HopperFiltersMenu menu;

    // One-tick gate per hopper to avoid double-moving in same tick
    private final Map<String, Long> hopperTickGate = new HashMap<>();

    public HopperFiltersListener(HopperFilterData data, HopperFiltersMenu menu) {
        this.data = data;
        this.menu = menu;
    }

    /* ===============================================================
       OPEN MENU: sneak-interact hopper
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSneakInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.HOPPER) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        if (!player.hasPermission("entitycore.hopperfilters.use")) {
            player.sendMessage("§cYou don't have permission to use hopper filters.");
            return;
        }

        event.setCancelled(true);
        menu.open(player, block);
    }

    /* ===============================================================
       GUI HANDLERS
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!HopperFiltersMenu.TITLE.equals(event.getView().getTitle())) return;

        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        // Block shift-clicking into the menu
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getRawSlot();

        if (slot == HopperFiltersMenu.SLOT_TOGGLE) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                menu.toggle(p, top);
            }
            return;
        }

        if (slot == HopperFiltersMenu.SLOT_CLOSE) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }

        // Prevent interaction with button area (25,26)
        if (slot > HopperFiltersMenu.FILTER_END && slot < top.getSize()) {
            event.setCancelled(true);
        }
        // Slots 0-24: allowed (player edits filters)
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

        if (event.getPlayer() instanceof Player p) {
            menu.save(p, top);
        }
    }

    /* ===============================================================
       HOPPER MOVE CONTROL: cancel vanilla + move exactly 1 item safely
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // Only take control when a hopper is the initiator
        if (!(event.getInitiator().getHolder() instanceof Hopper initiatorHopper)) return;

        Block hopperBlock = initiatorHopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Cancel vanilla movement so we can do safe one-item movement
        event.setCancelled(true);

        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        Inventory source = event.getSource();
        Inventory dest = event.getDestination();
        ItemStack proposed = event.getItem();
        if (source == null || dest == null || proposed == null || proposed.getType() == Material.AIR) return;

        String key = proposed.getType().getKey().toString();

        // If destination is a hopper, apply its intake filter too
        if (dest.getHolder() instanceof Hopper destHopper) {
            Block destBlock = destHopper.getBlock();
            if (!data.allows(destBlock, key)) return;
        }

        // Apply filter of initiator hopper to what it moves
        if (!data.allows(hopperBlock, key)) return;

        moveOneSafely(source, dest, proposed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;

        Block hopperBlock = hopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Cancel vanilla pickup; do controlled 1-item pickup
        event.setCancelled(true);

        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        Item entityItem = event.getItem();
        ItemStack stack = entityItem.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        String key = stack.getType().getKey().toString();
        if (!data.allows(hopperBlock, key)) return;

        ItemStack one = stack.clone();
        one.setAmount(1);

        Map<Integer, ItemStack> leftovers = event.getInventory().addItem(one);
        if (!leftovers.isEmpty()) return;

        int amt = stack.getAmount();
        if (amt <= 1) {
            entityItem.remove();
        } else {
            stack.setAmount(amt - 1);
            entityItem.setItemStack(stack);
        }
    }

    private void moveOneSafely(Inventory source, Inventory dest, ItemStack matchByType) {
        int srcSlot = -1;
        ItemStack srcStack = null;

        for (int i = 0; i < source.getSize(); i++) {
            ItemStack it = source.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;

            if (it.getType() == matchByType.getType()) {
                srcSlot = i;
                srcStack = it;
                break;
            }
        }

        if (srcSlot < 0 || srcStack == null) return;

        ItemStack one = srcStack.clone();
        one.setAmount(1);

        Map<Integer, ItemStack> leftovers = dest.addItem(one);
        if (!leftovers.isEmpty()) return;

        int amt = srcStack.getAmount();
        if (amt <= 1) {
            source.setItem(srcSlot, null);
        } else {
            srcStack.setAmount(amt - 1);
            source.setItem(srcSlot, srcStack);
        }
    }

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
