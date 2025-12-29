package com.entitycore.modules.provenance;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class ProvCommand implements CommandExecutor {

    private final ProvenanceService service;

    public ProvCommand(ProvenanceService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!player.hasPermission("entitycore.provenance.op") && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        ItemStack it = player.getInventory().getItemInMainHand();
        if (it == null || it.getType() == Material.AIR) {
            it = player.getInventory().getItemInOffHand();
        }
        if (it == null || it.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Hold an item (main hand or offhand).");
            return true;
        }

        String source = (args.length >= 2) ? args[1] : "manual:/prov";

        switch (sub) {
            case "markunnatural" -> {
                ItemStack tagged = service.tagUnnatural(it, source, null);
                setHeld(player, it, tagged);
                player.sendMessage(ChatColor.GREEN + "Marked item UNNATURAL. source=" + source);
            }
            case "marknatural" -> {
                ItemStack cleared = service.clearProvenance(it);
                setHeld(player, it, cleared);
                player.sendMessage(ChatColor.GREEN + "Cleared provenance tags (NATURAL).");
            }
            case "restamp" -> {
                UUID parent = service.getParentStamp(it); // keep if present
                ItemStack retagged = service.tagUnnatural(it, source, parent);
                setHeld(player, it, retagged);
                player.sendMessage(ChatColor.GREEN + "Restamped item UNNATURAL. source=" + source);
            }
            default -> {
                sendUsage(player);
            }
        }

        return true;
    }

    private static void setHeld(Player player, ItemStack oldHeld, ItemStack newHeld) {
        // Try to put back into the same hand the player used
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.isSimilar(oldHeld)) {
            player.getInventory().setItemInMainHand(newHeld);
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.isSimilar(oldHeld)) {
            player.getInventory().setItemInOffHand(newHeld);
            return;
        }

        // Fallback: main hand
        player.getInventory().setItemInMainHand(newHeld);
    }

    private static void sendUsage(Player p) {
        p.sendMessage(ChatColor.YELLOW + "Usage:");
        p.sendMessage(ChatColor.GRAY + "/prov markunnatural [source]");
        p.sendMessage(ChatColor.GRAY + "/prov marknatural");
        p.sendMessage(ChatColor.GRAY + "/prov restamp [source]");
    }
}
