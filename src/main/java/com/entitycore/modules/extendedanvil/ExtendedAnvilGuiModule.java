package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilGuiModule {

    private final JavaPlugin plugin;

    public ExtendedAnvilGuiModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        ExtendedAnvilConfig config = new ExtendedAnvilConfig(plugin);
        EnchantCostService costService = new EnchantCostService();
        XpRefundService refundService = new XpRefundService(config);

        ExtendedAnvilSessionManager sessions =
                new ExtendedAnvilSessionManager(plugin, config, costService, refundService);

        Bukkit.getPluginManager().registerEvents(
                new ExtendedAnvilListener(sessions), plugin
        );

        ExtendedAnvilCommand playerCmd = new ExtendedAnvilCommand(sessions);
        ExtendedAnvilAdminCommand adminCmd = new ExtendedAnvilAdminCommand(sessions);

        plugin.getCommand("ea").setExecutor(playerCmd);
        plugin.getCommand("eaadmin").setExecutor(adminCmd);
    }
}
