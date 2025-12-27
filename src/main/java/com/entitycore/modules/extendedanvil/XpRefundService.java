package com.entitycore.modules.extendedanvil;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class XpRefundService {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig cfg;

    public XpRefundService(JavaPlugin plugin, ExtendedAnvilConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    /**
     * Diminishing refund per-enchantment per-item:
     * - 1st time this enchant removed from THIS ITEM => cfg.refund.first
     * - 2nd time => cfg.refund.second
     * - 3rd+ => cfg.refund.after
     *
     * Refund is computed from a simple "levels value" based on enchant level.
     * (Stable, non-exploitable, and configurable by percentages.)
     */
    public void refundForRemoval(Player player, ItemStack itemAfterRemoval, LinkedHashMap<Enchantment, Integer> removed) {
        if (player == null || itemAfterRemoval == null || removed == null || removed.isEmpty()) return;

        ItemMeta meta = itemAfterRemoval.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int totalLevels = 0;

        for (Map.Entry<Enchantment, Integer> e : removed.entrySet()) {
            Enchantment ench = e.getKey();
            int lvl = e.getValue() == null ? 0 : e.getValue();
            if (ench == null || lvl <= 0) continue;

            NamespacedKey key = new NamespacedKey(plugin, "ea_rm_" + ench.getKey().toString().replace(":", "_"));
            Integer count = pdc.get(key, PersistentDataType.INTEGER);
            int c = (count == null) ? 0 : count;

            double pct;
            if (c <= 0) pct = cfg.getRefundFirst();
            else if (c == 1) pct = cfg.getRefundSecond();
            else pct = cfg.getRefundAfter();

            // Increment count
            pdc.set(key, PersistentDataType.INTEGER, c + 1);

            // Base refund value in "levels" (simple, stable):
            // higher-level enchants refund more; curses minimal.
            int base = estimateLevelsValue(ench, lvl);
            int refund = (int) Math.floor(base * pct);

            if (refund > 0) totalLevels += refund;
        }

        // Save PDC
        itemAfterRemoval.setItemMeta(meta);

        if (totalLevels > 0) {
            player.giveExpLevels(totalLevels);
        }
    }

    private int estimateLevelsValue(Enchantment ench, int level) {
        if (ench.isCursed()) return Math.max(1, level); // tiny
        if (ench.isTreasure()) return Math.max(2, level * 4);
        return Math.max(1, level * 2);
    }
}
