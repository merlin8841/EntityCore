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
                "§7AREA_SET: alternates pos1/pos2",
                "§7PREVIEW: left-click toggles border",
                "§7EDITOR: opens popup editor",
                "§7Mode stored via PDC"
        ));

        meta.getPersistentDataContainer().set(QuestBuilderKeys.TOOL, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(QuestBuilderKeys.MODE, PersistentDataType.STRING, QuestBuilderMode.POINT.name());
        meta.getPersistentDataContainer().set(QuestBuilderKeys.DRAFT, PersistentDataType.STRING, "default");

        // AREA_SET starts at pos1
        meta.getPersistentDataContainer().set(QuestBuilderKeys.AREA_NEXT, PersistentDataType.INTEGER, 1);

        // Preview border ON by default
        meta.getPersistentDataContainer().set(QuestBuilderKeys.PREVIEW_BORDER, PersistentDataType.BYTE, (byte) 1);

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
