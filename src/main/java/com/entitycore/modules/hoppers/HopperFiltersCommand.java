package com.entitycore.modules.hoppers;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public final class HopperFiltersCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final HopperFiltersMenu menu;

    public HopperFiltersCommand(JavaPlugin plugin, HopperFiltersMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("entitycore.hopperfilters.use")) {
            player.sendMessage("§cYou don't have permission to use hopper filters.");
            return true;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.HOPPER) {
            player.sendMessage("§eLook at a hopper within 5 blocks.");
            return true;
        }

        menu.open(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
