package com.entitycore.modules.questbuilder.command;

import com.entitycore.modules.questbuilder.action.QuestActionExecutor;
import com.entitycore.modules.questbuilder.storage.QuestStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public final class QuestTriggerCommand implements CommandExecutor {

    @SuppressWarnings("unused")
    private final QuestStorage storage;

    public QuestTriggerCommand(QuestStorage storage) {
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // args: <areaId> <triggerType> <playerName>
        if (args.length < 3) return true;

        Player player = Bukkit.getPlayerExact(args[2]);
        if (player == null) return true;

        // Placeholder actions for now (Script Builder GUI will define real actions in YAML)
        QuestActionExecutor.execute(player, List.of(
                "player:say §7[QB] Trigger fired: " + args[0] + " " + args[1]
        ));

        return true;
    }
}
