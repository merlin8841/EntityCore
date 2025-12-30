package com.entitycore.modules.questbuilder.command;

import com.entitycore.modules.questbuilder.action.QuestActionExecutor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public final class QuestTriggerCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 3) return true;

        Player player = Bukkit.getPlayer(args[2]);
        if (player == null) return true;

        // placeholder actions – script builder will populate later
        QuestActionExecutor.execute(player, List.of(
                "player:say Trigger fired: " + args[0] + " " + args[1]
        ));

        return true;
    }
}
