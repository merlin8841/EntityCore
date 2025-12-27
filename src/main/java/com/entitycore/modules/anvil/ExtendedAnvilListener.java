package com.entitycore.modules.anvil;

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

    public ExtendedAnvilListener(JavaPlugin plugin, XpRefundService refundService) {
        this.plugin = plugin;
        this.refundService = refundService;
    }

    /* =========================================================
       PREVIEW
       ========================================================= */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) event.getInventory();

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) {
            event.setResult(null);
            safeSetRepairCost(inv, 0);
            return;
        }
        if (right == null || right.getType() == Material.AIR) {
            // let vanilla handle rename/repair with only left item
            return;
        }

        // Block book+book entirely (no combining/upgrading enchanted books)
        if (isEnchantedBook(left) && isEnchantedBook(right)) {
            event.setResult(null);
            safeSetRepairCost(inv, 0);
            return;
        }

        // DISENCHANT PREVIEW: item + BOOK(s) -> enchanted book
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

            event.setResult(buildEnchantedBook(extracted));

            // Bedrock needs a non-zero cost to make result “takeable”
            safeSetRepairCost(inv, 1);
            return;
        }

        // APPLY PREVIEW: item + ENCHANTED_BOOK -> merged item
        if (isEnchantedBook(right) && !isBook(left)) {
            MergeOutcome out = manualMerge(left, right, inv);
            if (out == null || out.result == null || out.result.getType() == Material.AIR) {
                event.setResult(null);
                safeSetRepairCost(inv, 0);
                return;
            }

            event.setResult(out.result);

            // Keep preview <= 39 so Bedrock never hard-blocks.
            // We still charge the TRUE cost on click.
            int preview = Math.min(39, Math.max(1, out.trueCost));
            safeSetRepairCost(inv, preview);
        }
    }

    /* =========================================================
       RESULT CLICK (Bedrock-safe)
       ========================================================= */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!(event.getView().getTopInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) event.getView().getTopInventory();

        // This is the key Bedrock fix: result slot is ALWAYS raw slot 2 in the anvil top inventory.
        if (event.getRawSlot() != 2) return;

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        ItemStack result = inv.getItem(2);

        if (result == null || result.getType() == Material.AIR) return;
        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() == Material.AIR) return;

        // Always cancel vanilla result taking for our two custom modes.
        // (Prevents Bedrock “Too Expensive” lock and ensures consistent behavior.)
        if (right.getType() == Material.BOOK && !isBook(left)) {
            event.setCancelled(true);
            handleDisenchantTake(player, inv, event.getAction());
            return;
        }

        if (right.getType() == Material.ENCHANTED_BOOK && !isBook(left)) {
            event.setCancelled(true);
            handleApplyTake(player, inv, event.getAction());
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

        // consume exactly 1 plain book
        right.setAmount(right.getAmount() - 1);
        if (right.getAmount() <= 0) inv.setItem(1, null);
        else inv.setItem(1, right);

        // remove extracted enchants from the item
        ItemStack newLeft = left.clone();
        ItemMeta meta = newLeft.getItemMeta();
        if (meta == null) return;

        for (Enchantment ench : extracted.keySet()) {
            meta.removeEnchant(ench);
        }
        newLeft.setItemMeta(meta);
        inv.setItem(0, newLeft);

        // clear result
        inv.setItem(2, null);

        // refund XP levels (diminishing handled in service)
        int refundedLevels = refundService.refundForRemoval(player, newLeft, extracted);
        if (refundedLevels > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        }

        player.updateInventory();
    }

    /* =========================================================
       APPLY TAKE (bypass Too Expensive + charge true cost)
       ========================================================= */

    private void handleApplyTake(Player player, AnvilInventory inv, InventoryAction action) {
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() != Material.ENCHANTED_BOOK) return;

        MergeOutcome out = manualMerge(left, right, inv);
        if (out == null || out.result == null || out.result.getType() == Material.AIR) return;

        int trueCost = Math.max(0, out.trueCost);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        // This is the only “gate” for normal players: having enough levels.
        if (!creative && player.getLevel() < trueCost) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!giveResultToPlayer(player, action, out.result)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // consume 1 enchanted book
        if (right.getAmount() <= 1) inv.setItem(1, null);
        else {
            right.setAmount(right.getAmount() - 1);
            inv.setItem(1, right);
        }

        // consume left
        inv.setItem(0, null);

        // clear result
        inv.setItem(2, null);

        // charge levels (supports 500+)
        if (!creative && trueCost > 0) {
            player.giveExpLevels(-trueCost);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
        player.updateInventory();
    }

    /* =========================================================
       Merge / caps / cost
       ========================================================= */

    private MergeOutcome manualMerge(ItemStack left, ItemStack right, AnvilInventory inv) {
        Map<Enchantment, Integer> stored = getStoredEnchants(right);
        if (stored.isEmpty()) return null;

        ItemStack merged = left.clone();
        ItemMeta meta = merged.getItemMeta();
        if (meta == null) return null;

        Map<Enchantment, Integer> existing = new HashMap<Enchantment, Integer>(merged.getEnchantments());

        int applied = 0;
        int cost = 0;

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

            int capped = applyCap(ench, level);
            if (capped <= 0) continue;

            int oldLevel = existing.containsKey(ench) ? existing.get(ench) : 0;

            // Only apply if it increases level
            if (capped > oldLevel) {
                meta.addEnchant(ench, capped, false);
                existing.put(ench, capped);
                applied++;

                cost += computeApplyCost(ench, capped, oldLevel);
            }
        }

        // Rename support (Paper); harmless if empty
        String rename = safeGetRenameText(inv);
        if (rename != null && !rename.trim().isEmpty()) {
            meta.setDisplayName(rename);
            cost += 1;
        }

        if (applied == 0 && (rename == null || rename.trim().isEmpty())) return null;

        merged.setItemMeta(meta);

        // Never allow 0 cost when something changed
        return new MergeOutcome(merged, Math.max(1, cost));
    }

    private int computeApplyCost(Enchantment ench, int newLevel, int oldLevel) {
        int delta = Math.max(1, newLevel - oldLevel);

        int weight;
        if (ench.isCursed()) weight = 1;
        else if (ench.isTreasure()) weight = 8;
        else weight = 4;

        return weight * newLevel * delta;
    }

    private int applyCap(Enchantment ench, int level) {
        String key = ench.getKey().toString();
        String path = "extendedanvil.caps." + key;

        int cap;
        if (plugin.getConfig().contains(path)) cap = plugin.getConfig().getInt(path);
        else cap = ench.getMaxLevel();

        if (cap < 0) cap = ench.getMaxLevel();
        if (cap == 0) return 0;

        return Math.min(level, cap);
    }

    private void safeSetRepairCost(AnvilInventory inv, int cost) {
        try {
            inv.setRepairCost(cost);
        } catch (Throwable ignored) {
        }
    }

    private String safeGetRenameText(AnvilInventory inv) {
        try {
            return inv.getRenameText();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /* =========================================================
       Give result to player (Bedrock-safe)
       ========================================================= */

    private boolean giveResultToPlayer(Player player, InventoryAction action, ItemStack result) {
        // If cursor is empty and this is a normal pickup-type action, prefer cursor.
        ItemStack cursor = player.getItemOnCursor();
        boolean cursorEmpty = (cursor == null || cursor.getType() == Material.AIR);

        boolean pickupish =
                action == InventoryAction.PICKUP_ALL ||
                action == InventoryAction.PICKUP_ONE ||
                action == InventoryAction.PICKUP_HALF ||
                action == InventoryAction.PICKUP_SOME ||
                action == InventoryAction.SWAP_WITH_CURSOR ||
                action == InventoryAction.COLLECT_TO_CURSOR;

        if (pickupish && cursorEmpty) {
            player.setItemOnCursor(result);
            return true;
        }

        // Otherwise push into inventory (covers Bedrock tap / quick-move)
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(result);
        return leftovers == null || leftovers.isEmpty();
    }

    /* =========================================================
       Disenchant ordering + book utils
       ========================================================= */

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

    private Map<Enchantment, Integer> getStoredEnchants(ItemStack book) {
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) return Collections.emptyMap();
        return new HashMap<Enchantment, Integer>(((EnchantmentStorageMeta) meta).getStoredEnchants());
    }

    private boolean isBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.BOOK || it.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isEnchantedBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.ENCHANTED_BOOK;
    }

    private static final class MergeOutcome {
        final ItemStack result;
        final int trueCost;

        MergeOutcome(ItemStack result, int trueCost) {
            this.result = result;
            this.trueCost = trueCost;
        }
    }
}
