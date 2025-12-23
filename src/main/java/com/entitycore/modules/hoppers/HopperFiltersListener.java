package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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

        if (event.getPlayer() instanceof Player p) {
            menu.save(p, top);
        }
    }

    /* ===============================================================
       HOPPER TRANSFER CONTROL (NO LOSS VERSION)
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // Only handle hopper-initiated moves
        if (!(event.getInitiator().getHolder() instanceof Hopper initiatorHopper)) return;

        Block hopperBlock = initiatorHopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Gate per tick per hopper
        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        Inventory source = event.getSource();
        Inventory dest = event.getDestination();
        ItemStack moving = event.getItem();

        if (source == null || dest == null) return;
        if (moving == null || moving.getType() == Material.AIR) return;

        // Always cancel vanilla movement (prevents any stack yanks / desync)
        event.setCancelled(true);

        // IMPORTANT: some servers remove from source BEFORE event. If so, restore it.
        // We attempt to put the full stack back immediately.
        restoreToSource(source, moving, hopperBlock);

        // Now schedule our safe 1-item move next tick.
        // (Next tick = inventories are stable and no mid-transfer states)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Re-check inventories exist
            if (hopperBlock.getType() != Material.HOPPER) return;

            // Apply initiator filter (what this hopper is allowed to move)
            String key = moving.getType().getKey().toString();
            if (!data.allows(hopperBlock, key)) return;

            // Apply destination hopper intake filter (if destination is hopper)
            if (dest.getHolder() instanceof Hopper destHopper) {
                Block destBlock = destHopper.getBlock();
                if (!data.allows(destBlock, key)) return;
            }

            // Do safe one-item move by material type
            moveOneSafely(source, dest, moving.getType());
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

        Item entityItem = event.getItem();
        if (entityItem == null) return;

        ItemStack stack = entityItem.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        String key = stack.getType().getKey().toString();
        if (!data.allows(hopperBlock, key)) {
            event.setCancelled(true);
            return;
        }

        // We control pickup to ensure 1 item at a time
        event.setCancelled(true);

        ItemStack one = stack.clone();
        one.setAmount(1);

        Map<Integer, ItemStack> leftovers = event.getInventory().addItem(one);
        if (!leftovers.isEmpty()) return; // hopper full

        int amt = stack.getAmount();
        if (amt <= 1) {
            entityItem.remove();
        } else {
            stack.setAmount(amt - 1);
            entityItem.setItemStack(stack);
        }
    }

    /* ===============================================================
       Helpers
       =============================================================== */

    private void restoreToSource(Inventory source, ItemStack stack, Block hopperBlock) {
        if (stack == null || stack.getType() == Material.AIR) return;

        // Try add back exactly what the event claims was moved.
        // If it was NOT removed, addItem will just stack/fit and leftovers will be returned.
        Map<Integer, ItemStack> leftovers = source.addItem(stack.clone());

        // If there are leftovers, we drop them at the hopper to avoid deleting items.
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(left -> {
                if (left != null && left.getType() != Material.AIR && left.getAmount() > 0) {
                    hopperBlock.getWorld().dropItemNaturally(hopperBlock.getLocation().add(0.5, 1.0, 0.5), left);
                }
            });
        }
    }

    private void moveOneSafely(Inventory source, Inventory dest, Material type) {
        if (source == null || dest == null || type == null || type == Material.AIR) return;

        int srcSlot = -1;
        ItemStack srcStack = null;

        for (int i = 0; i < source.getSize(); i++) {
            ItemStack it = source.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
            if (it.getType() == type) {
                srcSlot = i;
                srcStack = it;
                break;
            }
        }

        if (srcSlot < 0 || srcStack == null) return;

        ItemStack one = new ItemStack(type, 1);

        Map<Integer, ItemStack> leftovers = dest.addItem(one);
        if (!leftovers.isEmpty()) return; // can't fit, do nothing

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
