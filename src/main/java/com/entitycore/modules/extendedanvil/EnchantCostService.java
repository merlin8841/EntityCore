package com.entitycore.modules.extendedanvil;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class EnchantCostService {

    public static final class ApplyPreview {
        public final boolean canApply;
        public final ItemStack result;
        public final int levelCost;

        public ApplyPreview(boolean canApply, ItemStack result, int levelCost) {
            this.canApply = canApply;
            this.result = result;
            this.levelCost = levelCost;
        }
    }

    public ApplyPreview previewApply(Player player, ItemStack item, ItemStack book) {
        if (item == null || book == null) return new ApplyPreview(false, null, 0);

        ItemStack out = item.clone();
        int cost = 0;

        // Placeholder scaling (we will replace with vanilla-like later)
        for (Map.Entry<Enchantment, Integer> e : book.getEnchantments().entrySet()) {
            out.addUnsafeEnchantment(e.getKey(), e.getValue());
            cost += Math.max(1, e.getValue()) * 2;
        }

        return new ApplyPreview(true, out, cost);
    }
}
