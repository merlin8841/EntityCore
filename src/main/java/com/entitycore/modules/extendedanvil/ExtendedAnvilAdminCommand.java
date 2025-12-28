package com.entitycore.modules.extendedanvil;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilAdminCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;
    private final ExtendedAnvilService service;

    public ExtendedAnvilAdminCommand(JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("No permission.");
            return true;
        }

        ExtendedAnvilAdminGui.open(player, plugin, config, service);
        return true;
    }
}
