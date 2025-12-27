package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilSessionManager {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig config;
    private final XpRefundService refundService;
    private final EnchantCostService costService;

    private final Map<UUID, GuiType> openGui = new HashMap<>();

    // Track Caps page per player
    private final Map<UUID, Integer> capsPage = new HashMap<>();

    public ExtendedAnvilSessionManager(JavaPlugin plugin,
                                      ExtendedAnvilConfig config,
                                      XpRefundService refundService,
                                      EnchantCostService costService) {
        this.plugin = plugin;
        this.config = config;
        this.refundService = refundService;
        this.costService = costService;
    }

    /* =========================================================
       OPEN MENUS
       ========================================================= */

    public void openPlayerMenu(Player player) {
        Inventory inv = PlayerMenu.create(player);
        openGui.put(player.getUniqueId(), GuiType.PLAYER);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.0f);

        Bukkit.getScheduler().runTask(plugin, () ->
                PlayerMenu.refreshPreview(player, inv, plugin, config, costService)
        );
    }

    public void openAdminMenu(Player player) {
        Inventory inv = AdminMenu.create(player, plugin, config);
        openGui.put(player.getUniqueId(), GuiType.ADMIN);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    public void openPriorityMenu(Player player) {
        Inventory inv = PriorityMenu.create(player, config);
        openGui.put(player.getUniqueId(), GuiType.PRIORITY);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    public void openCapsMenu(Player player) {
        int page = capsPage.getOrDefault(player.getUniqueId(), 0);
        Inventory inv = CapsMenu.create(player, plugin, config, page);
        openGui.put(player.getUniqueId(), GuiType.CAPS);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    private void setCapsPage(Player player, int page) {
        capsPage.put(player.getUniqueId(), Math.max(0, page));
    }

    private int getCapsPage(Player player) {
        return capsPage.getOrDefault(player.getUniqueId(), 0);
    }

    /* =========================================================
       EVENTS
       ========================================================= */

    public void handleClose(Player player, InventoryCloseEvent event) {
        openGui.remove(player.getUniqueId());
        // keep capsPage remembered while server is running (nice QoL)
    }

    public void handleDrag(Player player, InventoryDragEvent event) {
        GuiType t = openGui.get(player.getUniqueId());
        if (t == null) return;

        if (t == GuiType.PLAYER) {
            if (!PlayerMenu.isThis(event.getView())) return;

            // Block dragging into output slot
            for (int raw : event.getRawSlots()) {
                if (raw == PlayerMenu.SLOT_OUTPUT) {
                    event.setCancelled(true);
                    return;
                }
            }

            Bukkit.getScheduler().runTask(plugin, () ->
                    PlayerMenu.refreshPreview(player, event.getView().getTopInventory(), plugin, config, costService)
            );
            return;
        }

        // admin menus: no dragging
        if (t == GuiType.ADMIN && AdminMenu.isThis(event.getView())) event.setCancelled(true);
        if (t == GuiType.PRIORITY && PriorityMenu.isThis(event.getView())) event.setCancelled(true);
        if (t == GuiType.CAPS && CapsMenu.isThis(event.getView())) event.setCancelled(true);
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        GuiType t = openGui.get(player.getUniqueId());
        if (t == null) return;

        if (t == GuiType.PLAYER) {
            if (!PlayerMenu.isThis(event.getView())) return;
            handlePlayerAnvilClick(player, event);
            return;
        }

        if (t == GuiType.ADMIN) {
            if (!AdminMenu.isThis(event.getView())) return;
            event.setCancelled(true);
            handleAdminMenuClick(player, event);
            return;
        }

        if (t == GuiType.PRIORITY) {
            if (!PriorityMenu.isThis(event.getView())) return;
            event.setCancelled(true);
            handlePriorityMenuClick(player, event);
            return;
        }

        if (t == GuiType.CAPS) {
            if (!CapsMenu.isThis(event.getView())) return;
            event.setCancelled(true);
            handleCapsMenuClick(player, event);
        }
    }

    /* =========================================================
       PLAYER ANVIL GUI
       ========================================================= */

    private void handlePlayerAnvilClick(Player player, InventoryClickEvent event) {
        int raw = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();

        // Block shift-click dumping from player inv into the anvil UI (Bedrock safe)
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        // If click is in player inventory (bottom), allow normal (except shift handled above)
        if (raw >= top.getSize()) return;

        // Block placing into output
        if (raw == PlayerMenu.SLOT_OUTPUT) {
            event.setCancelled(true);

            ItemStack out = top.getItem(PlayerMenu.SLOT_OUTPUT);
            if (out == null || out.getType() == Material.AIR) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            PlayerMenu.Mode mode = PlayerMenu.inferMode(top);
            if (mode == PlayerMenu.Mode.DISENCHANT) {
                performDisenchant(player, top);
            } else if (mode == PlayerMenu.Mode.APPLY) {
                performApply(player, top);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            return;
        }

        // Inputs (0/1) allowed; refresh after click applies
        Bukkit.getScheduler().runTask(plugin, () ->
                PlayerMenu.refreshPreview(player, top, plugin, config, costService)
        );
    }

    private void performDisenchant(Player player, Inventory inv) {
        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack books = inv.getItem(PlayerMenu.SLOT_BOOK);

        if (item == null || item.getType() == Material.AIR) {
            msgNo(player, "Put an item in the left slot.");
            return;
        }
        if (books == null || books.getType() != Material.BOOK || books.getAmount() <= 0) {
            msgNo(player, "Put at least 1 plain book in the right slot.");
            return;
        }
        if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) {
            msgNo(player, "You can’t disenchant books here.");
            return;
        }

        Map<Enchantment, Integer> ench = item.getEnchantments();
        if (ench == null || ench.isEmpty()) {
            msgNo(player, "That item has no enchantments.");
            return;
        }

        boolean removeAll = (books.getAmount() == 1);

        LinkedHashMap<Enchantment, Integer> toRemove;
        if (removeAll) {
            toRemove = PlayerMenu.sortedAllForBook(ench);
        } else {
            Enchantment chosen = config.chooseNextDisenchant(ench.keySet());
            if (chosen == null) {
                msgNo(player, "No enchantment could be selected for removal.");
                return;
            }
            toRemove = new LinkedHashMap<>();
            toRemove.put(chosen, ench.get(chosen));
        }

        ItemStack outBook = PlayerMenu.buildEnchantedBook(toRemove);

        if (!PlayerMenu.giveToPlayer(player, outBook)) {
            msgNo(player, "No inventory space.");
            return;
        }

        // consume exactly ONE plain book
        books.setAmount(books.getAmount() - 1);
        if (books.getAmount() <= 0) inv.setItem(PlayerMenu.SLOT_BOOK, null);
        else inv.setItem(PlayerMenu.SLOT_BOOK, books);

        // remove enchants from item
        ItemStack newItem = item.clone();
        ItemMeta meta = newItem.getItemMeta();
        if (meta == null) return;

        for (Enchantment e : toRemove.keySet()) meta.removeEnchant(e);
        newItem.setItemMeta(meta);
        inv.setItem(PlayerMenu.SLOT_ITEM, newItem);

        // XP refund (diminishing)
        refundService.refundForRemoval(player, newItem, toRemove);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);

        Bukkit.getScheduler().runTask(plugin, () ->
                PlayerMenu.refreshPreview(player, inv, plugin, config, costService)
        );
        player.updateInventory();
    }

    private void performApply(Player player, Inventory inv) {
        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack book = inv.getItem(PlayerMenu.SLOT_BOOK);

        if (item == null || item.getType() == Material.AIR) {
            msgNo(player, "Put an item in the left slot.");
            return;
        }
        if (book == null || book.getType() != Material.ENCHANTED_BOOK) {
            msgNo(player, "Put an enchanted book in the right slot.");
            return;
        }
        if (item.getType() == Material.ENCHANTED_BOOK || item.getType() == Material.BOOK) {
            msgNo(player, "You can’t apply books onto books here.");
            return;
        }

        EnchantCostService.ApplyPreview preview = costService.previewApply(player, item, book);
        if (!preview.canApply || preview.result == null) {
            msgNo(player, "Nothing can be applied (conflicts or lower/equal levels).");
            return;
        }

        boolean creative = (player.getGameMode() == GameMode.CREATIVE);
        if (!creative && player.getLevel() < preview.levelCost) {
            msgNo(player, "Not enough levels. Need: " + preview.levelCost);
            return;
        }

        if (!PlayerMenu.giveToPlayer(player, preview.result.clone())) {
            msgNo(player, "No inventory space.");
            return;
        }

        // consume 1 enchanted book
        int amt = book.getAmount() - 1;
        if (amt <= 0) inv.setItem(PlayerMenu.SLOT_BOOK, null);
        else {
            book.setAmount(amt);
            inv.setItem(PlayerMenu.SLOT_BOOK, book);
        }

        // consume item
        inv.setItem(PlayerMenu.SLOT_ITEM, null);

        // charge levels (NO clamp)
        if (!creative && preview.levelCost > 0) {
            player.giveExpLevels(-preview.levelCost);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);

        Bukkit.getScheduler().runTask(plugin, () ->
                PlayerMenu.refreshPreview(player, inv, plugin, config, costService)
        );
        player.updateInventory();
    }

    private void msgNo(Player p, String msg) {
        p.sendMessage("§e" + msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    /* =========================================================
       ADMIN MENUS (unchanged logic)
       ========================================================= */

    private void handleAdminMenuClick(Player player, InventoryClickEvent event) {
        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cOperator only.");
            player.closeInventory();
            return;
        }

        Inventory inv = event.getView().getTopInventory();
        int slot = event.getRawSlot();

        if (slot == AdminMenu.SLOT_CLOSE) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == AdminMenu.SLOT_PRIORITY) {
            openPriorityMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == AdminMenu.SLOT_CAPS) {
            openCapsMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == AdminMenu.SLOT_REFUND_FIRST_MINUS) {
            config.adjustRefund("extendedanvil.refund.first", -0.05);
            AdminMenu.render(inv, plugin, config);
            return;
        }
        if (slot == AdminMenu.SLOT_REFUND_FIRST_PLUS) {
            config.adjustRefund("extendedanvil.refund.first", +0.05);
            AdminMenu.render(inv, plugin, config);
            return;
        }
        if (slot == AdminMenu.SLOT_REFUND_SECOND_MINUS) {
            config.adjustRefund("extendedanvil.refund.second", -0.05);
            AdminMenu.render(inv, plugin, config);
            return;
        }
        if (slot == AdminMenu.SLOT_REFUND_SECOND_PLUS) {
            config.adjustRefund("extendedanvil.refund.second", +0.05);
            AdminMenu.render(inv, plugin, config);
            return;
        }
        if (slot == AdminMenu.SLOT_REFUND_AFTER_MINUS) {
            config.adjustRefund("extendedanvil.refund.after", -0.05);
            AdminMenu.render(inv, plugin, config);
            return;
        }
        if (slot == AdminMenu.SLOT_REFUND_AFTER_PLUS) {
            config.adjustRefund("extendedanvil.refund.after", +0.05);
            AdminMenu.render(inv, plugin, config);
            return;
        }

        if (slot == AdminMenu.SLOT_COST_MULT_MINUS) {
            config.adjustMultiplier(-0.1);
            AdminMenu.render(inv, plugin, config);
            return;
        }
        if (slot == AdminMenu.SLOT_COST_MULT_PLUS) {
            config.adjustMultiplier(+0.1);
            AdminMenu.render(inv, plugin, config);
        }
    }

    private void handlePriorityMenuClick(Player player, InventoryClickEvent event) {
        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cOperator only.");
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        Inventory inv = event.getView().getTopInventory();

        if (slot == PriorityMenu.SLOT_BACK) {
            openAdminMenu(player);
            return;
        }
        if (slot == PriorityMenu.SLOT_RESET) {
            config.resetPriority();
            PriorityMenu.render(inv, config);
            return;
        }

        PriorityMenu.EntryClick click = PriorityMenu.getClicked(slot, config);
        if (click == null) return;

        boolean shift = event.isShiftClick();
        boolean right = event.isRightClick();

        if (shift && right) config.movePriorityToBottom(click.key);
        else if (shift) config.movePriorityToTop(click.key);
        else if (right) config.movePriorityDown(click.key);
        else config.movePriorityUp(click.key);

        PriorityMenu.render(inv, config);
    }

    private void handleCapsMenuClick(Player player, InventoryClickEvent event) {
        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cOperator only.");
            player.closeInventory();
            return;
        }

        Inventory inv = event.getView().getTopInventory();
        int slot = event.getRawSlot();

        int current = getCapsPage(player);

        if (slot == CapsMenu.SLOT_BACK) {
            openAdminMenu(player);
            return;
        }

        CapsMenu.Click click = CapsMenu.getClicked(inv, slot, current);
        if (click == null) return;

        if (click.type == CapsMenu.ClickType.PAGE) {
            setCapsPage(player, click.page);
            CapsMenu.render(inv, plugin, config, getCapsPage(player));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (click.type == CapsMenu.ClickType.BACK) {
            openAdminMenu(player);
            return;
        }

        if (click.type != CapsMenu.ClickType.ENTRY || click.enchantKey == null) return;

        boolean shift = event.isShiftClick();
        boolean right = event.isRightClick();

        int delta = shift ? (right ? +10 : -10) : (right ? +1 : -1);

        config.adjustCap(click.enchantKey, delta);

        // Re-render current page
        CapsMenu.render(inv, plugin, config, current);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    private enum GuiType {
        PLAYER,
        ADMIN,
        PRIORITY,
        CAPS
    }
}
