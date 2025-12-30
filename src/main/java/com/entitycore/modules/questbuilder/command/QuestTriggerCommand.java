package com.entitycore.modules.questbuilder.command;

import com.entitycore.modules.questbuilder.action.QuestActionExecutor;
import com.entitycore.modules.questbuilder.script.QuestScriptRegistry;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import com.entitycore.modules.questbuilder.trigger.QbTriggerType;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public final class QuestTriggerCommand implements CommandExecutor {

    private final QuestStorage storage;
    private final QuestScriptRegistry scripts;

    public QuestTriggerCommand(QuestStorage storage, QuestScriptRegistry scripts) {
        this.storage = storage;
        this.scripts = scripts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // args: <areaId> <triggerType> <playerName>
        if (args.length < 3) return true;

        String areaId = args[0];
        QbTriggerType type = QbTriggerType.from(args[1]);
        if (type == null) return true;

        Player player = Bukkit.getPlayerExact(args[2]);
        if (player == null) return true;

        // Execute YAML-defined actions for this trigger
        List<String> actions = scripts.getActionsForAreaTrigger(areaId, type);
        if (actions.isEmpty()) return true;

        QuestActionExecutor.execute(player, actions);
        return true;
    }
}
