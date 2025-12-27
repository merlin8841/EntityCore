package com.entitycore.modules.extendedanvil;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class ExtendedAnvilService {

    @SuppressWarnings("unused")
    private final Plugin plugin;
    private final ExtendedAnvilConfig config;

    public ExtendedAnvilService(Plugin plugin, ExtendedAnvilConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Entry point for "apply" action when an enchanted book is present in the book slots.
     * Supports:
     *  - book -> item (normal apply)
     *  - book -> enchanted book (combine without level upgrades)
     */
    public ApplyResult applyFromBook(Player player, ItemStack targetItem, ItemStack enchantedBook) {
        if (targetItem == null || targetItem.getType().isAir()) {
            return ApplyResult.fail(ChatColor.RED + "Put a target item in the item slot.");
        }
        if (enchantedBook == null || enchantedBook.getType() != Material.ENCHANTED_BOOK) {
            return ApplyResult.fail(ChatColor.RED + "Put an enchanted book in the book slots.");
        }
        if (!(enchantedBook.getItemMeta() instanceof EnchantmentStorageMeta esm)) {
            return ApplyResult.fail(ChatColor.RED + "Invalid enchanted book.");
        }
        if (esm.getStoredEnchants().isEmpty()) {
            return ApplyResult.fail(ChatColor.RED + "That book has no stored enchants.");
        }

        // If target is a book, we allow combine (for multi-enchant books),
        // but we DO NOT allow any level upgrades (no combining to gain higher levels).
        if (targetItem.getType() == Material.ENCHANTED_BOOK) {
            return combineBooks(player, targetItem, enchantedBook);
        }

        // Prevent applying onto plain books
        if (targetItem.getType() == Material.BOOK) {
            return ApplyResult.fail(ChatColor.RED + "Use an enchanted book as the target to combine books.");
        }

        return applyBookToItem(player, targetItem, enchantedBook);
    }

    /**
     * Applies enchants from an enchanted book onto a non-book item.
     * - respects vanilla conflicts
     * - upgrades lower levels only
     * - COST SCALES IN LEVELS based on what would actually apply
     */
    private ApplyResult applyBookToItem(Player player, ItemStack item, ItemStack enchantedBook) {
        if (!(enchantedBook.getItemMeta() instanceof EnchantmentStorageMeta esm)) {
            return ApplyResult.fail(ChatColor.RED + "Invalid enchanted book.");
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return ApplyResult.fail(ChatColor.RED + "That item can't be modified.");

        Map<Enchantment, Integer> stored = esm.getStoredEnchants();

        // Determine which enchants would actually apply (must be higher than existing)
        List<Map.Entry<Enchantment, Integer>> applicable = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            Enchantment ench = entry.getKey();
            Integer lvlObj = entry.getValue();
            int newLevel = (lvlObj == null) ? 0 : lvlObj;
            if (ench == null || newLevel <= 0) continue;

            if (!ench.canEnchantItem(item)) {
                return ApplyResult.fail(ChatColor.RED + "Can't apply "
                        + ExtendedAnvilUtil.prettyEnchantName(ExtendedAnvilUtil.enchantKey(ench)) + " to that item.");
            }

            int cur = itemMeta.getEnchantLevel(ench);
            if (newLevel > cur) {
                applicable.add(entry);
            }
        }

        if (applicable.isEmpty()) {
            return ApplyResult.fail(ChatColor.RED + "Nothing to apply (item already has equal/higher levels).");
        }

        // Validate conflicts against existing item enchants
        for (Map.Entry<Enchantment, Integer> entry : applicable) {
            Enchantment ench = entry.getKey();
            for (Enchantment existing : itemMeta.getEnchants().keySet()) {
                if (existing == null) continue;
                if (existing.conflictsWith(ench) || ench.conflictsWith(existing)) {
                    return ApplyResult.fail(ChatColor.RED + "That book conflicts with existing enchants.");
                }
            }
        }

        int costLevels = computeCostLevels(applicable.size(), sumLevels(applicable));
        if (costLevels > 0 && player.getLevel() < costLevels) {
            return ApplyResult.fail(ChatColor.RED + "You need " + costLevels + " levels to apply that book.");
        }

        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : applicable) {
            Enchantment ench = entry.getKey();
            int newLevel = entry.getValue() == null ? 0 : entry.getValue();
            if (ench == null || newLevel <= 0) continue;
            itemMeta.addEnchant(ench, newLevel, false);
            changed = true;
        }

        if (!changed) {
            return ApplyResult.fail(ChatColor.RED + "Nothing to apply.");
        }

        item.setItemMeta(itemMeta);

        if (costLevels > 0) {
            player.giveExpLevels(-costLevels);
        }

        return ApplyResult.ok(ChatColor.GREEN + "Enchants applied. Cost: " + costLevels + " level(s).", costLevels);
    }

    /**
     * Combine enchanted book onto enchanted book:
     * - merges enchants to make multi-enchant books easier
     * - DOES NOT upgrade levels: result level = max(levelA, levelB)
     * - rejects conflicts (keeps sane books)
     * - cost scales with the resulting book (not flat)
     */
    private ApplyResult combineBooks(Player player, ItemStack targetBook, ItemStack addBook) {
        if (!(targetBook.getItemMeta() instanceof EnchantmentStorageMeta tMeta)) {
            return ApplyResult.fail(ChatColor.RED + "Invalid target enchanted book.");
        }
        if (!(addBook.getItemMeta() instanceof EnchantmentStorageMeta aMeta)) {
            return ApplyResult.fail(ChatColor.RED + "Invalid enchanted book.");
        }

        Map<Enchantment, Integer> t = new HashMap<>(tMeta.getStoredEnchants());
        Map<Enchantment, Integer> a = new HashMap<>(aMeta.getStoredEnchants());

        if (a.isEmpty()) return ApplyResult.fail(ChatColor.RED + "That book has no stored enchants.");
        if (t.isEmpty()) return ApplyResult.fail(ChatColor.RED + "Target book has no stored enchants.");

        // Build merged map where levels never increase beyond max of inputs
        Map<Enchantment, Integer> merged = new HashMap<>(t);
        for (Map.Entry<Enchantment, Integer> entry : a.entrySet()) {
            Enchantment ench = entry.getKey();
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (ench == null || lvl <= 0) continue;

            int cur = merged.getOrDefault(ench, 0);
            merged.put(ench, Math.max(cur, lvl));
        }

        // Reject conflicts inside merged set (to avoid insane books)
        List<Enchantment> keys = new ArrayList<>(merged.keySet());
        for (int i = 0; i < keys.size(); i++) {
            for (int j = i + 1; j < keys.size(); j++) {
                Enchantment e1 = keys.get(i);
                Enchantment e2 = keys.get(j);
                if (e1 == null || e2 == null) continue;
                if (e1.conflictsWith(e2) || e2.conflictsWith(e1)) {
                    return ApplyResult.fail(ChatColor.RED + "Those books contain conflicting enchants.");
                }
            }
        }

        // If nothing changed, don't charge
        boolean changed = !sameStoredEnchants(tMeta.getStoredEnchants(), merged);
        if (!changed) {
            return ApplyResult.fail(ChatColor.RED + "Nothing new to combine (no higher levels or new enchants).");
        }

        int costLevels = computeCostLevels(merged.size(), sumLevels(merged));
        if (costLevels > 0 && player.getLevel() < costLevels) {
            return ApplyResult.fail(ChatColor.RED + "You need " + costLevels + " levels to combine those books.");
        }

        // Write merged enchants back (clear then set)
        EnchantmentStorageMeta out = (EnchantmentStorageMeta) targetBook.getItemMeta();
        if (out == null) return ApplyResult.fail(ChatColor.RED + "Failed to edit target book.");

        // Remove existing stored enchants
        for (Enchantment e : new HashSet<>(out.getStoredEnchants().keySet())) {
            out.removeStoredEnchant(e);
        }

        // Add merged enchants
        for (Map.Entry<Enchantment, Integer> entry : merged.entrySet()) {
            out.addStoredEnchant(entry.getKey(), entry.getValue(), false);
        }

        targetBook.setItemMeta(out);

        if (costLevels > 0) {
            player.giveExpLevels(-costLevels);
        }

        return ApplyResult.ok(ChatColor.GREEN + "Books combined. Cost: " + costLevels + " level(s).", costLevels);
    }

    /**
     * Disenchants an item into one enchanted book.
     * Refund is returned in LEVELS (giveExpLevels), with diminishing returns tracked per-enchant via PDC.
     */
    public DisenchantResult disenchant(Player player, ItemStack item, boolean removeAll) {
        if (item == null || item.getType().isAir()) {
            return DisenchantResult.fail(ChatColor.RED + "Put an item in the item slot.");
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return DisenchantResult.fail(ChatColor.RED + "That item can't be modified.");
        }

        Map<Enchantment, Integer> current = new HashMap<>(meta.getEnchants());
        if (current.isEmpty()) {
            return DisenchantResult.fail(ChatColor.RED + "That item has no enchants.");
        }

        List<Enchantment> ordered = orderByPriority(current.keySet());
        List<Enchantment> toRemove = new ArrayList<>();

        if (removeAll) {
            for (Enchantment e : ordered) {
                if (isEligibleForRemoval(e)) toRemove.add(e);
            }
        } else {
            for (Enchantment e : ordered) {
                if (isEligibleForRemoval(e)) {
                    toRemove.add(e);
                    break;
                }
            }
        }

        if (toRemove.isEmpty()) {
            return DisenchantResult.fail(ChatColor.RED + (config.isAllowCurseRemoval()
                    ? "No removable enchants found."
                    : "Only curses found (curse removal is disabled)."));
        }

        ItemStack outBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bm = (EnchantmentStorageMeta) outBook.getItemMeta();
        if (bm == null) return DisenchantResult.fail(ChatColor.RED + "Failed to create output book.");

        int refundLevels = 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        for (Enchantment e : toRemove) {
            int lvl = meta.getEnchantLevel(e);
            if (lvl <= 0) continue;

            bm.addStoredEnchant(e, lvl, false);
            meta.removeEnchant(e);

            String key = ExtendedAnvilUtil.enchantKey(e);
            NamespacedKey countKey = config.keyForRemovalCount(key);
            Integer count = pdc.get(countKey, PersistentDataType.INTEGER);
            int newCount = (count == null ? 0 : count) + 1;
            pdc.set(countKey, PersistentDataType.INTEGER, newCount);

            refundLevels += computeRefundLevelsFor(lvl, newCount);
        }

        outBook.setItemMeta(bm);
        item.setItemMeta(meta);

        if (refundLevels > 0) {
            player.giveExpLevels(refundLevels);
        }

        return DisenchantResult.ok(outBook, refundLevels, toRemove.size());
    }

    private int computeRefundLevelsFor(int enchantLevel, int removalCountAfterIncrement) {
        int pct;
        if (removalCountAfterIncrement <= 1) pct = config.getRefundPercentFirst();
        else if (removalCountAfterIncrement == 2) pct = config.getRefundPercentSecond();
        else pct = config.getRefundPercentLater();

        int baseLevels = config.getRefundLevelsPerEnchantLevel() * Math.max(1, enchantLevel);
        return (baseLevels * pct) / 100;
    }

    private boolean isEligibleForRemoval(Enchantment e) {
        if (e == null) return false;
        if (e.isCursed() && !config.isAllowCurseRemoval()) return false;
        return true;
    }

    private List<Enchantment> orderByPriority(Set<Enchantment> enchants) {
        Map<String, Enchantment> map = new HashMap<>();
        for (Enchantment e : enchants) {
            map.put(ExtendedAnvilUtil.enchantKey(e), e);
        }

        List<Enchantment> out = new ArrayList<>();
        for (String key : config.getPriority()) {
            Enchantment e = map.get(key);
            if (e != null) out.add(e);
        }

        for (Enchantment e : enchants) {
            if (!out.contains(e)) out.add(e);
        }
        return out;
    }

    private int computeCostLevels(int enchantCount, int sumStoredLevels) {
        return Math.max(0,
                config.getApplyCostBaseLevels()
                        + (config.getApplyCostPerEnchant() * Math.max(0, enchantCount))
                        + (config.getApplyCostPerStoredLevel() * Math.max(0, sumStoredLevels))
        );
    }

    private int sumLevels(List<Map.Entry<Enchantment, Integer>> list) {
        int s = 0;
        for (Map.Entry<Enchantment, Integer> e : list) {
            if (e == null || e.getValue() == null) continue;
            s += Math.max(0, e.getValue());
        }
        return s;
    }

    private int sumLevels(Map<Enchantment, Integer> map) {
        int s = 0;
        for (Integer v : map.values()) {
            if (v == null) continue;
            s += Math.max(0, v);
        }
        return s;
    }

    private boolean sameStoredEnchants(Map<Enchantment, Integer> a, Map<Enchantment, Integer> b) {
        if (a.size() != b.size()) return false;
        for (Map.Entry<Enchantment, Integer> e : a.entrySet()) {
            Integer bv = b.get(e.getKey());
            if (bv == null) return false;
            if (!Objects.equals(bv, e.getValue())) return false;
        }
        return true;
    }

    public record DisenchantResult(boolean ok, ItemStack book, int refundLevels, int removedCount, String message) {
        static DisenchantResult ok(ItemStack book, int refundLevels, int removedCount) {
            return new DisenchantResult(true, book, refundLevels, removedCount, null);
        }
        static DisenchantResult fail(String msg) {
            return new DisenchantResult(false, null, 0, 0, msg);
        }
    }

    public record ApplyResult(boolean ok, String message, int costLevels) {
        static ApplyResult ok(String msg, int costLevels) {
            return new ApplyResult(true, msg, costLevels);
        }
        static ApplyResult fail(String msg) {
            return new ApplyResult(false, msg, 0);
        }
    }
}
