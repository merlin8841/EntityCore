package com.entitycore.modules.extendedanvil;

import com.entitycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilGuiModule implements Module {

    private JavaPlugin plugin;

    private ExtendedAnvilSessionManager sessions;
    private ExtendedAnvilListener listener;

    private ExtendedAnvilCommand playerCmd;
    private ExtendedAnvilAdminCommand adminCmd;

    // Optional: allow your EntityCore.java to still do new ExtendedAnvilGuiModule(this)
    public ExtendedAnvilGuiModule() {}

    public ExtendedAnvilGuiModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "ExtendedAnvilGUI";
    }

    @Override
    public void enable(JavaPlugin plugin) {
        if (this.plugin == null) this.plugin = plugin;

        ExtendedAnvilConfig config = new ExtendedAnvilConfig(this.plugin);
        EnchantCostService costService = new EnchantCostService();
        XpRefundService refundService = new XpRefundService(config);

        // IMPORTANT: constructor order matches the SessionManager constructor below
        this.sessions = new ExtendedAnvilSessionManager(this.plugin, config, refundService, costService);

        this.listener = new ExtendedAnvilListener(sessions);
        Bukkit.getPluginManager().registerEvents(listener, this.plugin);

        this.playerCmd = new ExtendedAnvilCommand(sessions);
        this.adminCmd = new ExtendedAnvilAdminCommand(sessions);

        PluginCommand ea = this.plugin.getCommand("ea");
        if (ea != null) ea.setExecutor(playerCmd);
        else this.plugin.getLogger().warning("Command 'ea' missing from plugin.yml");

        PluginCommand eaAdmin = this.plugin.getCommand("eaadmin");
        if (eaAdmin != null) eaAdmin.setExecutor(adminCmd);
        else this.plugin.getLogger().warning("Command 'eaadmin' missing from plugin.yml");

        this.plugin.getLogger().info("[ExtendedAnvil] Enabled (Chest GUI).");
    }

    @Override
    public void disable() {
        if (listener != null) HandlerList.unregisterAll(listener);

        sessions = null;
        listener = null;
        playerCmd = null;
        adminCmd = null;
        plugin = null;
    }
}
