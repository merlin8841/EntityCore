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

/**
 * Core logic for enchanting / disenchanting.
 */
public final class ExtendedAnvilService {

    private final Plugin plugin;
    private final ExtendedAnvilConfig config;

    public ExtendedAnvilService(Plugin plugin, ExtendedAnvilConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Applies enchants from an enchanted book onto an item.
     * - respects vanilla conflicts
     * - replaces lower levels (only if higher than current)
     */
    public String applyBookToItem(Player player, ItemStack item, ItemStack enchantedBook) {
        if (item == null || item.getType().isAir()) return ChatColor.RED + "Put an item in the item slot.";
        if (enchantedBook == null || enchantedBook.getType() != Material.ENCHANTED_BOOK) return ChatColor.RED + "Put an enchanted book in the book slots.";
        if (!(enchantedBook.getItemMeta() instanceof EnchantmentStorageMeta esm)) return ChatColor.RED + "Invalid enchanted book.";
        if (esm.getStoredEnchants().isEmpty()) return ChatColor.RED + "That book has no stored enchants.";

        if (config.getApplyBookLevelCost() > 0 && player.getLevel() < config.getApplyBookLevelCost()) {
            return ChatColor.RED + "You need " + config.getApplyBookLevelCost() + " levels to apply that book.";
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return ChatColor.RED + "That item can't be modified.";

        Map<Enchantment, Integer> stored = esm.getStoredEnchants();

        // Validate conflicts
        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            Enchantment ench = entry.getKey();
            if (ench == null) continue;

            if (!ench.canEnchantItem(item)) {
                return ChatColor.RED + "Can't apply " + ExtendedAnvilUtil.prettyEnchantName(ExtendedAnvilUtil.enchantKey(ench)) + " to that item.";
            }
            for (Enchantment existing : itemMeta.getEnchants().keySet()) {
                if (existing == null) continue;
                if (existing.conflictsWith(ench) || ench.conflictsWith(existing)) {
                    return ChatColor.RED + "That book conflicts with existing enchants.";
                }
            }
        }

        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            Enchantment ench = entry.getKey();
            int newLevel = entry.getValue() == null ? 0 : entry.getValue();
            if (ench == null || newLevel <= 0) continue;

            int cur = itemMeta.getEnchantLevel(ench);
            if (newLevel > cur) {
                itemMeta.addEnchant(ench, newLevel, false);
                changed = true;
            }
        }

        if (!changed) {
            return ChatColor.RED + "Nothing to apply (item already has equal/higher levels).";
        }

        item.setItemMeta(itemMeta);

        if (config.getApplyBookLevelCost() > 0) {
            player.setLevel(player.getLevel() - config.getApplyBookLevelCost());
        }

        return ChatColor.GREEN + "Enchants applied.";
    }

    /**
     * Disenchants an item into one enchanted book.
     * - 1 empty book: removeAll=true => all enchants moved to ONE book
     * - 2+ empty books: removeAll=false => one enchant removed per click by priority
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

        int xpRefund = 0;
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

            xpRefund += computeRefundFor(lvl, newCount);
        }

        outBook.setItemMeta(bm);
        item.setItemMeta(meta);

        if (xpRefund > 0) {
            player.giveExp(xpRefund);
        }

        return DisenchantResult.ok(outBook, xpRefund, toRemove.size());
    }

    private int computeRefundFor(int level, int removalCountAfterIncrement) {
        int pct;
        if (removalCountAfterIncrement <= 1) pct = config.getRefundPercentFirst();
        else if (removalCountAfterIncrement == 2) pct = config.getRefundPercentSecond();
        else pct = config.getRefundPercentLater();

        int base = config.getXpPerEnchantLevel() * Math.max(1, level);
        return (base * pct) / 100;
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

    public record DisenchantResult(boolean ok, ItemStack book, int xpRefund, int removedCount, String message) {
        static DisenchantResult ok(ItemStack book, int xpRefund, int removedCount) {
            return new DisenchantResult(true, book, xpRefund, removedCount, null);
        }
        static DisenchantResult fail(String msg) {
            return new DisenchantResult(false, null, 0, 0, msg);
        }
    }
}
