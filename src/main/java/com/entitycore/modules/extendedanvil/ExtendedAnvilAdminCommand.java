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
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }

        sessions.openAdminMenu(player);
        return true;
    }
}
