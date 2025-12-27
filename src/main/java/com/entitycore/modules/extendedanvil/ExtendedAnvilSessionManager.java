package com.entitycore.modules.extendedanvil;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExtendedAnvilSessionManager {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;
    private final EnchantCostService costService;
    private final XpRefundService refundService;

    // Track open player GUIs
    private final Map<UUID, Inventory> openMenus = new HashMap<>();

    public ExtendedAnvilSessionManager(JavaPlugin plugin,
                                      ExtendedAnvilConfig config,
                                      XpRefundService refundService,
                                      EnchantCostService costService) {
        this.plugin = plugin;
        this.config = config;
        this.refundService = refundService;
        this.costService = costService;
    }

    /* =====================
       Accessors
       ===================== */

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public ExtendedAnvilConfig getConfig() {
        return config;
    }

    public EnchantCostService getCostService() {
        return costService;
    }

    public XpRefundService getRefundService() {
        return refundService;
    }

    /* =====================
       Player GUI
       ===================== */

    public void openPlayerMenu(Player player) {
        Inventory inv = PlayerMenu.create(player);
        openMenus.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public boolean isPlayerMenu(Player player, Inventory inv) {
        return openMenus.get(player.getUniqueId()) == inv;
    }

    public void close(Player player) {
        openMenus.remove(player.getUniqueId());
    }
}
