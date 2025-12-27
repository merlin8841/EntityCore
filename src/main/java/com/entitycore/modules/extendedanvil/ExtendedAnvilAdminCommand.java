package com.entitycore.modules.extendedanvil;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** /eaadmin opens Operator settings GUI. */
public final class ExtendedAnvilAdminCommand implements CommandExecutor, TabCompleter {

    private final ExtendedAnvilAdminGui gui;

    public ExtendedAnvilAdminCommand(ExtendedAnvilAdminGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        gui.open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
