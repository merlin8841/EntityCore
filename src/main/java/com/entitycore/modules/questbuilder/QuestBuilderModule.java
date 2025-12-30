package com.entitycore.modules.questbuilder;

import com.entitycore.modules.questbuilder.listener.QuestBuilderListener;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderModule {

    private final JavaPlugin plugin;
    private QuestStorage storage;

    public QuestBuilderModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        QuestBuilderKeys.init(plugin);

        storage = new QuestStorage(plugin);

        Bukkit.getPluginManager().registerEvents(
                new QuestBuilderListener(plugin, storage),
                plugin
        );

        plugin.getCommand("qb").setExecutor(
                new QuestBuilderCommand(plugin, storage)
        );
    }
}
