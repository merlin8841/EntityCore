package com.entitycore.modules.questbuilder.adapter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BetonQuestAdapter {

    public static void runEvent(Player player, String eventId) {
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "q event " + player.getName() + " " + eventId
        );
    }
}
