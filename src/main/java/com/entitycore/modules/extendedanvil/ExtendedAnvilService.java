package com.entitycore.modules.extendedanvil;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

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

    /** Cost for applying a single enchant level to a target item (caps are enforced). */
    public int computeEnchantCost(ItemStack target, Enchantment ench, int levelToApply) {
        if (target == null || ench == null) return 0;

        int cap = config.capFor(ench);
        int level = Math.min(levelToApply, cap);
        if (level <= 0) return 0;

        int base = config.enchantBaseCostPerLevel();
        int mult = config.enchantAddCountMultiplier();
        int addCount = getAddCount(target, ench);

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

    /** Merge rules: never upgrades levels; same enchant => keep higher. Conflicts blocked. Caps enforced. */
    public MergeResult mergeInto(ItemStack base, ItemStack addition) {
        if (base == null || addition == null) return MergeResult.fail("Missing base/addition.");

        ItemStack out = base.clone();
        ItemMeta outMeta = out.getItemMeta();
        if (outMeta == null) return MergeResult.fail("Base item cannot be modified.");

        Map<Enchantment, Integer> addEnchants = extractAllEnchants(addition);
        if (addEnchants.isEmpty()) {
            return MergeResult.fail("No enchants to merge.");
        }

        int totalCost = 0;
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> e : addEnchants.entrySet()) {
            Enchantment ench = e.getKey();
            int addLevel = e.getValue() == null ? 0 : e.getValue();
            if (ench == null || addLevel <= 0) continue;

            // conflict check vs existing (block this enchant only)
            boolean conflict = false;
            for (Enchantment existing : outMeta.getEnchants().keySet()) {
                if (existing == null) continue;
                if (existing.conflictsWith(ench) || ench.conflictsWith(existing)) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;

            int cap = config.capFor(ench);
            int clamped = Math.min(addLevel, cap);
            if (clamped <= 0) continue;

            int current = outMeta.getEnchantLevel(ench);
            int resultLevel = Math.max(current, clamped); // IMPORTANT: never +1 combine
            if (resultLevel <= 0) continue;

            if (resultLevel != current) {
                // Cost MUST be computed from the current working item (out),
                // otherwise addCount never scales correctly.
                totalCost += computeEnchantCost(out, ench, resultLevel);

                outMeta.addEnchant(ench, resultLevel, true);

                incrementAddCount(out, ench);
                changed = true;
            }
        }

        if (!changed) return MergeResult.fail("Nothing could be applied (conflicts/caps/current levels).");

        out.setItemMeta(outMeta);
        return MergeResult.ok(out, totalCost);
    }

    /** Disenchant behavior: BOOK count 1 => remove all to one enchanted book. 2+ => remove 1 by priority. */
    public DisenchantOpResult disenchant(ItemStack base, int bookCount, List<Enchantment> priority) {
        if (base == null) return DisenchantOpResult.fail("Missing item.");
        if (bookCount < 1) return DisenchantOpResult.fail("You need at least 1 book.");

        if (bookCount == 1) {
            DisenchantResult r = disenchantAllToOneBook(base, new ItemStack(Material.BOOK));
            if (!r.ok() || r.newItem() == null || r.outBook() == null) {
                return DisenchantOpResult.fail(r.error() == null ? "Disenchant failed." : r.error());
            }
            return DisenchantOpResult.ok(r.newItem(), List.of(r.outBook()), r.returnLevels(), 1);
        } else {
            DisenchantResult r = disenchantOneByPriority(base, bookCount, priority);
            if (!r.ok() || r.newItem() == null || r.outBook() == null) {
                return DisenchantOpResult.fail(r.error() == null ? "Disenchant failed." : r.error());
            }
            return DisenchantOpResult.ok(r.newItem(), List.of(r.outBook()), r.returnLevels(), 1);
        }
    }

    // --------------------
    // Repair logic
    // --------------------

    public boolean isDamageable(ItemStack it) {
        if (it == null) return false;
        ItemMeta meta = it.getItemMeta();
        return meta instanceof Damageable;
    }

    public int getRepairCount(ItemStack item) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer v = pdc.get(keys.repairCountKey(), PersistentDataType.INTEGER);
        return v == null ? 0 : Math.max(0, v);
    }

    public void incrementRepairCount(ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int now = getRepairCount(item) + 1;
        pdc.set(keys.repairCountKey(), PersistentDataType.INTEGER, now);
        item.setItemMeta(meta);
    }

    public int computeRepairCostLevels(ItemStack base) {
        int repairs = getRepairCount(base);
        double mult = config.repairBaseMultiplier() + (repairs * config.repairIncrementPerRepair());
        int baseLevels = config.repairBaseLevels();
        int cost = (int) Math.ceil(baseLevels * mult);
        if (cost < 0) cost = 0;
        return cost;
    }

    /** Attempt repair by material stack; returns amountConsumed + new item. */
    public RepairResult repairWithMaterial(ItemStack base, ItemStack materialStack) {
        if (!config.allowMaterialRepair()) return RepairResult.fail("Material repair disabled.");
        if (base == null || materialStack == null) return RepairResult.fail("Missing item/material.");
        if (!isDamageable(base)) return RepairResult.fail("Item is not damageable.");

        Material mat = materialStack.getType();
        Material needed = guessRepairMaterial(base.getType());
        if (needed == null) return RepairResult.fail("This item can't be material-repaired here.");
        if (mat != needed) return RepairResult.fail("Wrong material. Needs: " + needed);

        ItemStack out = base.clone();
        Damageable dmg = (Damageable) out.getItemMeta();
        if (dmg == null) return RepairResult.fail("Item meta missing.");

        int max = out.getType().getMaxDurability();
        int curDamage = dmg.getDamage();
        int curRemaining = max - curDamage;
        if (curRemaining >= max) return RepairResult.fail("Item is already fully repaired.");

        int perUnit = Math.max(1, (int) Math.floor(max * 0.25)); // ~25% per unit
        int amount = materialStack.getAmount();
        int unitsUsed = 0;

        int remaining = curRemaining;
        while (unitsUsed < amount && remaining < max) {
            remaining = Math.min(max, remaining + perUnit);
            unitsUsed++;
        }

        int newDamage = Math.max(0, max - remaining);
        dmg.setDamage(newDamage);
        out.setItemMeta((ItemMeta) dmg);

        return RepairResult.ok(out, unitsUsed);
    }

    /** Attempt repair by merging same item type (durability combine) + merge enchants (no upgrading). */
    public RepairResult repairWithSameItem(ItemStack base, ItemStack otherSameType) {
        if (!config.allowItemMergeRepair()) return RepairResult.fail("Item-merge repair disabled.");
        if (base == null || otherSameType == null) return RepairResult.fail("Missing items.");
        if (base.getType() != otherSameType.getType()) return RepairResult.fail("Items must be same type.");
        if (!isDamageable(base)) return RepairResult.fail("Item is not damageable.");

        ItemStack out = base.clone();
        ItemMeta outMeta = out.getItemMeta();
        if (!(outMeta instanceof Damageable outDmg)) return RepairResult.fail("Damage meta missing.");

        ItemMeta otherMeta = otherSameType.getItemMeta();
        if (!(otherMeta instanceof Damageable otherDmg)) return RepairResult.fail("Damage meta missing.");

        int max = base.getType().getMaxDurability();
        int baseRemaining = max - outDmg.getDamage();
        int otherRemaining = max - otherDmg.getDamage();

        // vanilla-ish: sum + 12% bonus
        int bonus = (int) Math.floor(max * 0.12);
        int newRemaining = Math.min(max, baseRemaining + otherRemaining + bonus);
        int newDamage = Math.max(0, max - newRemaining);
        outDmg.setDamage(newDamage);

        out.setItemMeta((ItemMeta) outDmg);

        // merge enchants with "keep max" + conflict blocking
        MergeResult merged = mergeInto(out, otherSameType);
        if (merged.ok() && merged.newItem() != null) {
            out = merged.newItem();
        }

        return RepairResult.ok(out, 1);
    }

    private static Material guessRepairMaterial(Material item) {
        if (item == null) return null;
        String n = item.name();

        if (n.equals("ELYTRA")) return Material.PHANTOM_MEMBRANE;
        if (n.equals("TRIDENT")) return Material.PRISMARINE_CRYSTALS;
        if (n.equals("SHIELD")) return Material.OAK_PLANKS;

        if (n.equals("BOW") || n.equals("FISHING_ROD")) return Material.STRING;
        if (n.equals("SHEARS") || n.equals("FLINT_AND_STEEL")) return Material.IRON_INGOT;

        // SCUTE doesn't exist on some older APIs -> safe lookup
        if (n.equals("TURTLE_HELMET")) {
            Material scute = Material.matchMaterial("SCUTE");
            return scute; // may be null on very old jars
        }

        if (n.startsWith("NETHERITE_")) return Material.NETHERITE_INGOT;
        if (n.startsWith("DIAMOND_")) return Material.DIAMOND;
        if (n.startsWith("IRON_")) return Material.IRON_INGOT;
        if (n.startsWith("GOLDEN_")) return Material.GOLD_INGOT;
        if (n.startsWith("CHAINMAIL_")) return Material.IRON_INGOT;
        if (n.startsWith("LEATHER_")) return Material.LEATHER;

        if (n.startsWith("WOODEN_")) return Material.OAK_PLANKS;
        if (n.startsWith("STONE_")) return Material.COBBLESTONE;

        return null;
    }

    private Map<Enchantment, Integer> extractAllEnchants(ItemStack src) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        if (src == null) return out;

        if (isEnchantedBook(src)) {
            out.putAll(getBookStoredEnchants(src));
            return out;
        }

        ItemMeta meta = src.getItemMeta();
        if (meta == null) return out;
        out.putAll(meta.getEnchants());
        return out;
    }

    // --------------------
    // Existing disenchant methods (kept)
    // --------------------

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

        EnchantmentStorageMeta check = (EnchantmentStorageMeta) outBook.getItemMeta();
        if (check == null || check.getStoredEnchants().isEmpty()) {
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

        for (Enchantment ench : priority) {
            if (ench == null) continue;
            Integer lvl = enchants.get(ench);
            if (lvl == null || lvl <= 0) continue;
            if (!canRemove(ench)) continue;
            chosen = ench;
            chosenLevel = lvl;
            break;
        }

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

    // --------------------
    // PDC counts (existing)
    // --------------------

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

    // --------------------
    // Records
    // --------------------

    public record MergeResult(boolean ok, String error, ItemStack newItem, int costLevels) {
        public static MergeResult ok(ItemStack newItem, int costLevels) {
            return new MergeResult(true, null, newItem, costLevels);
        }
        public static MergeResult fail(String error) {
            return new MergeResult(false, error, null, 0);
        }
    }

    public record DisenchantOpResult(boolean ok, String error, ItemStack newItem, List<ItemStack> outBooks, int returnLevels, int booksConsumed) {
        public static DisenchantOpResult ok(ItemStack newItem, List<ItemStack> outBooks, int returnLevels, int booksConsumed) {
            return new DisenchantOpResult(true, null, newItem, outBooks, returnLevels, booksConsumed);
        }
        public static DisenchantOpResult fail(String error) {
            return new DisenchantOpResult(false, error, null, List.of(), 0, 0);
        }
    }

    public record RepairResult(boolean ok, String error, ItemStack newItem, int amountConsumed) {
        public static RepairResult ok(ItemStack newItem, int amountConsumed) {
            return new RepairResult(true, null, newItem, amountConsumed);
        }
        public static RepairResult fail(String error) {
            return new RepairResult(false, error, null, 0);
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
