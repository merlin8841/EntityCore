package com.entitycore.modules.questbuilder;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class QuestBuilderTool {

    private QuestBuilderTool() {}

    public static ItemStack create() {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        meta.setDisplayName("§b§lQuest Builder");
        meta.setLore(List.of(
                "§7Operator tool",
                "§7Right-click: use mode",
                "§7Sneak + Right-click: cycle mode",
                "§7Mode stored via PDC"
        ));

        meta.getPersistentDataContainer().set(QuestBuilderKeys.TOOL, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(QuestBuilderKeys.MODE, PersistentDataType.STRING, QuestBuilderMode.POINT.name());
        meta.getPersistentDataContainer().set(QuestBuilderKeys.DRAFT, PersistentDataType.STRING, "default");

        stick.setItemMeta(meta);
        return stick;
    }

    public static void give(Player player) {
        player.getInventory().addItem(create());
    }

    public static boolean isTool(ItemStack item) {
        return item != null
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(QuestBuilderKeys.TOOL, PersistentDataType.BYTE);
    }
}
