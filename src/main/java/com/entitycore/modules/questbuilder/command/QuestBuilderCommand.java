package com.entitycore.modules.questbuilder.command;

import com.entitycore.modules.questbuilder.QuestBuilderTool;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class QuestBuilderCommand implements CommandExecutor {

    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    @SuppressWarnings("unused")
    private final QuestStorage storage;

    public QuestBuilderCommand(JavaPlugin plugin, QuestStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("entitycore.questbuilder.operator")) {
            player.sendMessage("No permission.");
            return true;
        }

        QuestBuilderTool.give(player);
        player.sendMessage("§aQuest Builder tool given.");
        return true;
    }
}
