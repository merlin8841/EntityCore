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
 *  - /ea (permission: entitycore.extendedanvil.use) [requires looking at an anvil]
 * Operator GUI:
 *  - /eaadmin (permission: entitycore.extendedanvil.admin)
 */
public final class ExtendedAnvilGuiModule implements Module {

    private JavaPlugin plugin;

    private ExtendedAnvilConfig config;
    private ExtendedAnvilService service;

    private ExtendedAnvilGui playerGui;
    private ExtendedAnvilAdminMainGui adminMainGui;
    private ExtendedAnvilRefundGui refundGui;
    private ExtendedAnvilApplyCostGui applyCostGui;
    private ExtendedAnvilGeneralGui generalGui;
    private ExtendedAnvilPriorityGui priorityGui;
    private ExtendedAnvilEnchantCostGui costGui;
    private ExtendedAnvilEnchantCapGui capGui;

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

        this.adminMainGui = new ExtendedAnvilAdminMainGui(config);
        this.refundGui = new ExtendedAnvilRefundGui(config);
        this.applyCostGui = new ExtendedAnvilApplyCostGui(config);
        this.generalGui = new ExtendedAnvilGeneralGui(config);
        this.priorityGui = new ExtendedAnvilPriorityGui(plugin, config);
        this.costGui = new ExtendedAnvilEnchantCostGui(config);
        this.capGui = new ExtendedAnvilEnchantCapGui(config);
        this.playerGui = new ExtendedAnvilGui(plugin, config, service);

        this.listener = new ExtendedAnvilListener(plugin, config, service,
                playerGui, adminMainGui, refundGui, applyCostGui, generalGui,
                priorityGui, costGui, capGui);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.playerCommand = new ExtendedAnvilCommand(playerGui);
        this.adminCommand = new ExtendedAnvilAdminCommand(adminMainGui);

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
                + config.getRefundPercentLast() + "%"
                + ", fallbackRefundLvlsPerEnchantLvl=" + config.getRefundLevelsPerEnchantLevel()
                + ", allowCurseRemoval=" + config.isAllowCurseRemoval()
                + ", debug=" + config.isDebug()
                + ", priorWork(cost/inc)=" + config.getPriorWorkCostPerStep() + "/" + config.getPriorWorkIncrementPerApply()
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
        adminMainGui = null;
        refundGui = null;
        applyCostGui = null;
        generalGui = null;
        priorityGui = null;
        costGui = null;
        capGui = null;
        listener = null;
        playerCommand = null;
        adminCommand = null;
    }
}
