package com.entitycore.modules.extendedanvil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExtendedAnvilSessionManager {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;
    private final EnchantCostService costService;
    private final XpRefundService refundService;

    private final Map<UUID, Inventory> openMenus = new HashMap<>();

    public ExtendedAnvilSessionManager(
            JavaPlugin plugin,
            ExtendedAnvilConfig config,
            EnchantCostService costService,
            XpRefundService refundService
    ) {
        this.plugin = plugin;
        this.config = config;
        this.costService = costService;
        this.refundService = refundService;
    }

    /* ================= PLAYER ================= */

    public void openPlayerMenu(Player player) {
        Inventory inv = PlayerMenu.create(player);
        openMenus.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public boolean isPlayerMenu(Player player, Inventory inv) {
        return inv != null && openMenus.get(player.getUniqueId()) == inv;
    }

    public void close(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    /* ================= ACTIONS ================= */

    public void refresh(Player player, Inventory inv) {
        PlayerMenu.refreshPreview(player, inv, config, costService);
    }

    public void completeCraft(Player player, Inventory inv) {
        ItemStack result = inv.getItem(PlayerMenu.SLOT_RESULT);
        if (result == null || result.getType() == Material.AIR) return;

        int cost = PlayerMenu.getLastCost(inv);
        if (player.getLevel() < cost) return;

        player.setLevel(player.getLevel() - cost);
        player.getInventory().addItem(result.clone());

        inv.clear();
        PlayerMenu.decorate(inv);
    }

    /* ================= ADMIN ================= */

    public void openAdminMenu(Player player) {
        AdminMenu.open(player);
    }

    public void openCapsMenu(Player player) {
        CapsMenu.open(player, config);
    }

    public void openPriorityMenu(Player player) {
        PriorityMenu.open(player, config);
    }
    public JavaPlugin getPlugin() {
    return plugin;
}
