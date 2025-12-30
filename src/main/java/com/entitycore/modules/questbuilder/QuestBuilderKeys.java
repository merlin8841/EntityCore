package com.entitycore.modules.questbuilder;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderKeys {

    private QuestBuilderKeys() {}

    public static NamespacedKey TOOL;
    public static NamespacedKey MODE;
    public static NamespacedKey DRAFT;
    public static NamespacedKey POINT_INDEX;

    public static void init(JavaPlugin plugin) {
        TOOL = new NamespacedKey(plugin, "qb_tool");
        MODE = new NamespacedKey(plugin, "qb_mode");
        DRAFT = new NamespacedKey(plugin, "qb_draft");
        POINT_INDEX = new NamespacedKey(plugin, "qb_point_index");
    }
}
