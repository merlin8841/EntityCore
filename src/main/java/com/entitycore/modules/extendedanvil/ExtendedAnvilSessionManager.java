package com.entitycore.modules.extendedanvil;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilSessionManager {

    private final JavaPlugin plugin;
    private final EnchantCostService costService;
    private final XpRefundService refundService;
    private final ExtendedAnvilConfig config;

    private final Set<UUID> active = new HashSet<>();

    public ExtendedAnvilSessionManager(JavaPlugin plugin,
                                       EnchantCostService costService,
                                       XpRefundService refundService,
                                       ExtendedAnvilConfig config) {
        this.plugin = plugin;
        this.costService = costService;
        this.refundService = refundService;
        this.config = config;
    }

    /* ============================================================= */

    public void open(Player player) {
        active.add(player.getUniqueId());
        PlayerMenu.open(player);
    }

    public void handleClose(Player player) {
        active.remove(player.getUniqueId());
    }

    private boolean isActive(Player player) {
        return active.contains(player.getUniqueId());
    }

    /* ============================================================= */

    public void handlePrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!isActive(player)) return;
        if (!(event.getInventory() instanceof AnvilInventory anvil)) return;

        // CRITICAL: Kill vanilla 39-level cap EVERY TIME
        anvil.setMaximumRepairCost(999999);
        anvil.setRepairCost(0);

        ItemStack left = anvil.getItem(0);
        ItemStack right = anvil.getItem(1);

        if (left == null || right == null) {
            event.setResult(null);
            return;
        }

        // DISENCHANT MODE (plain book)
        if (right.getType() == org.bukkit.Material.BOOK) {
            Map<Enchantment, Integer> enchants = left.getEnchantments();
            if (enchants.isEmpty()) {
                event.setResult(null);
                return;
            }

            boolean removeAll = right.getAmount() == 1;
            LinkedHashMap<Enchantment, Integer> removed = new LinkedHashMap<>();

            if (removeAll) {
                removed.putAll(enchants);
            } else {
                Enchantment next = config.chooseNextDisenchant(enchants.keySet());
                if (next != null) removed.put(next, enchants.get(next));
            }

            ItemStack book = PlayerMenuHelper.buildEnchantedBook(removed);
            event.setResult(book);
            anvil.setRepairCost(1);
            return;
        }

        // APPLY MODE (enchanted book)
        if (right.getType() == org.bukkit.Material.ENCHANTED_BOOK) {
            EnchantCostService.ApplyPreview preview =
                    costService.previewApply(player, left, right);

            if (!preview.canApply) {
                event.setResult(null);
                return;
            }

            event.setResult(preview.result);
            anvil.setRepairCost(preview.levelCost);
        }
    }

    /* ============================================================= */

    public void handleClick(Player player, InventoryClickEvent event) {
        if (!isActive(player)) return;
        if (!(event.getInventory() instanceof AnvilInventory anvil)) return;

        if (event.getRawSlot() != 2) return; // result slot only
        ItemStack result = event.getCurrentItem();
        if (result == null) return;

        int cost = anvil.getRepairCost();
        if (player.getLevel() < cost) {
            event.setCancelled(true);
            return;
        }

        player.setLevel(player.getLevel() - cost);
        event.setCancelled(false);
    }

    public void handleDrag(Player player, InventoryDragEvent event) {
        if (isActive(player)) {
            event.setCancelled(true);
        }
    }
}
