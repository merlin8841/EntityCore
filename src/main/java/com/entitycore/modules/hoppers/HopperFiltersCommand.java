package com.entitycore.modules.hoppers;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.List;

public final class HopperFiltersCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final HopperFiltersListener listener;

    public static final String PERM_USE = "entitycore.hopperfilters.use";

    public HopperFiltersCommand(JavaPlugin plugin, HopperFiltersListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission(PERM_USE)) {
            p.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }

        Block target = getTargetBlock(p, 6.0);
        if (target == null || !listener.isHopperBlock(target)) {
            p.sendMessage(ChatColor.RED + "Look directly at a hopper (within 6 blocks).");
            return true;
        }

        listener.openFilterGui(p, target);
        return true;
    }

    private Block getTargetBlock(Player p, double range) {
        RayTraceResult r = p.rayTraceBlocks(range);
        if (r == null) return null;
        return r.getHitBlock();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
