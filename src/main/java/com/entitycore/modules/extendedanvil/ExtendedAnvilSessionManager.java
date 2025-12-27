package com.entitycore.modules.extendedanvil;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilSessionManager {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;
    private final XpRefundService refundService;
    private final EnchantCostService costService;

    private final Set<UUID> active = new HashSet<>();

    public ExtendedAnvilSessionManager(JavaPlugin plugin,
                                       ExtendedAnvilConfig config,
                                       XpRefundService refundService,
                                       EnchantCostService costService) {
        this.plugin = plugin;
        this.config = config;
        this.refundService = refundService;
        this.costService = costService;
    }

    /* ========================================================= */
    /* SESSION LIFECYCLE                                         */
    /* ========================================================= */

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

    /* ========================================================= */
    /* PREPARE ANVIL                                             */
    /* ========================================================= */

    public void handlePrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!isActive(player)) return;
        if (!(event.getInventory() instanceof AnvilInventory)) return;

        AnvilInventory anvil = (AnvilInventory) event.getInventory();

        // CRITICAL: remove vanilla 39-level cap
        anvil.setMaximumRepairCost(999999);
        anvil.setRepairCost(0);

        ItemStack item = anvil.getItem(0);
        ItemStack right = anvil.getItem(1);

        if (item == null || right == null) {
            event.setResult(null);
            return;
        }

        /* ---------------- DISENCHANT ---------------- */

        if (right.getType() == Material.BOOK) {
            Map<Enchantment, Integer> enchants = item.getEnchantments();
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

            event.setResult(buildEnchantedBook(removed));
            anvil.setRepairCost(1);
            return;
        }

        /* ---------------- APPLY ---------------- */

        if (right.getType() == Material.ENCHANTED_BOOK) {
            EnchantCostService.ApplyPreview preview =
                    costService.previewApply(player, item, right);

            if (!preview.canApply || preview.result == null) {
                event.setResult(null);
                return;
            }

            event.setResult(preview.result);
            anvil.setRepairCost(preview.levelCost);
        }
    }

    /* ========================================================= */
    /* CLICK / DRAG                                              */
    /* ========================================================= */

    public void handleClick(Player player, InventoryClickEvent event) {
        if (!isActive(player)) return;
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        if (event.getRawSlot() != 2) return;

        AnvilInventory anvil = (AnvilInventory) event.getInventory();
        int cost = anvil.getRepairCost();

        if (player.getLevel() < cost) {
            event.setCancelled(true);
            return;
        }

        player.setLevel(player.getLevel() - cost);
    }

    public void handleDrag(Player player, InventoryDragEvent event) {
        if (isActive(player)) {
            event.setCancelled(true);
        }
    }

    /* ========================================================= */

    private ItemStack buildEnchantedBook(Map<Enchantment, Integer> enchants) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            meta.addStoredEnchant(e.getKey(), e.getValue(), false);
        }
        book.setItemMeta(meta);
        return book;
    }
}
