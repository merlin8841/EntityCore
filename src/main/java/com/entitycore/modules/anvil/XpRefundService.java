package com.entitycore.modules.anvil;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class XpRefundService {

    private final JavaPlugin plugin;
    private final NamespacedKey keyRemovalMap;

    public XpRefundService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyRemovalMap = new NamespacedKey(plugin, "ea_removed_counts"); // enchantKey=count|...
    }

    /**
     * Refunds "levels" by converting to xp using vanilla-ish curve via giveExpLevels.
     * We intentionally keep this conservative & operator-tunable in config.
     */
    public int refundForRemoval(Player player, ItemStack updatedItem, Map<Enchantment, Integer> removed) {
        if (player == null || updatedItem == null || removed == null || removed.isEmpty()) return 0;

        double first = clamp01(plugin.getConfig().getDouble("extendedanvil.refund.first", 0.75));
        double second = clamp01(plugin.getConfig().getDouble("extendedanvil.refund.second", 0.25));
        double third = clamp01(plugin.getConfig().getDouble("extendedanvil.refund.thirdPlus", 0.0));

        int maxLevels = Math.max(0, plugin.getConfig().getInt("extendedanvil.refund.max-levels-per-op", 30));
        double basePerEnchant = Math.max(0.0, plugin.getConfig().getDouble("extendedanvil.refund.base-levels-per-enchant", 2.0));
        double levelMult = Math.max(0.0, plugin.getConfig().getDouble("extendedanvil.refund.level-multiplier", 1.0));

        ItemMeta meta = updatedItem.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Map<String, Integer> counts = readCounts(pdc);

        double totalLevels = 0.0;

        for (Map.Entry<Enchantment, Integer> e : removed.entrySet()) {
            Enchantment ench = e.getKey();
            int lvl = Math.max(1, e.getValue());

            String k = ench.getKey().toString();
            int c = counts.getOrDefault(k, 0);

            double pct = (c == 0) ? first : (c == 1) ? second : third;

            // Conservative "level value" approximation (tunable)
            double value = basePerEnchant + (lvl * levelMult);

            totalLevels += value * pct;

            // increment removal count for this enchant on this item
            counts.put(k, c + 1);
        }

        // write back PDC
        writeCounts(pdc, counts);
        updatedItem.setItemMeta(meta);

        int give = (int) Math.floor(totalLevels);
        if (give <= 0) return 0;
        if (maxLevels > 0) give = Math.min(give, maxLevels);

        player.giveExpLevels(give);
        return give;
    }

    private Map<String, Integer> readCounts(PersistentDataContainer pdc) {
        String raw = pdc.get(keyRemovalMap, PersistentDataType.STRING);
        Map<String, Integer> out = new HashMap<>();
        if (raw == null || raw.isBlank()) return out;

        String[] parts = raw.split("\\|");
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim();
            try {
                int v = Integer.parseInt(kv[1].trim());
                if (!k.isEmpty() && v >= 0) out.put(k, v);
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private void writeCounts(PersistentDataContainer pdc, Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            pdc.remove(keyRemovalMap);
            return;
        }

        // deterministic output
        List<String> keys = new ArrayList<>(counts.keySet());
        keys.sort(String::compareTo);

        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            int v = counts.getOrDefault(k, 0);
            if (v < 0) v = 0;
            if (sb.length() > 0) sb.append("|");
            sb.append(k).append("=").append(v);
        }

        pdc.set(keyRemovalMap, PersistentDataType.STRING, sb.toString());
    }

    private double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
