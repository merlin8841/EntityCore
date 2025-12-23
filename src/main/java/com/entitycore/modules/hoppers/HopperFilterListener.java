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
            ItemStack offered = !cursorEmpty ? cursor : (!handEmpty ? hand
