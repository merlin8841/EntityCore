package com.entitycore.modules.extendedanvil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ExtendedAnvilAdminCommand implements CommandExecutor {

    private final ExtendedAnvilSessionManager sessions;

    public ExtendedAnvilAdminCommand(ExtendedAnvilSessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cYou do not have permission to use this.");
            return true;
        }

        // IMPORTANT:
        // AdminMenu is static / singleton by design.
        // Do NOT instantiate it.
        AdminMenu.open(player);

        return true;
    }
}
