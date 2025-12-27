package com.entitycore.modules.extendedanvil;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Extended Anvil (chest GUI) module.
 *
 * Player GUI:
 *  - /ea (permission: entitycore.extendedanvil.use) [requires looking at an anvil via command handler]
 * Operator GUI:
 *  - /eaadmin (permission: entitycore.extendedanvil.admin)
 */
public final class ExtendedAnvilGuiModule implements Module {

    private JavaPlugin plugin;

    private ExtendedAnvilConfig config;
    private ExtendedAnvilService service;

    private ExtendedAnvilGui playerGui;
    private ExtendedAnvilAdminGui adminGui;
    private ExtendedAnvilPriorityGui priorityGui;

    private ExtendedAnvilListener listener;

    private ExtendedAnvilCommand playerCommand;
    private ExtendedAnvilAdminCommand adminCommand;

    @Override
    public String getName() {
        return "ExtendedAnvil";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        this.plugin = plugin;

        this.config = new ExtendedAnvilConfig(plugin);
        this.config.load();

        this.service = new ExtendedAnvilService(plugin, config);

        this.playerGui = new ExtendedAnvilGui(plugin, config, service);
        this.adminGui = new ExtendedAnvilAdminGui(plugin, config);
        this.priorityGui = new ExtendedAnvilPriorityGui(plugin, config);

        this.listener = new ExtendedAnvilListener(plugin, config, service, playerGui, adminGui, priorityGui);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.playerCommand = new ExtendedAnvilCommand(playerGui);
        this.adminCommand = new ExtendedAnvilAdminCommand(adminGui);

        PluginCommand ea = plugin.getCommand("ea");
        if (ea != null) {
            ea.setExecutor(playerCommand);
            ea.setTabCompleter(playerCommand);
        } else {
            plugin.getLogger().warning("Command 'ea' missing from plugin.yml (ExtendedAnvilGuiModule).");
        }

        PluginCommand eaAdmin = plugin.getCommand("eaadmin");
        if (eaAdmin != null) {
            eaAdmin.setExecutor(adminCommand);
            eaAdmin.setTabCompleter(adminCommand);
        } else {
            plugin.getLogger().warning("Command 'eaadmin' missing from plugin.yml (ExtendedAnvilGuiModule).");
        }

        plugin.getLogger().info("[ExtendedAnvil] Enabled"
                + " refund=" + config.getRefundPercentFirst() + "%/"
                + config.getRefundPercentSecond() + "%/"
                + config.getRefundPercentLater() + "%"
                + ", refundLvlsPerEnchantLvl=" + config.getRefundLevelsPerEnchantLevel()
                + ", allowCurseRemoval=" + config.isAllowCurseRemoval()
                + ", applyCost(base/perEnchant/perStoredLvl)="
                + config.getApplyCostBaseLevels() + "/"
                + config.getApplyCostPerEnchant() + "/"
                + config.getApplyCostPerStoredLevel()
        );
    }

    @Override
    public void disable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }

        plugin = null;
        config = null;
        service = null;
        playerGui = null;
        adminGui = null;
        priorityGui = null;
        listener = null;
        playerCommand = null;
        adminCommand = null;
    }
}
