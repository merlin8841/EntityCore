package com.entitycore.modules.extendedanvil;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /eaadmin opens Operator settings GUI.
 *
 * Operator-only (per EntityCore permission tier rules).
 */
public final class ExtendedAnvilAdminCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "entitycore.extendedanvil.admin";

    private final ExtendedAnvilAdminMainGui gui;

    public ExtendedAnvilAdminCommand(ExtendedAnvilAdminMainGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used in-game.");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (gui == null) {
            player.sendMessage(ChatColor.RED + "ExtendedAnvil admin GUI is not available (module not initialized).");
            return true;
        }

        try {
            gui.open(player);
        } catch (Throwable t) {
            player.sendMessage(ChatColor.RED + "Failed to open ExtendedAnvil admin GUI. Check console for details.");
            t.printStackTrace();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}