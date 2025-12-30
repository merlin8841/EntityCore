package com.entitycore.modules.questbuilder.action;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class QuestActionExecutor {

    private QuestActionExecutor() {}

    /**
     * Action formats (portable):
     * - "player:<command>"   -> run as player
     * - "console:<command>"  -> run as console
     * - "bq:<eventId>"       -> runs: /q event <player> <eventId> (BetonQuest via command)
     *
     * Placeholders:
     * - {player}
     */
    public static void execute(Player player, List<String> actions) {
        for (String raw : actions) {
            if (raw == null || raw.isBlank()) continue;

            String line = raw.replace("{player}", player.getName()).trim();

            if (line.startsWith("player:")) {
                player.performCommand(line.substring("player:".length()).trim());
                continue;
            }

            if (line.startsWith("console:")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.substring("console:".length()).trim());
                continue;
            }

            if (line.startsWith("bq:")) {
                String eventId = line.substring("bq:".length()).trim();
                if (!eventId.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "q event " + player.getName() + " " + eventId);
                }
                continue;
            }

            // Default: console command
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
    }
}
