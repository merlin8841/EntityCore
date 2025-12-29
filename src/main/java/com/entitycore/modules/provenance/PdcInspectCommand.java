package com.entitycore.modules.provenance;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class PdcInspectCommand implements CommandExecutor {

    private final ProvenanceService service;

    public PdcInspectCommand(ProvenanceService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!player.hasPermission("entitycore.pdc") && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        // Bedrock/Floodgate safety: try main hand, then offhand
        ItemStack it = player.getInventory().getItemInMainHand();
        if (it == null || it.getType() == Material.AIR) {
            it = player.getInventory().getItemInOffHand();
        }

        if (it == null || it.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must be holding an item (main hand or offhand).");
            return true;
        }

        boolean allKeys = false;
        if (args.length >= 1) {
            String mode = args[0].trim().toLowerCase();
            allKeys = mode.equals("all") || mode.equals("*");
        }

        player.sendMessage(ChatColor.GRAY + "---- " + ChatColor.YELLOW + "PDC Inspect" + ChatColor.GRAY + " ----");
        player.sendMessage(ChatColor.GRAY + "Item: " + ChatColor.WHITE + it.getType()
                + ChatColor.GRAY + " x" + ChatColor.WHITE + it.getAmount());
        player.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + (allKeys ? "ALL KEYS" : "ENTITYCORE ONLY"));

        player.sendMessage(ChatColor.GRAY + "Unnatural: " + (service.isUnnatural(it) ? ChatColor.RED + "YES" : ChatColor.GREEN + "NO"));

        String src = service.getSource(it);
        player.sendMessage(ChatColor.GRAY + "Source: " + ChatColor.WHITE + (src == null ? "(none)" : src));

        var stamp = service.getStamp(it);
        player.sendMessage(ChatColor.GRAY + "Stamp: " + ChatColor.WHITE + (stamp == null ? "(none)" : stamp.toString()));

        var parent = service.getParentStamp(it);
        player.sendMessage(ChatColor.GRAY + "Parent: " + ChatColor.WHITE + (parent == null ? "(none)" : parent.toString()));

        List<String> dump = service.dumpPdc(it, allKeys);
        for (String line : dump) {
            player.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + line);
        }

        return true;
    }
}
