package com.entitycore.modules.extendedanvil;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;

public final class XpRefundService {

    private final ExtendedAnvilConfig cfg;

    public XpRefundService(ExtendedAnvilConfig config) {
        this.cfg = config;
    }

    public void refundForDisenchant(Player player,
                                   LinkedHashMap<Enchantment, Integer> removed,
                                   ItemStack itemThatPersists) {
        // Safe no-op for now unless you want the full PDC tracking version next.
        // This is intentionally stable so we do not affect item movement / GUI safety.
        if (player == null || removed == null || removed.isEmpty()) return;
        if (!cfg.isRefundsEnabled()) return;
    }
}
