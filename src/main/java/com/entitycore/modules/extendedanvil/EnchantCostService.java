package com.entitycore.modules.extendedanvil;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.*;

public final class EnchantCostService {

    private final ExtendedAnvilConfig cfg;

    public EnchantCostService(ExtendedAnvilConfig cfg) {
        this.cfg = cfg;
    }

    public static final class ApplyPreview {
        public final boolean canApply;
        public final int levelCost;
        public final ItemStack result;

        ApplyPreview(boolean canApply, int levelCost, ItemStack result) {
            this.canApply = canApply;
            this.levelCost = levelCost;
            this.result = result;
        }
    }

    public ApplyPreview previewApply(Player viewer, ItemStack item, ItemStack enchantedBook) {
        if (viewer == null || item == null || enchantedBook == null) {
            return new ApplyPreview(false, 0, null);
        }
        if (item.getType() == Material.AIR) return new ApplyPreview(false, 0, null);
        if (enchantedBook.getType() != Material.ENCHANTED_BOOK) return new ApplyPreview(false, 0, null);

        if (!(enchantedBook.getItemMeta() instanceof EnchantmentStorageMeta)) {
            return new ApplyPreview(false, 0, null);
        }

        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
        Map<Enchantment, Integer> stored = meta.getStoredEnchants();
        if (stored == null || stored.isEmpty()) return new ApplyPreview(false, 0, null);

        // build result by applying allowed enchants
        ItemStack result = item.clone();
        Map<Enchantment, Integer> current = result.getEnchantments();

        int cost = 0;
        boolean appliedAny = false;

        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            Enchantment ench = entry.getKey();
            int fromBookLevel = entry.getValue();

            if (ench == null || fromBookLevel <= 0) continue;

            // must be applicable (like vanilla)
            if (!ench.canEnchantItem(result)) continue;

            // conflicts
            boolean conflicts = false;
            for (Enchantment existing : current.keySet()) {
                if (existing == null) continue;
                if (existing.conflictsWith(ench)) {
                    conflicts = true;
                    break;
                }
            }
            if (conflicts) continue;

            int cap = cfg.getCap(ench.getKey().toString());
            if (cap > 0) fromBookLevel = Math.min(fromBookLevel, cap);

            int existingLevel = current.getOrDefault(ench, 0);

            // locked rule: overwrite lower
            if (fromBookLevel > existingLevel) {
                result.addUnsafeEnchantment(ench, fromBookLevel);
                appliedAny = true;

                // cost: scale with level and whether it's new
                int add = (fromBookLevel * 2) + (existingLevel == 0 ? 1 : 0);
                cost += add;
            }
        }

        if (!appliedAny) return new ApplyPreview(false, 0, null);

        // cost must be at least 1
        cost = Math.max(1, cost);

        return new ApplyPreview(true, cost, result);
    }
}
