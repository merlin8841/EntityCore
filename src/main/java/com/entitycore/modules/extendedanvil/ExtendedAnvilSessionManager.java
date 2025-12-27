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
    private final XpRefundService refundService;
    private final EnchantCostService costService;

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

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void openPlayerMenu(Player player) {
        Inventory inv = PlayerMenu.create(player);
        openMenus.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        refresh(player, inv);
    }

    public boolean isPlayerMenu(Player player, Inventory inv) {
        return inv != null && openMenus.get(player.getUniqueId()) == inv;
    }

    public void close(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    public void refresh(Player player, Inventory inv) {
        PlayerMenu.refreshPreview(player, inv, config, costService);
    }

    public void completeCraft(Player player, Inventory inv) {
        if (inv == null) return;

        ItemStack result = inv.getItem(PlayerMenu.SLOT_RESULT);
        if (result == null || result.getType() == Material.AIR) return;

        int cost = PlayerMenu.getLastCost(inv);
        if (cost < 0) cost = 0;

        if (player.getLevel() < cost) {
            player.sendMessage("§cYou need §f" + cost + "§c levels.");
            return;
        }

        // Deduct levels
        player.setLevel(player.getLevel() - cost);

        // Give result
        player.getInventory().addItem(result.clone());

        // Consume inputs (simple v1 behavior)
        inv.setItem(PlayerMenu.SLOT_ITEM, null);
        inv.setItem(PlayerMenu.SLOT_BOOK, null);
        inv.setItem(PlayerMenu.SLOT_RESULT, null);

        // Re-decorate + refresh
        PlayerMenu.decorate(inv);
        refresh(player, inv);
    }

    public void openAdminMenu(Player player) {
        AdminMenu.open(player);
    }

    public void openCapsMenu(Player player) {
        CapsMenu.open(player, config);
    }

    public void openPriorityMenu(Player player) {
        PriorityMenu.open(player, config);
    }
}
