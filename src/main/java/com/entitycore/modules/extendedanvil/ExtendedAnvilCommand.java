package com.entitycore.modules.extendedanvil;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class ExtendedAnvilCommand implements CommandExecutor, TabCompleter {

    private final ExtendedAnvilSessionManager sessions;

    public ExtendedAnvilCommand(ExtendedAnvilSessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("entitycore.extendedanvil.use")) {
            player.sendMessage("§cYou don't have permission to use Extended Anvil.");
            return true;
        }

        // Must be looking at an anvil (Bedrock-friendly gating)
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§eLook at an anvil within 5 blocks.");
            return true;
        }

        Material t = target.getType();
        if (t != Material.ANVIL && t != Material.CHIPPED_ANVIL && t != Material.DAMAGED_ANVIL) {
            player.sendMessage("§eLook at an anvil within 5 blocks.");
            return true;
        }

        sessions.openPlayerMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
