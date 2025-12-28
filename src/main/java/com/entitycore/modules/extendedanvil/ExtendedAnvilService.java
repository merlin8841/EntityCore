package com.entitycore.modules.extendedanvil;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExtendedAnvilService {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;
    private final ExtendedAnvilKeys keys;

    public ExtendedAnvilService(JavaPlugin plugin, ExtendedAnvilConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.keys = new ExtendedAnvilKeys(plugin);
    }

    public boolean isEmptyBook(ItemStack stack) {
        return stack != null && stack.getType() == Material.BOOK;
    }

    public boolean isEnchantedBook(ItemStack stack) {
        return stack != null && stack.getType() == Material.ENCHANTED_BOOK;
    }

    public Map<Enchantment, Integer> getBookStoredEnchants(ItemStack book) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        if (!isEnchantedBook(book)) return result;
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta esm)) return result;
        for (Map.Entry<Enchantment, Integer> e : esm.getStoredEnchants().entrySet()) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    public int computeEnchantCost(ItemStack target, Enchantment ench, int levelToApply) {
        if (target == null || ench == null) return 0;

        // Clamp to admin cap
        int cap = config.capFor(ench);
        int level = Math.min(levelToApply, cap);

        int base = config.enchantBaseCostPerLevel();
        int mult = config.enchantAddCountMultiplier();

        int addCount = getAddCount(target, ench);

        // Cost scales with level + how many times that enchant has been added to that item.
        // This is deterministic and tunable via config.
        long cost = (long) base * (long) level + (long) mult * (long) addCount;

        if (cost < 0) cost = 0;
        if (cost > Integer.MAX_VALUE) cost = Integer.MAX_VALUE;
        return (int) cost;
    }

    public int computeDisenchantReturn(ItemStack target, Enchantment ench, int removedLevel) {
        if (target == null || ench == null) return 0;

        int removeCount = getRemoveCount(target, ench);

        double pct;
        if (removeCount <= 0) {
            pct = config.firstReturnPercent();
        } else if (removeCount == 1) {
            pct = config.secondSameEnchantReturnPercent();
        } else {
            pct = config.thirdPlusSameEnchantReturnPercent();
        }

        // Return is based on "what it would cost to apply" at that level (using current addCount scaling).
        int baselineCost = computeEnchantCost(target, ench, removedLevel);
        long returned = Math.round(baselineCost * pct);

        if (returned < 0) returned = 0;
        if (returned > Integer.MAX_VALUE) returned = Integer.MAX_VALUE;
        return (int) returned;
    }

    public boolean canRemove(Enchantment ench) {
        if (ench == null) return false;
        if (ench.isCursed()) return config.allowCurseRemoval();
        return true;
    }

    public ApplyEnchantResult applyEnchantedBook(ItemStack target, ItemStack enchantedBook) {
        if (target == null || enchantedBook == null) return ApplyEnchantResult.fail("Missing item/book.");
        if (!isEnchantedBook(enchantedBook)) return ApplyEnchantResult.fail("Book is not an enchanted book.");

        Map<Enchantment, Integer> stored = getBookStoredEnchants(enchantedBook);
        if (stored.isEmpty()) return ApplyEnchantResult.fail("Book has no enchants.");

        // Enforced rule: no book combining here. This GUI only applies book -> item.
        ItemStack newItem = target.clone();
        ItemMeta meta = newItem.getItemMeta();
        if (meta == null) return ApplyEnchantResult.fail("Item cannot be modified.");

        int totalCost = 0;

        for (Map.Entry<Enchantment, Integer> e : stored.entrySet()) {
            Enchantment ench = e.getKey();
            int wantedLevel = e.getValue() == null ? 0 : e.getValue();

            int cap = config.capFor(ench);
            int level = Math.min(wantedLevel, cap);
            if (level <= 0) continue;

            // If item already has this enchant at higher, keep higher (vanilla behavior).
            int current = meta.getEnchantLevel(ench);
            if (current > level) {
                level = current;
            }

            // Apply enchant (unsafe allowed so caps above vanilla still work, but clamped by admin cap)
            meta.addEnchant(ench, level, true);

            totalCost += computeEnchantCost(target, ench, level);
            incrementAddCount(newItem, ench);
        }

        newItem.setItemMeta(meta);
        return ApplyEnchantResult.ok(newItem, totalCost);
    }

    public DisenchantResult disenchantAllToOneBook(ItemStack target, ItemStack emptyBook) {
        if (target == null || emptyBook == null) return DisenchantResult.fail("Missing item/book.");
        if (!isEmptyBook(emptyBook)) return DisenchantResult.fail("You must provide an empty book.");

        ItemStack newItem = target.clone();
        ItemMeta meta = newItem.getItemMeta();
        if (meta == null) return DisenchantResult.fail("Item cannot be modified.");

        Map<Enchantment, Integer> enchants = new LinkedHashMap<>(meta.getEnchants());
        if (enchants.isEmpty()) return DisenchantResult.fail("Item has no enchants.");

        ItemStack outBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) outBook.getItemMeta();

        int totalReturn = 0;

        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            Enchantment ench = e.getKey();
            int lvl = e.getValue() == null ? 0 : e.getValue();
            if (lvl <= 0) continue;
            if (!canRemove(ench)) continue;

            bookMeta.addStoredEnchant(ench, lvl, true);
            meta.removeEnchant(ench);

            totalReturn += computeDisenchantReturn(target, ench, lvl);
            incrementRemoveCount(newItem, ench);
        }

        outBook.setItemMeta(bookMeta);
        newItem.setItemMeta(meta);

        if (((EnchantmentStorageMeta) outBook.getItemMeta()).getStoredEnchants().isEmpty()) {
            return DisenchantResult.fail("No removable enchants found.");
        }

        return DisenchantResult.ok(newItem, outBook, totalReturn, 1);
    }

    public DisenchantResult disenchantOneByPriority(ItemStack target, int emptyBookCount, List<Enchantment> priority) {
        if (target == null) return DisenchantResult.fail("Missing item.");
        if (emptyBookCount < 2) return DisenchantResult.fail("You must provide 2+ books for priority disenchant.");

        ItemStack newItem = target.clone();
        ItemMeta meta = newItem.getItemMeta();
        if (meta == null) return DisenchantResult.fail("Item cannot be modified.");

        Map<Enchantment, Integer> enchants = new LinkedHashMap<>(meta.getEnchants());
        if (enchants.isEmpty()) return DisenchantResult.fail("Item has no enchants.");

        Enchantment chosen = null;
        int chosenLevel = 0;

        // Pick the first enchant present in the priority list
        for (Enchantment ench : priority) {
            if (ench == null) continue;
            Integer lvl = enchants.get(ench);
            if (lvl == null) continue;
            if (lvl <= 0) continue;
            if (!canRemove(ench)) continue;
            chosen = ench;
            chosenLevel = lvl;
            break;
        }

        // Fallback: pick any removable enchant
        if (chosen == null) {
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                Enchantment ench = e.getKey();
                int lvl = e.getValue() == null ? 0 : e.getValue();
                if (lvl <= 0) continue;
                if (!canRemove(ench)) continue;
                chosen = ench;
                chosenLevel = lvl;
                break;
            }
        }

        if (chosen == null) return DisenchantResult.fail("No removable enchants found.");

        ItemStack outBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) outBook.getItemMeta();
        bookMeta.addStoredEnchant(chosen, chosenLevel, true);
        outBook.setItemMeta(bookMeta);

        meta.removeEnchant(chosen);
        newItem.setItemMeta(meta);

        int returned = computeDisenchantReturn(target, chosen, chosenLevel);
        incrementRemoveCount(newItem, chosen);

        return DisenchantResult.ok(newItem, outBook, returned, 1);
    }

    public int getAddCount(ItemStack item, Enchantment ench) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer v = pdc.get(keys.addCountKey(ench), PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    public int getRemoveCount(ItemStack item, Enchantment ench) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer v = pdc.get(keys.removeCountKey(ench), PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    public void incrementAddCount(ItemStack item, Enchantment ench) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int now = getAddCount(item, ench) + 1;
        pdc.set(keys.addCountKey(ench), PersistentDataType.INTEGER, now);
        item.setItemMeta(meta);
    }

    public void incrementRemoveCount(ItemStack item, Enchantment ench) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int now = getRemoveCount(item, ench) + 1;
        pdc.set(keys.removeCountKey(ench), PersistentDataType.INTEGER, now);
        item.setItemMeta(meta);
    }

    public record ApplyEnchantResult(boolean ok, String error, ItemStack newItem, int costLevels) {
        public static ApplyEnchantResult ok(ItemStack newItem, int costLevels) {
            return new ApplyEnchantResult(true, null, newItem, costLevels);
        }
        public static ApplyEnchantResult fail(String error) {
            return new ApplyEnchantResult(false, error, null, 0);
        }
    }

    public record DisenchantResult(boolean ok, String error, ItemStack newItem, ItemStack outBook, int returnLevels, int booksConsumed) {
        public static DisenchantResult ok(ItemStack newItem, ItemStack outBook, int returnLevels, int booksConsumed) {
            return new DisenchantResult(true, null, newItem, outBook, returnLevels, booksConsumed);
        }
        public static DisenchantResult fail(String error) {
            return new DisenchantResult(false, error, null, null, 0, 0);
        }
    }
}
