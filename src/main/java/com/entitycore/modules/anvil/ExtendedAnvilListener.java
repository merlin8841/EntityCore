package com.entitycore.modules.anvil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilListener implements Listener {

    private final JavaPlugin plugin;
    private final XpRefundService refundService;

    // Store TRUE vanilla repair cost for apply operations (per-inventory instance)
    private final Map<Integer, Integer> trueApplyCostByInv = new HashMap<Integer, Integer>();

    public ExtendedAnvilListener(JavaPlugin plugin, XpRefundService refundService) {
        this.plugin = plugin;
        this.refundService = refundService;
    }

    /* =========================================================
       PREVIEW (DISENCHANT override) + PREVIEW (APPLY vanilla cost capture)
       ========================================================= */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        final AnvilInventory inv = (AnvilInventory) event.getInventory();
        final int invKey = System.identityHashCode(inv);

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR || right == null || right.getType() == Material.AIR) {
            // clear stored true cost if inputs not present
            trueApplyCostByInv.remove(invKey);
            return;
        }

        // Block enchanted book + enchanted book entirely
        if (isEnchantedBook(left) && isEnchantedBook(right)) {
            event.setResult(null);
            safeSetRepairCost(inv, 0);
            trueApplyCostByInv.remove(invKey);
            return;
        }

        // ---------------------------------------------------------
        // DISENCHANT PREVIEW: item + BOOK(s) -> enchanted book
        // ---------------------------------------------------------
        if (right.getType() == Material.BOOK && !isBook(left)) {
            Map<Enchantment, Integer> current = new HashMap<Enchantment, Integer>(left.getEnchantments());
            if (current.isEmpty()) {
                event.setResult(null);
                safeSetRepairCost(inv, 0);
                return;
            }

            int books = Math.max(1, right.getAmount());
            LinkedHashMap<Enchantment, Integer> extracted = (books == 1)
                    ? extractAll(current)
                    : extractOne(current);

            if (extracted.isEmpty()) {
                event.setResult(null);
                safeSetRepairCost(inv, 0);
                return;
            }

            final ItemStack outBook = buildEnchantedBook(extracted);

            // Set result in event
            event.setResult(outBook);

            // Also force into result slot for Bedrock/Geyser
            trySetResultSlot(inv, outBook);

            // And set again next tick (Geyser sometimes needs a follow-up)
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override public void run() {
                    trySetResultSlot(inv, outBook);
                }
            });

            // Keep display cost low so Bedrock allows taking it
            safeSetRepairCost(inv, 1);
            return;
        }

        // ---------------------------------------------------------
        // APPLY PREVIEW: item + ENCHANTED_BOOK -> vanilla handles preview
        // We capture vanilla cost on MONITOR (below) and clamp display to 39 if needed.
        // ---------------------------------------------------------
        if (right.getType() == Material.ENCHANTED_BOOK && !isBook(left)) {
            // Do nothing here; allow vanilla to compute. Cost capture happens in MONITOR handler.
            return;
        }

        // Otherwise: not our custom operation
        trueApplyCostByInv.remove(invKey);
    }

    /**
     * After vanilla prepares the result, capture the TRUE repair cost and then clamp DISPLAY cost to 39
     * to avoid Bedrock "Too Expensive", while still charging the true cost on click.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareCaptureVanillaCost(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) event.getInventory();
        int invKey = System.identityHashCode(inv);

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR || right == null || right.getType() == Material.AIR) {
            trueApplyCostByInv.remove(invKey);
            return;
        }

        // Only track apply-from-book
        if (right.getType() != Material.ENCHANTED_BOOK || isBook(left)) {
            trueApplyCostByInv.remove(invKey);
            return;
        }

        int vanillaCost = safeGetRepairCost(inv);
        if (vanillaCost < 0) vanillaCost = 0;

        trueApplyCostByInv.put(invKey, vanillaCost);

        // If vanilla cost > 39, Bedrock will show "Too Expensive" and block.
        // Clamp DISPLAY cost to 39 to keep UI usable. We still charge vanillaCost on take.
        if (vanillaCost > 39) {
            safeSetRepairCost(inv, 39);
        }
    }

    /* =========================================================
       CLICK HANDLING (Bedrock-safe)
       ========================================================= */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!(event.getView().getTopInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) event.getView().getTopInventory();
        int invKey = System.identityHashCode(inv);

        // Result slot in anvil top inventory is raw slot 2 (consistent on Java + Bedrock)
        if (event.getRawSlot() != 2) return;

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() == Material.AIR) return;

        // DISENCHANT: item + BOOK
        if (right.getType() == Material.BOOK && !isBook(left)) {
            event.setCancelled(true);
            handleDisenchantTake(player, inv, event.getAction());
            return;
        }

        // APPLY: item + ENCHANTED_BOOK
        if (right.getType() == Material.ENCHANTED_BOOK && !isBook(left)) {
            event.setCancelled(true);
            handleApplyTake(player, inv, event.getAction(), invKey);
        }
    }

    /* =========================================================
       DISENCHANT TAKE
       ========================================================= */

    private void handleDisenchantTake(Player player, AnvilInventory inv, InventoryAction action) {
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() != Material.BOOK) return;

        Map<Enchantment, Integer> current = new HashMap<Enchantment, Integer>(left.getEnchantments());
        if (current.isEmpty()) return;

        int books = Math.max(1, right.getAmount());
        LinkedHashMap<Enchantment, Integer> extracted = (books == 1)
                ? extractAll(current)
                : extractOne(current);

        if (extracted.isEmpty()) return;

        ItemStack outBook = buildEnchantedBook(extracted);

        if (!giveResultToPlayer(player, action, outBook)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // consume 1 plain book
        int newAmt = right.getAmount() - 1;
        if (newAmt <= 0) inv.setItem(1, null);
        else {
            right.setAmount(newAmt);
            inv.setItem(1, right);
        }

        // remove extracted enchants from item
        ItemStack newLeft = left.clone();
        ItemMeta meta = newLeft.getItemMeta();
        if (meta == null) return;

        for (Enchantment ench : extracted.keySet()) {
            meta.removeEnchant(ench);
        }
        newLeft.setItemMeta(meta);
        inv.setItem(0, newLeft);

        // clear result slot
        inv.setItem(2, null);

        // refund XP (diminishing handled in service)
        int refunded = refundService.refundForRemoval(player, newLeft, extracted);
        if (refunded > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        }

        player.updateInventory();
    }

    /* =========================================================
       APPLY TAKE (charge TRUE vanilla cost, hide Too Expensive on Bedrock)
       ========================================================= */

    private void handleApplyTake(Player player, AnvilInventory inv, InventoryAction action, int invKey) {
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        ItemStack result = inv.getItem(2);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() != Material.ENCHANTED_BOOK) return;

        // If vanilla didn't produce a result (rare edge), build our own merge result
        if (result == null || result.getType() == Material.AIR) {
            result = manualMergeResult(left, right, inv);
            if (result == null || result.getType() == Material.AIR) return;
        }

        int trueCost = 0;
        if (trueApplyCostByInv.containsKey(invKey)) {
            trueCost = trueApplyCostByInv.get(invKey);
        } else {
            // Fallback: use whatever is currently in repairCost (may be clamped to 39)
            trueCost = Math.max(0, safeGetRepairCost(inv));
        }

        boolean creative = (player.getGameMode() == GameMode.CREATIVE);
        if (!creative && player.getLevel() < trueCost) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!giveResultToPlayer(player, action, result.clone())) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // consume 1 enchanted book
        int bookAmt = right.getAmount() - 1;
        if (bookAmt <= 0) inv.setItem(1, null);
        else {
            right.setAmount(bookAmt);
            inv.setItem(1, right);
        }

        // consume left item
        inv.setItem(0, null);

        // clear result
        inv.setItem(2, null);

        // charge true cost (supports 500+)
        if (!creative && trueCost > 0) {
            player.giveExpLevels(-trueCost);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
        player.updateInventory();
    }

    /* =========================================================
       Helpers
       ========================================================= */

    private boolean giveResultToPlayer(Player player, InventoryAction action, ItemStack result) {
        // Bedrock "tap" often behaves like normal pickup, but cursor operations can be inconsistent.
        // Strategy:
        // - If action is MOVE_TO_OTHER_INVENTORY (shift), add to inventory.
        // - Otherwise: if cursor empty, put on cursor; else add to inventory.

        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(result);
            return leftovers == null || leftovers.isEmpty();
        }

        ItemStack cursor = player.getItemOnCursor();
        boolean cursorEmpty = (cursor == null || cursor.getType() == Material.AIR);

        if (cursorEmpty) {
            player.setItemOnCursor(result);
            return true;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(result);
        return leftovers == null || leftovers.isEmpty();
    }

    private void trySetResultSlot(AnvilInventory inv, ItemStack result) {
        try {
            inv.setItem(2, result);
        } catch (Throwable ignored) {
        }
    }

    private int safeGetRepairCost(AnvilInventory inv) {
        try {
            return inv.getRepairCost();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void safeSetRepairCost(AnvilInventory inv, int cost) {
        try {
            inv.setRepairCost(cost);
        } catch (Throwable ignored) {
        }
    }

    private boolean isBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.BOOK || it.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isEnchantedBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.ENCHANTED_BOOK;
    }

    private Map<Enchantment, Integer> getStoredEnchants(ItemStack book) {
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) return Collections.emptyMap();
        return new HashMap<Enchantment, Integer>(((EnchantmentStorageMeta) meta).getStoredEnchants());
    }

    private ItemStack buildEnchantedBook(LinkedHashMap<Enchantment, Integer> enchants) {
        ItemStack out = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = out.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta esm = (EnchantmentStorageMeta) meta;
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                esm.addStoredEnchant(e.getKey(), e.getValue(), false);
            }
            out.setItemMeta(esm);
        }
        return out;
    }

    // Manual merge result ONLY for edge cases where vanilla doesn't populate output.
    // We still charge TRUE vanilla cost from inv.getRepairCost() capture.
    private ItemStack manualMergeResult(ItemStack left, ItemStack right, AnvilInventory inv) {
        Map<Enchantment, Integer> stored = getStoredEnchants(right);
        if (stored.isEmpty()) return null;

        ItemStack merged = left.clone();
        ItemMeta meta = merged.getItemMeta();
        if (meta == null) return null;

        Map<Enchantment, Integer> existing = new HashMap<Enchantment, Integer>(merged.getEnchantments());

        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            Enchantment ench = entry.getKey();
            int level = entry.getValue();
            if (ench == null) continue;

            if (!ench.canEnchantItem(merged)) continue;

            // vanilla conflict rules
            boolean conflict = false;
            for (Enchantment ex : existing.keySet()) {
                if (ex.equals(ench)) continue;
                if (ex.conflictsWith(ench)) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;

            int oldLevel = existing.containsKey(ench) ? existing.get(ench) : 0;

            // apply if higher (replace lower)
            if (level > oldLevel) {
                meta.addEnchant(ench, level, false);
                existing.put(ench, level);
                changed = true;
            }
        }

        if (!changed) return null;

        merged.setItemMeta(meta);
        return merged;
    }

    private LinkedHashMap<Enchantment, Integer> extractAll(Map<Enchantment, Integer> current) {
        List<Enchantment> order = new ArrayList<Enchantment>(current.keySet());
        Collections.sort(order, new Comparator<Enchantment>() {
            @Override
            public int compare(Enchantment a, Enchantment b) {
                return a.getKey().toString().compareTo(b.getKey().toString());
            }
        });

        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<Enchantment, Integer>();
        for (Enchantment e : order) out.put(e, current.get(e));
        return out;
    }

    private LinkedHashMap<Enchantment, Integer> extractOne(Map<Enchantment, Integer> current) {
        Enchantment chosen = DisenchantPriority.chooseOne(plugin, current.keySet());
        if (chosen == null) return new LinkedHashMap<Enchantment, Integer>();

        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<Enchantment, Integer>();
        out.put(chosen, current.get(chosen));
        return out;
    }
}
