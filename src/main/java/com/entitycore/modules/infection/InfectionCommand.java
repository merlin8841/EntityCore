package com.entitycore.modules.infection;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InfectionCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final InfectionConfig config;
    private final InfectionService service;

    public InfectionCommand(JavaPlugin plugin, InfectionConfig config, InfectionService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!p.hasPermission("entitycore.infection.admin")) {
            p.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            InfectionAdminGui.open(p, plugin, config, service);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gui" -> {
                InfectionAdminGui.open(p, plugin, config, service);
                return true;
            }
            case "give" -> {
                p.getInventory().addItem(InfectionSeedItem.create(plugin, config));
                p.sendMessage(ChatColor.GREEN + "Given Infection Seed.");
                return true;
            }
            case "reload" -> {
                config.reload();
                service.restartSpreadTask(); // cycle ticks could have changed
                p.sendMessage(ChatColor.GREEN + "Reloaded infection config.");
                return true;
            }
            default -> {
                p.sendMessage(ChatColor.YELLOW + "Usage:");
                p.sendMessage(ChatColor.GRAY + "/infect  " + ChatColor.DARK_GRAY + "(opens GUI)");
                p.sendMessage(ChatColor.GRAY + "/infect give");
                p.sendMessage(ChatColor.GRAY + "/infect reload");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("gui");
            out.add("give");
            out.add("reload");
        }
        return out;
    }
}
