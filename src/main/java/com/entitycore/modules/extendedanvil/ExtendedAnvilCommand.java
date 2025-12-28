package com.entitycore.modules.extendedanvil;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;
    private final ExtendedAnvilService service;

    public ExtendedAnvilCommand(JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
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

        if (!player.hasPermission("entitycore.extendedanvil.use")) {
            player.sendMessage("No permission.");
            return true;
        }

        Block target = getTargetBlock(player);
        if (target == null || !isAnvil(target.getType())) {
            player.sendMessage("You must be looking at an anvil to use /ea.");
            return true;
        }

        ExtendedAnvilGui.open(player, plugin, config, service);
        return true;
    }

    private static Block getTargetBlock(Player player) {
        RayTraceResult r = player.rayTraceBlocks(5.0);
        if (r == null) return null;
        return r.getHitBlock();
    }

    private static boolean isAnvil(Material m) {
        return m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL;
    }
}
