package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
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

        // If target is an enchanted book, we allow combining into a multi-enchant book,
        // BUT we do NOT allow any level upgrades beyond max(existing, incoming).
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
     * - COST SCALES using per-enchant base/per-level cost + optional global adders + prior-work
     * - STORES per-enchant cost metadata onto the item (PDC) for consistent future refunds
     */
    private ApplyResult applyBookToItem(Player player, ItemStack item, ItemStack enchantedBook) {
        if (!(enchantedBook.getItemMeta() instanceof EnchantmentStorageMeta esm)) {
            return ApplyResult.fail(ChatColor.RED + "Invalid enchanted book.");
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return ApplyResult.fail(ChatColor.RED + "That item can't be modified.");

        Map<Enchantment, Integer> stored = esm.getStoredEnchants();
        if (stored.isEmpty()) return ApplyResult.fail(ChatColor.RED + "That book has no stored enchants.");

        // Determine which enchants would actually apply (must be higher than existing)
        List<Map.Entry<Enchantment, Integer>> applicable = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            Enchantment ench = entry.getKey();
            int newLevel = entry.getValue() == null ? 0 : entry.getValue();
            if (ench == null || newLevel <= 0) continue;

            String ek = ExtendedAnvilUtil.enchantKey(ench);
            int cap = config.getEnchantMaxLevel(ek);
            int effectiveLevel = Math.min(newLevel, cap);
            if (effectiveLevel <= 0) continue;

            if (!ench.canEnchantItem(item)) {
                return ApplyResult.fail(ChatColor.RED + "Can't apply "
                        + ExtendedAnvilUtil.prettyEnchantName(ek) + " to that item.");
            }

            int cur = itemMeta.getEnchantLevel(ench);
            if (effectiveLevel > cur) {
                applicable.add(new java.util.AbstractMap.SimpleEntry<>(ench, effectiveLevel));
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

        // Compute cost using per-enchant costs (from book metadata if present, else config), plus global adders and prior work.
        CostBreakdown breakdown = computeApplyCostForItem(itemMeta, esm, applicable);
        int costLevels = breakdown.totalLevels;

        if (costLevels > 0 && player.getLevel() < costLevels) {
            return ApplyResult.fail(ChatColor.RED + "You need " + costLevels + " levels to apply that book.");
        }

        // Apply enchants
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

        // Update prior-work + store per-enchant cost metadata onto the item so refunds remain consistent.
        stampItemCostsAndPriorWork(itemMeta, esm, applicable);

        item.setItemMeta(itemMeta);

        if (costLevels > 0) {
            player.giveExpLevels(-costLevels);
        }

        return ApplyResult.ok(ChatColor.GREEN + "Enchants applied. Cost: " + costLevels + " level(s).", costLevels);
    }

    /**
     * Combine enchanted book onto enchanted book:
     * - merges enchants into a multi-enchant book
     * - DOES NOT upgrade levels: result level = max(levelA, levelB)
     * - rejects conflicts within the merged book
     * - preserves per-enchant cost metadata (PDC) so reapply scaling stays correct
     * - uses cost = sum(per-enchant costs) + optional global adders (no prior-work)
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

        if (t.isEmpty()) return ApplyResult.fail(ChatColor.RED + "Target book has no stored enchants.");
        if (a.isEmpty()) return ApplyResult.fail(ChatColor.RED + "That book has no stored enchants.");

        // Build merged levels (max per enchant)
        Map<Enchantment, Integer> merged = new HashMap<>(t);
        for (Map.Entry<Enchantment, Integer> entry : a.entrySet()) {
            Enchantment ench = entry.getKey();
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (ench == null || lvl <= 0) continue;

            int cur = merged.getOrDefault(ench, 0);
            String ek = ExtendedAnvilUtil.enchantKey(ench);
            int cap = config.getEnchantMaxLevel(ek);
            int newLevel = Math.min(Math.max(cur, lvl), cap);
            merged.put(ench, newLevel);
        }

        // Reject conflicts inside merged set (avoid insane books)
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

        // Determine if anything actually changes
        boolean changed = !sameStoredEnchants(tMeta.getStoredEnchants(), merged);
        if (!changed) {
            return ApplyResult.fail(ChatColor.RED + "Nothing new to combine (no new enchants or higher levels).");
        }

        // Compute cost based on per-enchant costs (preserve metadata when possible)
        int costLevels = computeCombineCostLevels(tMeta, aMeta, merged);
        if (costLevels > 0 && player.getLevel() < costLevels) {
            return ApplyResult.fail(ChatColor.RED + "You need " + costLevels + " levels to combine those books.");
        }

        // Write merged enchants back
        EnchantmentStorageMeta out = (EnchantmentStorageMeta) targetBook.getItemMeta();
        if (out == null) return ApplyResult.fail(ChatColor.RED + "Failed to edit target book.");

        // Clear stored enchants
        for (Enchantment e : new HashSet<>(out.getStoredEnchants().keySet())) {
            out.removeStoredEnchant(e);
        }
        // Add merged enchants
        for (Map.Entry<Enchantment, Integer> entry : merged.entrySet()) {
            out.addStoredEnchant(entry.getKey(), entry.getValue(), false);
        }

        // Merge cost metadata onto output book (PDC on the book meta)
        mergeBookCostMetadata(out, tMeta, aMeta, merged);

        targetBook.setItemMeta(out);

        if (costLevels > 0) {
            player.giveExpLevels(-costLevels);
        }

        return ApplyResult.ok(ChatColor.GREEN + "Books combined. Cost: " + costLevels + " level(s).", costLevels);
    }

    /**
     * Disenchants an item into one enchanted book.
     * - removeAll=true => all removable enchants moved to ONE book
     * - removeAll=false => one enchant removed per click by priority
     *
     * Refund is returned in LEVELS (giveExpLevels), applied on a 1-tick delay for Bedrock/Geyser safety.
     * Refund is computed from per-enchant cost metadata stored on the item if present, else from config.
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

            // Move enchant to book
            bm.addStoredEnchant(e, lvl, false);

            String ek = ExtendedAnvilUtil.enchantKey(e);

            // Determine intrinsic cost for this enchant (prefer stored on item, else compute from config)
            int intrinsicCost = readStoredCost(pdc, ek);
            if (intrinsicCost <= 0) {
                intrinsicCost = computeIntrinsicCostFromConfig(ek, lvl);
            }

            // Stamp that cost onto the output book (so reapply scales properly)
            PersistentDataContainer bookPdc = bm.getPersistentDataContainer();
            writeStoredCost(bookPdc, ek, intrinsicCost);

            // Remove enchant from item
            meta.removeEnchant(e);

            // Remove stored cost metadata from item (optional but keeps it tidy)
            removeStoredCost(pdc, ek);

            // Update removal count for diminishing returns
            NamespacedKey countKey = config.keyForRemovalCount(ek);
            Integer count = pdc.get(countKey, PersistentDataType.INTEGER);
            int newCount = (count == null ? 0 : count) + 1;
            pdc.set(countKey, PersistentDataType.INTEGER, newCount);

            int pct;
            if (newCount <= 1) pct = config.getRefundPercentFirst();
            else if (newCount == 2) pct = config.getRefundPercentSecond();
            else pct = config.getRefundPercentLast();

            refundLevels += (intrinsicCost * pct) / 100;
        }

        outBook.setItemMeta(bm);
        item.setItemMeta(meta);

        // Apply refund as LEVELS on a 1-tick delay (Bedrock-friendly)
        if (refundLevels > 0) {
            int give = refundLevels;
            Bukkit.getScheduler().runTask(plugin, () -> player.giveExpLevels(give));
        }

        return DisenchantResult.ok(outBook, refundLevels, toRemove.size());
    }

    // -------------------------
    // Cost model helpers
    // -------------------------

    private static final class CostBreakdown {
        final int intrinsicSum;
        final int globalAdders;
        final int priorWorkAdd;
        final int totalLevels;

        CostBreakdown(int intrinsicSum, int globalAdders, int priorWorkAdd) {
            this.intrinsicSum = intrinsicSum;
            this.globalAdders = globalAdders;
            this.priorWorkAdd = priorWorkAdd;
            this.totalLevels = Math.max(0, intrinsicSum + globalAdders + priorWorkAdd);
        }
    }

    private CostBreakdown computeApplyCostForItem(ItemMeta itemMeta, EnchantmentStorageMeta bookMeta,
                                                 List<Map.Entry<Enchantment, Integer>> applicable) {
        int intrinsicSum = 0;
        int enchCount = 0;
        int storedLevelSum = 0;

        PersistentDataContainer bookPdc = bookMeta.getPersistentDataContainer();

        for (Map.Entry<Enchantment, Integer> entry : applicable) {
            Enchantment e = entry.getKey();
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (e == null || lvl <= 0) continue;

            enchCount++;
            storedLevelSum += lvl;

            String ek = ExtendedAnvilUtil.enchantKey(e);

            // Prefer cost metadata stored on the book (prevents weird scaling when combined)
            int c = readStoredCost(bookPdc, ek);
            if (c <= 0) {
                c = computeIntrinsicCostFromConfig(ek, lvl);
            }
            intrinsicSum += Math.max(0, c);
        }

        int global = 0;
        global += config.getApplyCostGlobalBaseLevels();
        global += config.getApplyCostPerEnchantAdd() * enchCount;
        global += config.getApplyCostPerStoredLevelAdd() * storedLevelSum;

        // Prior work penalty stored on item PDC
        int priorAdd = 0;
        PersistentDataContainer itemPdc = itemMeta.getPersistentDataContainer();
        Integer prior = itemPdc.get(ExtendedAnvilKeys.priorWork(plugin), PersistentDataType.INTEGER);
        int priorWork = prior == null ? 0 : Math.max(0, prior);
        if (priorWork > 0 && config.getPriorWorkCostPerStep() > 0) {
            priorAdd = priorWork * config.getPriorWorkCostPerStep();
        }

        return new CostBreakdown(intrinsicSum, global, priorAdd);
    }

    private int computeCombineCostLevels(EnchantmentStorageMeta targetMeta, EnchantmentStorageMeta addMeta,
                                        Map<Enchantment, Integer> merged) {
        int intrinsicSum = 0;
        int enchCount = 0;
        int levelSum = 0;

        PersistentDataContainer tPdc = targetMeta.getPersistentDataContainer();
        PersistentDataContainer aPdc = addMeta.getPersistentDataContainer();

        for (Map.Entry<Enchantment, Integer> entry : merged.entrySet()) {
            Enchantment e = entry.getKey();
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (e == null || lvl <= 0) continue;

            enchCount++;
            levelSum += lvl;

            String ek = ExtendedAnvilUtil.enchantKey(e);

            // Prefer metadata from whichever book "provides" that level:
            // If add book has >= target level, prefer add's stored cost; otherwise target's.
            int targetLvl = targetMeta.getStoredEnchantLevel(e);
            int addLvl = addMeta.getStoredEnchantLevel(e);

            int c;
            if (addLvl >= targetLvl) c = readStoredCost(aPdc, ek);
            else c = readStoredCost(tPdc, ek);

            if (c <= 0) c = Math.max(readStoredCost(tPdc, ek), readStoredCost(aPdc, ek));
            if (c <= 0) c = computeIntrinsicCostFromConfig(ek, lvl);

            intrinsicSum += Math.max(0, c);
        }

        int global = 0;
        global += config.getApplyCostGlobalBaseLevels();
        global += config.getApplyCostPerEnchantAdd() * enchCount;
        global += config.getApplyCostPerStoredLevelAdd() * levelSum;

        return Math.max(0, intrinsicSum + global);
    }

    private void stampItemCostsAndPriorWork(ItemMeta itemMeta, EnchantmentStorageMeta bookMeta,
                                           List<Map.Entry<Enchantment, Integer>> applicable) {
        PersistentDataContainer itemPdc = itemMeta.getPersistentDataContainer();
        PersistentDataContainer bookPdc = bookMeta.getPersistentDataContainer();

        // Increment prior-work
        NamespacedKey priorKey = ExtendedAnvilKeys.priorWork(plugin);
        Integer prior = itemPdc.get(priorKey, PersistentDataType.INTEGER);
        int cur = prior == null ? 0 : Math.max(0, prior);
        int next = cur + Math.max(0, config.getPriorWorkIncrementPerApply());
        itemPdc.set(priorKey, PersistentDataType.INTEGER, next);

        // Store per-enchant cost metadata onto the item
        for (Map.Entry<Enchantment, Integer> entry : applicable) {
            Enchantment e = entry.getKey();
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (e == null || lvl <= 0) continue;

            String ek = ExtendedAnvilUtil.enchantKey(e);

            int cost = readStoredCost(bookPdc, ek);
            if (cost <= 0) cost = computeIntrinsicCostFromConfig(ek, lvl);

            writeStoredCost(itemPdc, ek, cost);
        }
    }

    private int computeIntrinsicCostFromConfig(String enchantKey, int level) {
        // base + (perLevel * (level-1))
        int base = config.getEnchantBaseCost(enchantKey);
        int per = config.getEnchantPerLevelCost(enchantKey);
        int lvlAdj = Math.max(0, level - 1);
        return Math.max(0, base + (per * lvlAdj));
    }

    private int readStoredCost(PersistentDataContainer pdc, String enchantKey) {
        if (pdc == null || enchantKey == null) return 0;
        NamespacedKey k = ExtendedAnvilKeys.bookEnchantCost(plugin, enchantKey);
        Integer v = pdc.get(k, PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    private void writeStoredCost(PersistentDataContainer pdc, String enchantKey, int cost) {
        if (pdc == null || enchantKey == null) return;
        NamespacedKey k = ExtendedAnvilKeys.bookEnchantCost(plugin, enchantKey);
        pdc.set(k, PersistentDataType.INTEGER, Math.max(0, cost));
    }

    private void removeStoredCost(PersistentDataContainer pdc, String enchantKey) {
        if (pdc == null || enchantKey == null) return;
        NamespacedKey k = ExtendedAnvilKeys.bookEnchantCost(plugin, enchantKey);
        pdc.remove(k);
    }

    private void mergeBookCostMetadata(EnchantmentStorageMeta out, EnchantmentStorageMeta targetMeta,
                                       EnchantmentStorageMeta addMeta, Map<Enchantment, Integer> merged) {
        PersistentDataContainer outPdc = out.getPersistentDataContainer();
        PersistentDataContainer tPdc = targetMeta.getPersistentDataContainer();
        PersistentDataContainer aPdc = addMeta.getPersistentDataContainer();

        // Remove any old costs not in merged
        Set<String> mergedKeys = new HashSet<>();
        for (Enchantment e : merged.keySet()) {
            mergedKeys.add(ExtendedAnvilUtil.enchantKey(e));
        }

        // For each merged enchant, select the cost metadata from the source that provides the kept level.
        for (Map.Entry<Enchantment, Integer> entry : merged.entrySet()) {
            Enchantment e = entry.getKey();
            int lvl = entry.getValue() == null ? 0 : entry.getValue();
            if (e == null || lvl <= 0) continue;

            String ek = ExtendedAnvilUtil.enchantKey(e);

            int targetLvl = targetMeta.getStoredEnchantLevel(e);
            int addLvl = addMeta.getStoredEnchantLevel(e);

            int c;
            if (addLvl >= targetLvl) c = readStoredCost(aPdc, ek);
            else c = readStoredCost(tPdc, ek);

            if (c <= 0) c = Math.max(readStoredCost(tPdc, ek), readStoredCost(aPdc, ek));
            if (c <= 0) c = computeIntrinsicCostFromConfig(ek, lvl);

            writeStoredCost(outPdc, ek, c);
        }

        // Best-effort cleanup: remove costs for enchants no longer stored
        // (We can't iterate all PDC keys safely here; leaving extras is harmless.)
        // So we simply do nothing beyond writing merged ones.
    }

    // -------------------------
    // Priority helpers
    // -------------------------

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

    private boolean sameStoredEnchants(Map<Enchantment, Integer> a, Map<Enchantment, Integer> b) {
        if (a.size() != b.size()) return false;
        for (Map.Entry<Enchantment, Integer> e : a.entrySet()) {
            Integer bv = b.get(e.getKey());
            if (bv == null) return false;
            if (!Objects.equals(bv, e.getValue())) return false;
        }
        return true;
    }

    // -------------------------
    // Results
    // -------------------------

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
