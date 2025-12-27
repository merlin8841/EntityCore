package com.entitycore.modules.extendedanvil;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilModule implements Module {

    private ExtendedAnvilSessionManager sessions;

    @Override
    public String getName() {
        return "ExtendedAnvil";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        ExtendedAnvilConfig config = new ExtendedAnvilConfig(plugin);
        EnchantCostService costService = new EnchantCostService(config);
        XpRefundService refundService = new XpRefundService(plugin);

        this.sessions = new ExtendedAnvilSessionManager(
                plugin,
                config,
                costService,
                refundService
        );

        Bukkit.getPluginManager().registerEvents(
                new ExtendedAnvilListener(sessions),
                plugin
        );

        plugin.getCommand("ea").setExecutor(
                new ExtendedAnvilCommand(sessions)
        );

        plugin.getLogger().info("[ExtendedAnvil] Chest-based anvil enabled.");
    }

    @Override
    public void disable() {
        if (sessions != null) {
            sessions.shutdown();
        }
    }
}
