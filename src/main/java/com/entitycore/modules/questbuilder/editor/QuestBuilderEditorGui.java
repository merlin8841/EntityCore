package com.entitycore.modules.questbuilder.editor;

import com.entitycore.modules.questbuilder.script.QuestScriptRegistry;
import com.entitycore.modules.questbuilder.trigger.QuestTriggerType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class QuestBuilderEditorGui {

    private QuestBuilderEditorGui() {}

    public static final String TITLE_PREFIX = "QuestBuilder Editor: ";

    public static void open(Player player, String areaId, QuestScriptRegistry scripts) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PREFIX + areaId);

        inv.setItem(11, button(Material.LIME_DYE, "§aENTER_AREA actions", List.of(
                "§7Edit actions for ENTER_AREA",
                "§7Current: " + scripts.getActionsForAreaTrigger(areaId, QuestTriggerType.ENTER_AREA).size() + " line(s)",
                "§8Click to edit"
        )));

        inv.setItem(15, button(Material.RED_DYE, "§cEXIT_AREA actions", List.of(
                "§7Edit actions for EXIT_AREA",
                "§7Current: " + scripts.getActionsForAreaTrigger(areaId, QuestTriggerType.EXIT_AREA).size() + " line(s)",
                "§8Click to edit"
        )));

        inv.setItem(22, button(Material.BOOK, "§bReload scripts", List.of(
                "§7Reload questbuilder-triggers.yml",
                "§8Click to reload"
        )));

        player.openInventory(inv);
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }
}
