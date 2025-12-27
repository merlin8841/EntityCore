package com.entitycore.modules.extendedanvil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ExtendedAnvilCommand implements CommandExecutor {

    private final ExtendedAnvilSessionManager sessions;

    public ExtendedAnvilCommand(ExtendedAnvilSessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (!player.hasPermission("entitycore.extendedanvil.use")) {
            player.sendMessage("§cYou do not have permission to use Extended Anvil.");
            return true;
        }

        sessions.open(player);
        return true;
    }
}
