package com.entitycore.modules.questbuilder.action;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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
        if (player == null || actions == null || actions.isEmpty()) return;

        CommandSender console = Bukkit.getConsoleSender();

        for (String raw : actions) {
            if (raw == null) continue;

            String line = raw.replace("{player}", player.getName()).trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("player:")) {
                String cmd = normalizeCommand(line.substring("player:".length()));
                if (!cmd.isEmpty()) player.performCommand(cmd);
                continue;
            }

            if (line.startsWith("console:")) {
                String cmd = normalizeCommand(line.substring("console:".length()));
                if (!cmd.isEmpty()) Bukkit.dispatchCommand(console, cmd);
                continue;
            }

            if (line.startsWith("bq:")) {
                String eventId = line.substring("bq:".length()).trim();
                if (!eventId.isEmpty()) {
                    // BetonQuest 2.2.1 supports /q event <player> <eventId> (as you’re using)
                    Bukkit.dispatchCommand(console, "q event " + player.getName() + " " + eventId);
                }
                continue;
            }

            // Default: console command
            String cmd = normalizeCommand(line);
            if (!cmd.isEmpty()) Bukkit.dispatchCommand(console, cmd);
        }
    }

    private static String normalizeCommand(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (t.startsWith("/")) t = t.substring(1).trim();
        return t;
    }
}
