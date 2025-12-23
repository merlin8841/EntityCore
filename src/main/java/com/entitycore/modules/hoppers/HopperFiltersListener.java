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

import java.util.HashMap;
import java.util.Map;

public final class HopperFiltersListener implements Listener {

    private final HopperFilterData data;
    private final HopperFiltersMenu menu;

    // Small anti-double-fire guard: one tick cooldown per hopper
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
        Inventory top = event.getView().getTopInventory();
        if (top == null || !menu.isOurMenu(top)) return;

        // Block shift-clicking into the menu (prevents weirdness)
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

        // Prevent interacting with buttons area
        if (slot > HopperFiltersMenu.FILTER_END && slot < top.getSize()) {
            event.setCancelled(true);
        }
        // Filter slots are editable normally (0-24).
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top == null || !menu.isOurMenu(top)) return;

        // Prevent dragging onto button slots or outside filter area
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
        Inventory top = event.getView().getTopInventory();
        if (top == null || !menu.isOurMenu(top)) return;

        if (event.getPlayer() instanceof Player p) {
            menu.saveAndClose(p, top);
        }
    }

    /* ===============================================================
       HOPPER MOVE FIX: 1 item per move, no stack deletion
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // We only take control if initiator is a hopper
        if (!(event.getInitiator().getHolder() instanceof Hopper initiatorHopper)) return;

        Block hopperBlock = initiatorHopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Always enforce 1-item movement to prevent stack-eat bugs
        // (also allows consistent behavior for all hoppers, filtered or not)
        event.setCancelled(true);

        // Anti-double-fire gate within same server tick
        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        Inventory source = event.getSource();
        Inventory dest = event.getDestination();
        ItemStack proposed = event.getItem();
        if (proposed == null || proposed.getType() == Material.AIR) return;

        String key = proposed.getType().getKey().toString();

        // If destination is a hopper with filter ON, apply it on intake
        if (dest.getHolder() instanceof Hopper destHopper) {
            Block destBlock = destHopper.getBlock();
            if (!data.allows(destBlock, key)) return;
        }

        // If initiator hopper itself has filter ON, apply it (controls what it moves)
        if (!data.allows(hopperBlock, key)) return;

        // Move exactly 1 item safely
        moveOneSafely(source, dest, proposed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;

        Block hopperBlock = hopper.getBlock();
        if (hopperBlock.getType() != Material.HOPPER) return;

        // Take control
        event.setCancelled(true);

        if (isGated(hopperBlock)) return;
        gate(hopperBlock);

        Item entityItem = event.getItem();
        ItemStack stack = entityItem.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) return;

        String key = stack.getType().getKey().toString();
        if (!data.allows(hopperBlock, key)) return;

        // Try insert 1 item into hopper inventory
        ItemStack one = stack.clone();
        one.setAmount(1);

        Map<Integer, ItemStack> leftovers = event.getInventory().addItem(one);
        if (!leftovers.isEmpty()) return; // can't fit

        // Remove 1 from entity stack safely
        int amt = stack.getAmount();
        if (amt <= 1) {
            entityItem.remove();
        } else {
            stack.setAmount(amt - 1);
            entityItem.setItemStack(stack);
        }
    }

    private void moveOneSafely(Inventory source, Inventory dest, ItemStack matchByType) {
        if (source == null || dest == null) return;

        // Find one matching stack in source
        int srcSlot = -1;
        ItemStack srcStack = null;

        for (int i = 0; i < source.getSize(); i++) {
            ItemStack it = source.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;

            // Match by material only (stable & fast). If you want meta-sensitive later, change to isSimilar().
            if (it.getType() == matchByType.getType()) {
                srcSlot = i;
                srcStack = it;
                break;
            }
        }

        if (srcSlot < 0 || srcStack == null) return;

        ItemStack one = srcStack.clone();
        one.setAmount(1);

        // Try adding first; if it fails, do nothing (prevents losses)
        Map<Integer, ItemStack> leftovers = dest.addItem(one);
        if (!leftovers.isEmpty()) return;

        // Now remove 1 from source
        int amt = srcStack.getAmount();
        if (amt <= 1) {
            source.setItem(srcSlot, null);
        } else {
            srcStack.setAmount(amt - 1);
            source.setItem(srcSlot, srcStack);
        }
    }

    private String key(Block hopperBlock) {
        return hopperBlock.getWorld().getUID() + ":" + hopperBlock.getX() + ":" + hopperBlock.getY() + ":" + hopperBlock.getZ();
    }

    private boolean isGated(Block hopperBlock) {
        Long tick = hopperTickGate.get(key(hopperBlock));
        long now = hopperBlock.getWorld().getFullTime();
        return tick != null && tick == now;
    }

    private void gate(Block hopperBlock) {
        hopperTickGate.put(key(hopperBlock), hopperBlock.getWorld().getFullTime());
    }
}
