package com.entitycore.modules.extendedanvil;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtendedAnvilListener implements Listener {

    private final Plugin plugin;
    private final ExtendedAnvilConfig config;
    private final ExtendedAnvilService service;

    private final ExtendedAnvilGui playerGui;
    private final ExtendedAnvilAdminMainGui adminMainGui;
    private final ExtendedAnvilRefundGui refundGui;
    private final ExtendedAnvilApplyCostGui applyCostGui;
    private final ExtendedAnvilGeneralGui generalGui;
    private final ExtendedAnvilPriorityGui priorityGui;
    private final ExtendedAnvilEnchantCostGui costGui;
    private final ExtendedAnvilEnchantCapGui capGui;

    public ExtendedAnvilListener(
            Plugin plugin,
            ExtendedAnvilConfig config,
            ExtendedAnvilService service,
            ExtendedAnvilGui playerGui,
            ExtendedAnvilAdminMainGui adminMainGui,
            ExtendedAnvilRefundGui refundGui,
            ExtendedAnvilApplyCostGui applyCostGui,
            ExtendedAnvilGeneralGui generalGui,
            ExtendedAnvilPriorityGui priorityGui,
            ExtendedAnvilEnchantCostGui costGui,
            ExtendedAnvilEnchantCapGui capGui
    ) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.playerGui = playerGui;
        this.adminMainGui = adminMainGui;
        this.refundGui = refundGui;
        this.applyCostGui = applyCostGui;
        this.generalGui = generalGui;
        this.priorityGui = priorityGui;
        this.costGui = costGui;
        this.capGui = capGui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof ExtendedAnvilHolder holder)) return;

        switch (holder.getType()) {
            case PLAYER -> handlePlayerGuiClick(e, player);
            case ADMIN_MAIN -> handleAdminMainClick(e, player);
            case ADMIN_REFUND -> handleRefundGuiClick(e, player);
            case ADMIN_APPLY_COST -> handleApplyCostGuiClick(e, player);
            case ADMIN_GENERAL -> handleGeneralGuiClick(e, player);
            case PRIORITY -> handlePriorityListClick(e, player);
            case PRIORITY_EDIT -> handlePriorityEditClick(e, player, holder);
            case ENCHANT_COST_LIST -> handleCostListClick(e, player, holder);
            case ENCHANT_COST_EDIT -> handleCostEditClick(e, player, holder);
            case ENCHANT_CAP_LIST -> handleCapListClick(e, player, holder);
            case ENCHANT_CAP_EDIT -> handleCapEditClick(e, player, holder);
            default -> e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // nothing special (kept for future safety)
    }

    // -------------------------
    // PLAYER GUI
    // -------------------------

    private void handlePlayerGuiClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;

        // Block any outside-inventory interactions
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }

        // Only block clicks for our GUI control slots; allow players to place items into the GUI slots.
        // But to keep Bedrock safe, we cancel everything and manually handle relevant actions.
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilCommand.PERM_USE)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        // Allow players to put items into the intended slots by mirroring click behavior
        // (Bedrock clients are picky; easiest is to let them place via taps by using normal inventory mechanics.
        // For now: only support click actions via the dedicated buttons and require players to place items
        // with their client; cancelling the event means they can't. So we implement a safe 'swap' behavior
        // for the key slots.)
        //
        // This module version uses a simple approach:
        // - Allow swapping cursor <-> slot for the three input slots
        // - Handle action buttons normally

        if (raw == ExtendedAnvilGui.SLOT_ITEM || raw == ExtendedAnvilGui.SLOT_BOOK || raw == ExtendedAnvilGui.SLOT_BOOK2) {
            ItemStack cursor = e.getCursor();
            ItemStack inSlot = e.getCurrentItem();
            e.getInventory().setItem(raw, cursor);
            e.setCursor(inSlot);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            return;
        }

        if (raw == ExtendedAnvilGui.SLOT_APPLY) {
            Inventory inv = e.getInventory();
            ItemStack item = inv.getItem(ExtendedAnvilGui.SLOT_ITEM);
            ItemStack book = inv.getItem(ExtendedAnvilGui.SLOT_BOOK);
            if (item == null || item.getType() == Material.AIR || book == null || book.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Put an item and an enchanted book in first.");
                return;
            }

            ExtendedAnvilService.ApplyResult r = service.applyBook(player, item, book);
            if (!r.ok()) {
                player.sendMessage(r.message());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                return;
            }

            inv.setItem(ExtendedAnvilGui.SLOT_ITEM, item);
            inv.setItem(ExtendedAnvilGui.SLOT_BOOK, null);
            inv.setItem(ExtendedAnvilGui.SLOT_BOOK2, null);
            player.sendMessage(r.message());
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
            playerGui.refresh(inv, player);
            return;
        }

        if (raw == ExtendedAnvilGui.SLOT_DISENCHANT) {
            Inventory inv = e.getInventory();
            ItemStack item = inv.getItem(ExtendedAnvilGui.SLOT_ITEM);
            ItemStack book1 = inv.getItem(ExtendedAnvilGui.SLOT_BOOK);
            ItemStack book2 = inv.getItem(ExtendedAnvilGui.SLOT_BOOK2);

            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "Put an enchanted item in first.");
                return;
            }

            int emptyBooks = 0;
            if (book1 != null && book1.getType() == Material.BOOK) emptyBooks++;
            if (book2 != null && book2.getType() == Material.BOOK) emptyBooks++;

            if (emptyBooks == 0) {
                player.sendMessage(ChatColor.RED + "Put 1 or 2 empty books in.");
                return;
            }

            boolean removeAll = (emptyBooks == 1);
            ExtendedAnvilService.DisenchantResult r = service.disenchant(player, item, removeAll);
            if (!r.ok()) {
                player.sendMessage(r.message());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                return;
            }

            // Consume books used
            inv.setItem(ExtendedAnvilGui.SLOT_BOOK, null);
            inv.setItem(ExtendedAnvilGui.SLOT_BOOK2, null);
            inv.setItem(ExtendedAnvilGui.SLOT_ITEM, item);

            // Put output book into book slot if empty, else drop
            if (inv.getItem(ExtendedAnvilGui.SLOT_BOOK) == null) {
                inv.setItem(ExtendedAnvilGui.SLOT_BOOK, r.book());
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), r.book());
            }

            player.sendMessage(ChatColor.GREEN + "Removed " + r.removedCount() + " enchant(s). Refund: " + r.refundLevels() + " level(s).");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
            playerGui.refresh(inv, player);
            return;
        }

        if (raw == ExtendedAnvilGui.SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    // -------------------------
    // ADMIN MAIN + SUBMENUS
    // -------------------------

    private void handleAdminMainClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        if (raw == ExtendedAnvilAdminMainGui.SLOT_REFUND) {
            refundGui.open(player);
            click(player);
            return;
        }
        if (raw == ExtendedAnvilAdminMainGui.SLOT_APPLY_COST) {
            applyCostGui.open(player);
            click(player);
            return;
        }
        if (raw == ExtendedAnvilAdminMainGui.SLOT_GENERAL) {
            generalGui.open(player);
            click(player);
            return;
        }
        if (raw == ExtendedAnvilAdminMainGui.SLOT_PRIORITY) {
            priorityGui.open(player);
            click(player);
            return;
        }
        if (raw == ExtendedAnvilAdminMainGui.SLOT_ENCHANT_COSTS) {
            costGui.openList(player, 0);
            click(player);
            return;
        }
        if (raw == ExtendedAnvilAdminMainGui.SLOT_ENCHANT_CAPS) {
            capGui.openList(player, 0);
            click(player);
            return;
        }

        if (raw == ExtendedAnvilAdminMainGui.SLOT_SAVE) {
            config.save();
            player.sendMessage(ChatColor.GREEN + "[EA] Saved extendedanvil.yml");
            click(player);
            return;
        }

        if (raw == ExtendedAnvilAdminMainGui.SLOT_RESET) {
            ExtendedAnvilConfig fresh = new ExtendedAnvilConfig((org.bukkit.plugin.java.JavaPlugin) plugin);
            fresh.load();

            config.setRefundPercentFirst(fresh.getRefundPercentFirst());
            config.setRefundPercentSecond(fresh.getRefundPercentSecond());
            config.setRefundPercentLast(fresh.getRefundPercentLast());
            config.setRefundLevelsPerEnchantLevel(fresh.getRefundLevelsPerEnchantLevel());
            config.setAllowCurseRemoval(fresh.isAllowCurseRemoval());
            config.setDebug(fresh.isDebug());

            config.setApplyCostGlobalBaseLevels(fresh.getApplyCostGlobalBaseLevels());
            config.setApplyCostPerEnchantAdd(fresh.getApplyCostPerEnchantAdd());
            config.setApplyCostPerStoredLevelAdd(fresh.getApplyCostPerStoredLevelAdd());

            config.setPriorWorkCostPerStep(fresh.getPriorWorkCostPerStep());
            config.setPriorWorkIncrementPerApply(fresh.getPriorWorkIncrementPerApply());

            config.getEnchantBaseCostMap().clear();
            config.getEnchantBaseCostMap().putAll(fresh.getEnchantBaseCostMap());
            config.getEnchantPerLevelCostMap().clear();
            config.getEnchantPerLevelCostMap().putAll(fresh.getEnchantPerLevelCostMap());

            config.getEnchantMaxLevelMap().clear();
            config.getEnchantMaxLevelMap().putAll(fresh.getEnchantMaxLevelMap());

            config.getPriority().clear();
            config.getPriority().addAll(fresh.getPriority());

            config.save();
            player.sendMessage(ChatColor.GREEN + "[EA] Reset to defaults.");
            click(player);
        }
    }

    private void handleRefundGuiClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        if (raw == ExtendedAnvilRefundGui.SLOT_BACK) {
            adminMainGui.open(player);
            return;
        }

        Integer delta = resolveDelta(raw, refundValueSlots());
        if (delta == null) return;
        Integer valueSlot = resolveValueSlotFromDeltaButton(raw, refundValueSlots());
        if (valueSlot == null) return;

        if (valueSlot == ExtendedAnvilRefundGui.SLOT_FIRST_VALUE) {
            config.setRefundPercentFirst(config.getRefundPercentFirst() + delta);
        } else if (valueSlot == ExtendedAnvilRefundGui.SLOT_SECOND_VALUE) {
            config.setRefundPercentSecond(config.getRefundPercentSecond() + delta);
        } else if (valueSlot == ExtendedAnvilRefundGui.SLOT_LAST_VALUE) {
            config.setRefundPercentLast(config.getRefundPercentLast() + delta);
        } else if (valueSlot == ExtendedAnvilRefundGui.SLOT_FALLBACK_VALUE) {
            config.setRefundLevelsPerEnchantLevel(config.getRefundLevelsPerEnchantLevel() + delta);
        }

        refundGui.refresh(e.getInventory());
        click(player);
    }

    private void handleApplyCostGuiClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        if (raw == ExtendedAnvilApplyCostGui.SLOT_BACK) {
            adminMainGui.open(player);
            return;
        }

        Integer delta = resolveDelta(raw, applyCostValueSlots());
        if (delta == null) return;
        Integer valueSlot = resolveValueSlotFromDeltaButton(raw, applyCostValueSlots());
        if (valueSlot == null) return;

        if (valueSlot == ExtendedAnvilApplyCostGui.SLOT_GLOBAL_BASE_VALUE) {
            config.setApplyCostGlobalBaseLevels(config.getApplyCostGlobalBaseLevels() + delta);
        } else if (valueSlot == ExtendedAnvilApplyCostGui.SLOT_ADD_PER_ENCHANT_VALUE) {
            config.setApplyCostPerEnchantAdd(config.getApplyCostPerEnchantAdd() + delta);
        } else if (valueSlot == ExtendedAnvilApplyCostGui.SLOT_ADD_PER_STORED_LEVEL_VALUE) {
            config.setApplyCostPerStoredLevelAdd(config.getApplyCostPerStoredLevelAdd() + delta);
        } else if (valueSlot == ExtendedAnvilApplyCostGui.SLOT_PRIOR_WORK_COST_VALUE) {
            config.setPriorWorkCostPerStep(config.getPriorWorkCostPerStep() + delta);
        } else if (valueSlot == ExtendedAnvilApplyCostGui.SLOT_PRIOR_WORK_INC_VALUE) {
            config.setPriorWorkIncrementPerApply(config.getPriorWorkIncrementPerApply() + delta);
        }

        applyCostGui.refresh(e.getInventory());
        click(player);
    }

    private void handleGeneralGuiClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        if (raw == ExtendedAnvilGeneralGui.SLOT_BACK) {
            adminMainGui.open(player);
            return;
        }

        if (raw == ExtendedAnvilGeneralGui.SLOT_CURSE_TOGGLE) {
            config.setAllowCurseRemoval(!config.isAllowCurseRemoval());
            generalGui.refresh(e.getInventory());
            click(player);
            return;
        }

        if (raw == ExtendedAnvilGeneralGui.SLOT_DEBUG_TOGGLE) {
            config.setDebug(!config.isDebug());
            generalGui.refresh(e.getInventory());
            click(player);
        }
    }

    private Integer resolveDelta(int raw, int[] valueSlots) {
        for (int v : valueSlots) {
            if (raw == v - 18) return -10;
            if (raw == v - 9) return -1;
            if (raw == v + 9) return 1;
            if (raw == v + 18) return 10;
        }
        return null;
    }

    private Integer resolveValueSlotFromDeltaButton(int raw, int[] valueSlots) {
        for (int v : valueSlots) {
            if (raw == v - 18 || raw == v - 9 || raw == v + 9 || raw == v + 18) return v;
        }
        return null;
    }

    private int[] refundValueSlots() {
        return new int[]{
                ExtendedAnvilRefundGui.SLOT_FIRST_VALUE,
                ExtendedAnvilRefundGui.SLOT_SECOND_VALUE,
                ExtendedAnvilRefundGui.SLOT_LAST_VALUE,
                ExtendedAnvilRefundGui.SLOT_FALLBACK_VALUE
        };
    }

    private int[] applyCostValueSlots() {
        return new int[]{
                ExtendedAnvilApplyCostGui.SLOT_GLOBAL_BASE_VALUE,
                ExtendedAnvilApplyCostGui.SLOT_ADD_PER_ENCHANT_VALUE,
                ExtendedAnvilApplyCostGui.SLOT_ADD_PER_STORED_LEVEL_VALUE,
                ExtendedAnvilApplyCostGui.SLOT_PRIOR_WORK_COST_VALUE,
                ExtendedAnvilApplyCostGui.SLOT_PRIOR_WORK_INC_VALUE
        };
    }

    // -------------------------
    // PRIORITY GUIS
    // -------------------------

    private void handlePriorityListClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        if (raw == ExtendedAnvilPriorityGui.SLOT_BACK) {
            adminMainGui.open(player);
            return;
        }

        if (raw >= 45) return;
        if (raw >= config.getPriority().size()) return;

        String key = config.getPriority().get(raw);
        priorityGui.openEdit(player, key);
        click(player);
    }

    private void handlePriorityEditClick(InventoryClickEvent e, Player player, ExtendedAnvilHolder holder) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        String key = holder.getContextKey();
        if (key == null) {
            priorityGui.open(player);
            return;
        }

        if (raw == ExtendedAnvilPriorityGui.SLOT_EDIT_BACK) {
            priorityGui.open(player);
            return;
        }

        List<String> list = config.getPriority();
        int idx = list.indexOf(key);
        if (idx < 0) {
            priorityGui.open(player);
            return;
        }

        int newIdx = idx;
        if (raw == ExtendedAnvilPriorityGui.SLOT_MOVE_UP) newIdx = Math.max(0, idx - 1);
        else if (raw == ExtendedAnvilPriorityGui.SLOT_MOVE_DOWN) newIdx = Math.min(list.size() - 1, idx + 1);
        else if (raw == ExtendedAnvilPriorityGui.SLOT_MOVE_TOP) newIdx = 0;
        else if (raw == ExtendedAnvilPriorityGui.SLOT_MOVE_BOTTOM) newIdx = list.size() - 1;
        else return;

        if (newIdx != idx) {
            list.remove(idx);
            list.add(newIdx, key);
            priorityGui.drawEdit(e.getInventory(), key);
            click(player);
        }
    }

    // -------------------------
    // ENCHANT COST GUIS
    // -------------------------

    private void handleCostListClick(InventoryClickEvent e, Player player, ExtendedAnvilHolder holder) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        int page = holder.getPage();

        if (raw == ExtendedAnvilEnchantCostGui.SLOT_BACK) {
            adminMainGui.open(player);
            return;
        }
        if (raw == ExtendedAnvilEnchantCostGui.SLOT_PREV) {
            costGui.openList(player, Math.max(0, page - 1));
            return;
        }
        if (raw == ExtendedAnvilEnchantCostGui.SLOT_NEXT) {
            costGui.openList(player, page + 1);
            return;
        }

        if (raw >= 45) return;

        List<String> keys = new ArrayList<>(config.getEnchantBaseCostMap().keySet());
        Collections.sort(keys);
        int idx = page * 45 + raw;
        if (idx < 0 || idx >= keys.size()) return;

        costGui.openEdit(player, keys.get(idx));
        click(player);
    }

    private void handleCostEditClick(InventoryClickEvent e, Player player, ExtendedAnvilHolder holder) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        String key = holder.getContextKey();
        if (key == null) {
            costGui.openList(player, 0);
            return;
        }

        if (raw == ExtendedAnvilEnchantCostGui.SLOT_EDIT_BACK) {
            costGui.openList(player, 0);
            return;
        }
        if (raw == ExtendedAnvilEnchantCostGui.SLOT_SAVE) {
            config.save();
            player.sendMessage(ChatColor.GREEN + "[EA] Saved extendedanvil.yml");
            click(player);
            return;
        }

        boolean changed = false;
        if (raw == ExtendedAnvilEnchantCostGui.SLOT_BASE_MINUS_10) {
            config.setEnchantBaseCost(key, config.getEnchantBaseCost(key) - 10);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCostGui.SLOT_BASE_MINUS_1) {
            config.setEnchantBaseCost(key, config.getEnchantBaseCost(key) - 1);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCostGui.SLOT_BASE_PLUS_1) {
            config.setEnchantBaseCost(key, config.getEnchantBaseCost(key) + 1);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCostGui.SLOT_BASE_PLUS_10) {
            config.setEnchantBaseCost(key, config.getEnchantBaseCost(key) + 10);
            changed = true;
        }

        if (raw == ExtendedAnvilEnchantCostGui.SLOT_PERLVL_MINUS_10) {
            config.setEnchantPerLevelCost(key, config.getEnchantPerLevelCost(key) - 10);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCostGui.SLOT_PERLVL_MINUS_1) {
            config.setEnchantPerLevelCost(key, config.getEnchantPerLevelCost(key) - 1);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCostGui.SLOT_PERLVL_PLUS_1) {
            config.setEnchantPerLevelCost(key, config.getEnchantPerLevelCost(key) + 1);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCostGui.SLOT_PERLVL_PLUS_10) {
            config.setEnchantPerLevelCost(key, config.getEnchantPerLevelCost(key) + 10);
            changed = true;
        }

        if (changed) {
            costGui.drawEdit(e.getInventory(), key);
            click(player);
        }
    }

    // -------------------------
    // ENCHANT CAP GUIS
    // -------------------------

    private void handleCapListClick(InventoryClickEvent e, Player player, ExtendedAnvilHolder holder) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        int page = holder.getPage();

        if (raw == ExtendedAnvilEnchantCapGui.SLOT_BACK) {
            adminMainGui.open(player);
            return;
        }
        if (raw == ExtendedAnvilEnchantCapGui.SLOT_PREV) {
            capGui.openList(player, Math.max(0, page - 1));
            return;
        }
        if (raw == ExtendedAnvilEnchantCapGui.SLOT_NEXT) {
            capGui.openList(player, page + 1);
            return;
        }

        if (raw >= 45) return;

        List<String> keys = new ArrayList<>(config.getEnchantMaxLevelMap().keySet());
        Collections.sort(keys);
        int idx = page * 45 + raw;
        if (idx < 0 || idx >= keys.size()) return;

        capGui.openEdit(player, keys.get(idx));
        click(player);
    }

    private void handleCapEditClick(InventoryClickEvent e, Player player, ExtendedAnvilHolder holder) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission(ExtendedAnvilAdminCommand.PERMISSION)) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        String key = holder.getContextKey();
        if (key == null) {
            capGui.openList(player, 0);
            return;
        }

        if (raw == ExtendedAnvilEnchantCapGui.SLOT_EDIT_BACK) {
            capGui.openList(player, 0);
            return;
        }
        if (raw == ExtendedAnvilEnchantCapGui.SLOT_SAVE) {
            config.save();
            player.sendMessage(ChatColor.GREEN + "[EA] Saved extendedanvil.yml");
            click(player);
            return;
        }

        boolean changed = false;
        if (raw == ExtendedAnvilEnchantCapGui.SLOT_MINUS_10) {
            config.setEnchantMaxLevel(key, config.getEnchantMaxLevel(key) - 10);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCapGui.SLOT_MINUS_1) {
            config.setEnchantMaxLevel(key, config.getEnchantMaxLevel(key) - 1);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCapGui.SLOT_PLUS_1) {
            config.setEnchantMaxLevel(key, config.getEnchantMaxLevel(key) + 1);
            changed = true;
        } else if (raw == ExtendedAnvilEnchantCapGui.SLOT_PLUS_10) {
            config.setEnchantMaxLevel(key, config.getEnchantMaxLevel(key) + 10);
            changed = true;
        }

        if (changed) {
            capGui.drawEdit(e.getInventory(), key);
            click(player);
        }
    }

    private void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }
}
