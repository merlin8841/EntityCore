package com.entitycore.modules.questbuilder;

import com.entitycore.module.Module;
import com.entitycore.modules.questbuilder.command.QuestBuilderCommand;
import com.entitycore.modules.questbuilder.command.QuestTriggerCommand;
import com.entitycore.modules.questbuilder.editor.QuestBuilderEditorListener;
import com.entitycore.modules.questbuilder.listener.QuestBuilderListener;
import com.entitycore.modules.questbuilder.listener.QuestInteractTriggerListener;
import com.entitycore.modules.questbuilder.listener.QuestTriggerListener;
import com.entitycore.modules.questbuilder.script.QuestScriptRegistry;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import com.entitycore.modules.questbuilder.trigger.QuestTriggerEngine;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderModule implements Module {

    private QuestStorage storage;
    private QuestScriptRegistry scripts;

    @Override
    public String getName() {
        return "QuestBuilder";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        QuestBuilderKeys.init(plugin);

        this.storage = new QuestStorage(plugin);
        this.scripts = new QuestScriptRegistry(plugin);

        QuestTriggerEngine triggerEngine = new QuestTriggerEngine(storage);

        // Commands
        plugin.getCommand("qb").setExecutor(new QuestBuilderCommand(plugin, storage));
        plugin.getCommand("entitycore-qb-trigger").setExecutor(new QuestTriggerCommand(storage, scripts));

        // Listeners
        Bukkit.getPluginManager().registerEvents(new QuestBuilderListener(plugin, storage, scripts), plugin);
        Bukkit.getPluginManager().registerEvents(new QuestTriggerListener(triggerEngine), plugin);
        Bukkit.getPluginManager().registerEvents(new QuestInteractTriggerListener(storage, scripts), plugin);

        // Popup editor listener
        Bukkit.getPluginManager().registerEvents(new QuestBuilderEditorListener(scripts), plugin);

        plugin.getLogger().info("[QuestBuilder] Enabled.");
    }

    @Override
    public void disable() {
        if (storage != null) storage.save();
        if (scripts != null) scripts.save();
    }
}
