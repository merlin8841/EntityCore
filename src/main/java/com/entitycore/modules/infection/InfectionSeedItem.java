package com.entitycore.modules.infection;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectionSeedItem {

    private InfectionSeedItem() {}

    public static ItemStack create(JavaPlugin plugin, InfectionConfig config) {
        ItemStack item = new ItemStack(Material.DIRT, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(config.getSeedName());

            if (config.isSeedGlow()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            NamespacedKey key = InfectionKeys.seedKey(plugin);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isSeed(JavaPlugin plugin, ItemStack item) {
        if (item == null) return false;
        if (item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Byte v = meta.getPersistentDataContainer().get(InfectionKeys.seedKey(plugin), PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }
}
