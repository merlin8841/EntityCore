package com.entitycore.modules.hoppers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class HopperFiltersListener implements Listener {

    private final JavaPlugin plugin;
    private final HopperFilterData data;
    private final HopperFiltersMenu menu;

    private BukkitTask tickTask;

    // Operator-controlled speed (ticks between iterations). 1 = fastest.
    private int tickInterval = 1;

    public HopperFiltersListener(JavaPlugin plugin, HopperFilterData data, HopperFiltersMenu menu) {
        this.plugin = plugin;
        this.data = data;
        this.menu = menu;
    }

    /* ===============================================================
       SPEED CONTROL (OP GUI)
       =============================================================== */

    public int getTickInterval() {
        return tickInterval;
    }

    public void setTickInterval(int newInterval) {
        int clamped = Math.max(1, Math.min(20, newInterval)); // 1..20 ticks
        if (this.tickInterval == clamped) return;

        this.tickInterval = clamped;

        // Restart task to apply immediately
        if (tickTask != null) {
            restart();
        }
    }

    /* ===============================================================
       LIFECYCLE
       =============================================================== */

    public void start() {
        if (tickTask != null) return;

        // Helps avoid “doesn’t work until re-toggle after reboot”
        data.bootstrapEnabledFromLoadedChunks();

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, tickInterval, tickInterval);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void restart() {
        stop();
        start();
    }

    /* ===============================================================
       FILTER UI EVENTS (COMMAND-OPENED)
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

        int raw = event.getRawSlot();

        if (raw == HopperFiltersMenu.SLOT_TOGGLE) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player p) {
                menu.toggle(p, top);
            }
            return;
        }

        if (raw == HopperFiltersMenu.SLOT_CLOSE) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            return;
        }

        if (raw > HopperFiltersMenu.FILTER_END && raw < top.getSize()) {
            event.setCancelled(true);
            return;
        }

        if (event.getWhoClicked() instanceof org.bukkit.entity.Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> menu.persist(p, top));
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

        if (event.getWhoClicked() instanceof org.bukkit.entity.Player p) {
            Bukkit.getScheduler().runTask(plugin, () -> menu.persist(p, top));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuClose(InventoryCloseEvent event) {
        if (!HopperFiltersMenu.TITLE.equals(event.getView().getTitle())) return;

        Inventory top = event.getView().getTopInventory();
        if (top == null) return;

        if (event.getPlayer() instanceof org.bukkit.entity.Player p) {
            menu.saveAndClose(p, top);
        }
    }

    /* ===============================================================
       READ-ONLY HOPPER GUI WHEN FILTER ENABLED
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;
        if (!data.isEnabled(hopper.getBlock())) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperGuiDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;
        if (!data.isEnabled(hopper.getBlock())) return;

        event.setCancelled(true);
    }

    /* ===============================================================
       BLOCK VANILLA TRANSFERS WHEN FILTER ENABLED
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (event.getDestination().getHolder() instanceof Hopper dest) {
            if (data.isEnabled(dest.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.getInitiator().getHolder() instanceof Hopper initiator) {
            if (data.isEnabled(initiator.getBlock())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        if (!(event.getInventory().getHolder() instanceof Hopper hopper)) return;
        if (data.isEnabled(hopper.getBlock())) {
            event.setCancelled(true);
        }
    }

    /* ===============================================================
       TICK LOOP: PURGE + CUSTOM MOVE
       =============================================================== */

    private void tick() {
        List<String> keys = data.enabledKeysSnapshot();
        if (keys.isEmpty()) return;

        for (String key : keys) {
            Block b = data.resolveKey(key);
            if (b == null) continue;
            if (!data.isHopperBlock(b)) continue;
            if (!data.isEnabled(b)) continue;

            if (!(b.getState() instanceof Hopper hopperState)) continue;

            Inventory hopperInv = hopperState.getInventory();
            if (hopperInv == null) continue;

            // 1) Purge junk from hopper
            purgeHopper(b, hopperInv);

            // 2) Push 1 allowed item out
            pushOne(b, hopperInv);

            // 3) Pull 1 allowed item in
            pullOne(b, hopperInv);
        }
    }

    /* ===============================================================
       PURGE
       =============================================================== */

    private void purgeHopper(Block hopperBlock, Inventory hopperInv) {
        for (int slot = 0; slot < hopperInv.getSize(); slot++) {
            ItemStack item = hopperInv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            String matKey = item.getType().getKey().toString();
            if (data.allows(hopperBlock, matKey)) continue;

            ItemStack toReturn = item.clone();
            hopperInv.setItem(slot, null);

            boolean returned = tryReturnToAnyFeedingContainer(hopperBlock, toReturn);
            if (!returned) {
                // Delete by design
            }
        }
    }

    private boolean tryReturnToAnyFeedingContainer(Block hopperBlock, ItemStack stack) {
        for (Container c : getFeedingContainers(hopperBlock)) {
            Inventory inv = c.getInventory();
            if (inv == null) continue;
            if (inv.addItem(stack).isEmpty()) return true;
        }
        return false;
    }

    private List<Container> getFeedingContainers(Block hopperBlock) {
        List<Container> out = new ArrayList<>();
        addIfContainer(hopperBlock.getRelative(0, 1, 0), out);
        addIfContainer(hopperBlock.getRelative(1, 0, 0), out);
        addIfContainer(hopperBlock.getRelative(-1, 0, 0), out);
        addIfContainer(hopperBlock.getRelative(0, 0, 1), out);
        addIfContainer(hopperBlock.getRelative(0, 0, -1), out);
        return out;
    }

    private void addIfContainer(Block block, List<Container> out) {
        if (block == null) return;
        if (block.getState() instanceof Container c) out.add(c);
    }

    /* ===============================================================
       CUSTOM OUTFLOW
       =============================================================== */

    private void pushOne(Block hopperBlock, Inventory hopperInv) {
        Block destBlock = getHopperFacingTarget(hopperBlock);
        if (destBlock == null) return;

        if (!(destBlock.getState() instanceof Container destContainer)) return;
        Inventory destInv = destContainer.getInventory();
        if (destInv == null) return;

        for (int i = 0; i < hopperInv.getSize(); i++) {
            ItemStack it = hopperInv.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;

            String key = it.getType().getKey().toString();
            if (!data.allows(hopperBlock, key)) continue;

            if (destContainer instanceof Hopper destHopper) {
                Block destHopperBlock = destHopper.getBlock();
                if (data.isEnabled(destHopperBlock) && !data.allows(destHopperBlock, key)) {
                    continue;
                }
            }

            ItemStack one = it.clone();
            one.setAmount(1);

            if (!destInv.addItem(one).isEmpty()) return;

            decrementSlot(hopperInv, i, it);
            return;
        }
    }

    private Block getHopperFacingTarget(Block hopperBlock) {
        if (hopperBlock == null) return null;
        if (!(hopperBlock.getBlockData() instanceof Directional dir)) return null;
        return hopperBlock.getRelative(dir.getFacing());
    }

    /* ===============================================================
       CUSTOM INTAKE
       =============================================================== */

    private void pullOne(Block hopperBlock, Inventory hopperInv) {
        for (Container source : getFeedingContainers(hopperBlock)) {
            Inventory srcInv = source.getInventory();
            if (srcInv == null) continue;

            for (int slot = 0; slot < srcInv.getSize(); slot++) {
                ItemStack it = srcInv.getItem(slot);
                if (it == null || it.getType() == Material.AIR) continue;

                String key = it.getType().getKey().toString();
                if (!data.allows(hopperBlock, key)) continue;

                ItemStack one = it.clone();
                one.setAmount(1);

                if (!hopperInv.addItem(one).isEmpty()) {
                    return;
                }

                decrementSlot(srcInv, slot, it);
                return;
            }
        }
    }

    private void decrementSlot(Inventory inv, int slot, ItemStack current) {
        int amt = current.getAmount();
        if (amt <= 1) inv.setItem(slot, null);
        else {
            current.setAmount(amt - 1);
            inv.setItem(slot, current);
        }
    }
}
