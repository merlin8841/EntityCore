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

    public ExtendedAnvilSessionManager(JavaPlugin plugin,
                                      ExtendedAnvilConfig config,
                                      XpRefundService refundService,
                                      EnchantCostService costService) {
        this.plugin = plugin;
        this.config = config;
        this.refundService = refundService;
        this.costService = costService;
    }

    public void openPlayerMenu(Player player) {
        Inventory inv = PlayerMenu.create(player);
        openGui.put(player.getUniqueId(), GuiType.PLAYER);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
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
        Inventory inv = CapsMenu.create(player, plugin, config, 0);
        openGui.put(player.getUniqueId(), GuiType.CAPS);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    public void handleClose(Player player, InventoryCloseEvent event) {
        openGui.remove(player.getUniqueId());
    }

    public void handleDrag(Player player, InventoryDragEvent event) {
        GuiType t = openGui.get(player.getUniqueId());
        if (t == null) return;

        if (t == GuiType.PLAYER) {
            if (!PlayerMenu.isThis(event.getView())) return;

            for (int raw : event.getRawSlots()) {
                if (raw < event.getView().getTopInventory().getSize()) {
                    if (raw != PlayerMenu.SLOT_ITEM && raw != PlayerMenu.SLOT_BOOK) {
                        event.setCancelled(true);
                        return;
                    }
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
            handlePlayerMenuClick(player, event);
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
       PLAYER MENU
       ========================================================= */

    private void handlePlayerMenuClick(Player player, InventoryClickEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        int raw = event.getRawSlot();

        // bottom inventory clicks allowed (but block shift-dump)
        if (raw >= topSize) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        Inventory inv = event.getView().getTopInventory();

        boolean isInput = (raw == PlayerMenu.SLOT_ITEM || raw == PlayerMenu.SLOT_BOOK);
        boolean isOutput = (raw == PlayerMenu.SLOT_OUTPUT);
        boolean isButton = (raw == PlayerMenu.SLOT_MODE || raw == PlayerMenu.SLOT_DO || raw == PlayerMenu.SLOT_CLOSE);

        if (!isInput && !isOutput && !isButton) {
            event.setCancelled(true);
            return;
        }

        if (raw == PlayerMenu.SLOT_CLOSE) {
            event.setCancelled(true);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (raw == PlayerMenu.SLOT_MODE) {
            event.setCancelled(true);
            PlayerMenu.toggleMode(inv);
            PlayerMenu.refreshPreview(player, inv, plugin, config, costService);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (raw == PlayerMenu.SLOT_DO || isOutput) {
            event.setCancelled(true);
            if (PlayerMenu.getMode(inv) == PlayerMenu.Mode.DISENCHANT) {
                performDisenchant(player, inv);
            } else {
                performApply(player, inv);
            }
            return;
        }

        // input slots: no shift-click
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () ->
                PlayerMenu.refreshPreview(player, inv, plugin, config, costService)
        );
    }

    private void performDisenchant(Player player, Inventory inv) {
        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack books = inv.getItem(PlayerMenu.SLOT_BOOK);

        if (item == null || item.getType() == Material.AIR) {
            msgNo(player, "Put an item in the item slot.");
            return;
        }
        if (books == null || books.getType() != Material.BOOK || books.getAmount() <= 0) {
            msgNo(player, "Put at least 1 plain book in the books slot.");
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

        // XP refund with diminishing tracking on the item
        refundService.refundForRemoval(player, newItem, toRemove);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);

        PlayerMenu.refreshPreview(player, inv, plugin, config, costService);
        player.updateInventory();
    }

    private void performApply(Player player, Inventory inv) {
        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack book = inv.getItem(PlayerMenu.SLOT_BOOK);

        if (item == null || item.getType() == Material.AIR) {
            msgNo(player, "Put an item in the item slot.");
            return;
        }
        if (book == null || book.getType() != Material.ENCHANTED_BOOK) {
            msgNo(player, "Put an enchanted book in the books slot.");
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

        // charge levels (NO CLAMP)
        if (!creative && preview.levelCost > 0) {
            player.giveExpLevels(-preview.levelCost);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);

        PlayerMenu.refreshPreview(player, inv, plugin, config, costService);
        player.updateInventory();
    }

    private void msgNo(Player p, String msg) {
        p.sendMessage("§e" + msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    /* =========================================================
       ADMIN MENUS
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

        if (slot == CapsMenu.SLOT_BACK) {
            openAdminMenu(player);
            return;
        }

        CapsMenu.Click c = CapsMenu.getClicked(inv, slot, 0);
        if (c == null) return;

        boolean shift = event.isShiftClick();
        boolean right = event.isRightClick();

        int delta = shift ? (right ? +10 : -10) : (right ? +1 : -1);

        config.adjustCap(c.enchantKey, delta);
        CapsMenu.render(inv, plugin, config, c.page);
    }

    private enum GuiType {
        PLAYER,
        ADMIN,
        PRIORITY,
        CAPS
    }
}
