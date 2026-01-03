package com.entitycore.modules.questbuilder;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderKeys {

    private QuestBuilderKeys() {}

    public static NamespacedKey TOOL;
    public static NamespacedKey MODE;
    public static NamespacedKey DRAFT;

    // AREA_SET alternation: 1 or 2
    public static NamespacedKey AREA_NEXT;

    // Preview border toggle: 1 = on, 0 = off (kept for future menu toggles)
    public static NamespacedKey PREVIEW_BORDER;

    // Resizing: selected corner (0 none, 1..4)
    public static NamespacedKey AREA_CORNER;

    public static void init(JavaPlugin plugin) {
        TOOL = new NamespacedKey(plugin, "qb_tool");
        MODE = new NamespacedKey(plugin, "qb_mode");
        DRAFT = new NamespacedKey(plugin, "qb_draft");

        AREA_NEXT = new NamespacedKey(plugin, "qb_area_next");
        PREVIEW_BORDER = new NamespacedKey(plugin, "qb_preview_border");
        AREA_CORNER = new NamespacedKey(plugin, "qb_area_corner");
    }
}
