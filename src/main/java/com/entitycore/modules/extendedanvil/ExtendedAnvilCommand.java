package com.entitycore.modules.extendedanvil;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** /ea opens player GUI, but only while looking at an anvil. */
public final class ExtendedAnvilCommand implements CommandExecutor, TabCompleter {

    private final ExtendedAnvilGui gui;

    public ExtendedAnvilCommand(ExtendedAnvilGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("entitycore.extendedanvil.use")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        // Require looking at an anvil block (vanilla feel)
        Block target = player.getTargetBlockExact(5);
        if (target == null || !isAnvil(target.getType())) {
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RED
                    + "You must be looking at an anvil to use /ea.");
            return true;
        }

        gui.open(player);
        return true;
    }

    private boolean isAnvil(Material mat) {
        return mat == Material.ANVIL
                || mat == Material.CHIPPED_ANVIL
                || mat == Material.DAMAGED_ANVIL;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
