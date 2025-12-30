package com.entitycore.modules.questbuilder;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderTool {

    private QuestBuilderTool() {}

    public static void give(Player player, JavaPlugin plugin) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        meta.setDisplayName("§b§lQuest Builder");
        meta.setLore(java.util.List.of(
                "§7Operator quest tool",
                "§7Use to mark points and areas"
        ));

        meta.getPersistentDataContainer().set(
                QuestBuilderKeys.TOOL,
                PersistentDataType.BYTE,
                (byte) 1
        );
        meta.getPersistentDataContainer().set(
                QuestBuilderKeys.MODE,
                PersistentDataType.STRING,
                QuestBuilderMode.POINT.name()
        );
        meta.getPersistentDataContainer().set(
                QuestBuilderKeys.DRAFT,
                PersistentDataType.STRING,
                "default"
        );
        meta.getPersistentDataContainer().set(
                QuestBuilderKeys.POINT_INDEX,
                PersistentDataType.INTEGER,
                0
        );

        stick.setItemMeta(meta);
        player.getInventory().addItem(stick);
    }

    public static boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                   .has(QuestBuilderKeys.TOOL, PersistentDataType.BYTE);
    }
}
