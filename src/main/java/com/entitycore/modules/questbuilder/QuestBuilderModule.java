package com.entitycore.modules.questbuilder;

import com.entitycore.module.Module;
import com.entitycore.modules.questbuilder.command.QuestBuilderCommand;
import com.entitycore.modules.questbuilder.command.QuestTriggerCommand;
import com.entitycore.modules.questbuilder.listener.QuestBuilderListener;
import com.entitycore.modules.questbuilder.listener.QuestTriggerListener;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import com.entitycore.modules.questbuilder.trigger.QuestTriggerEngine;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderModule implements Module {

    private QuestStorage storage;

    @Override
    public String getName() {
        return "QuestBuilder";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        QuestBuilderKeys.init(plugin);

        this.storage = new QuestStorage(plugin);

        QuestTriggerEngine triggerEngine = new QuestTriggerEngine(storage);

        // Commands
        plugin.getCommand("qb").setExecutor(new QuestBuilderCommand(plugin, storage));
        plugin.getCommand("entitycore-qb-trigger").setExecutor(new QuestTriggerCommand(storage));

        // Listeners
        Bukkit.getPluginManager().registerEvents(
                new QuestBuilderListener(plugin, storage),
                plugin
        );
        Bukkit.getPluginManager().registerEvents(
                new QuestTriggerListener(triggerEngine),
                plugin
        );

        plugin.getLogger().info("[QuestBuilder] Enabled.");
    }

    @Override
    public void disable() {
        if (storage != null) {
            storage.save();
        }
    }
}
