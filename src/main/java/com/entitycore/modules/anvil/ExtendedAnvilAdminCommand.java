package com.entitycore.modules.anvil;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class ExtendedAnvilAdminCommand implements CommandExecutor, TabCompleter {

    private final ExtendedAnvilAdminMenu menu;

    public ExtendedAnvilAdminCommand(ExtendedAnvilAdminMenu menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            return true;
        }

        menu.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
