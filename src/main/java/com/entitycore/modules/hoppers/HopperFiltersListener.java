package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class HopperFilterListener implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey filterKey;

    public HopperFilterListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.filterKey = new NamespacedKey(plugin, "hopper_filter");
    }

    /* ===============================================================
       PLAYER INTERACTION — SET / CLEAR FILTER
       =============================================================== */

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHopperInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock() instanceof Block block)) return;
        if (block.getType() != Material.HOPPER) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!(block.getState() instanceof TileState tile)) return;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();

        event.setCancelled(true);

        // Sneak + empty hand = clear filter
        if (player.isSneaking() && hand.getType() == Material.AIR) {
            pdc.remove(filterKey);
            tile.update();

            player.sendMessage("§cHopper filter cleared.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            return;
        }

        // Require a real item
        if (hand.getType() == Material.AIR) {
            player.sendMessage("§eHold an item to set as the hopper filter.");
            return;
        }

        // Store material key as filter
        pdc.set(filterKey, PersistentDataType.STRING, hand.getType().getKey().toString());
        tile.update();

        player.sendMessage("§aHopper filter set to §f" + hand.getType().name());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
    }

    /* ===============================================================
       ITEM TRANSFER LOGIC
       =============================================================== */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        if (!(event.getDestination().getHolder() instanceof Hopper hopper)) return;

        Block block = hopper.getBlock();
        if (!(block.getState() instanceof TileState tile)) return;

        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        if (!pdc.has(filterKey, PersistentDataType.STRING)) return;

        String filter = pdc.get(filterKey, PersistentDataType.STRING);
        if (filter == null) return;

        ItemStack item = event.getItem();
        if (!Objects.equals(item.getType().getKey().toString(), filter)) {
            event.setCancelled(true);
        }
    }
}
