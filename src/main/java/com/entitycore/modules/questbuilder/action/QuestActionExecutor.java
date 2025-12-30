package com.entitycore.modules.questbuilder.action;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class QuestActionExecutor {

    public static void execute(Player player, List<String> commands) {
        for (String raw : commands) {
            String cmd = raw.replace("{player}", player.getName());

            if (cmd.startsWith("player:")) {
                player.performCommand(cmd.substring(7));
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }
}
