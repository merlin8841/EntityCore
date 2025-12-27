package com.entitycore.modules.extendedanvil;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class ExtendedAnvilAdminCommand implements CommandExecutor, TabCompleter {

    private final ExtendedAnvilSessionManager sessions;

    public ExtendedAnvilAdminCommand(ExtendedAnvilSessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cOperator only.");
            return true;
        }

        sessions.openAdminMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
