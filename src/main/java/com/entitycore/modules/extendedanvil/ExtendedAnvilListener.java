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
    private final ExtendedAnvilAdminGui adminGui;
    private final ExtendedAnvilPriorityGui priorityGui;
    private final ExtendedAnvilEnchantCostGui costGui;

    public ExtendedAnvilListener(
            Plugin plugin,
            ExtendedAnvilConfig config,
            ExtendedAnvilService service,
            ExtendedAnvilGui playerGui,
            ExtendedAnvilAdminGui adminGui,
            ExtendedAnvilPriorityGui priorityGui,
            ExtendedAnvilEnchantCostGui costGui
    ) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.playerGui = playerGui;
        this.adminGui = adminGui;
        this.priorityGui = priorityGui;
        this.costGui = costGui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof ExtendedAnvilHolder holder)) return;

        switch (holder.getType()) {
            case PLAYER -> handlePlayerGuiClick(e, player);
            case ADMIN -> handleAdminGuiClick(e, player);
            case PRIORITY -> handlePriorityListClick(e, player);
            case PRIORITY_EDIT -> handlePriorityEditClick(e, player, holder);
            case ENCHANT_COST_LIST -> handleCostListClick(e, player, holder);
            case ENCHANT_COST_EDIT -> handleCostEditClick(e, player, holder);
            default -> {
                // Block any unknown EA holders
                e.setCancelled(true);
            }
        }
    }

    // -------------------------
    // PLAYER GUI
    // -------------------------

    private void handlePlayerGuiClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) return;

        boolean isItem = raw == ExtendedAnvilGui.SLOT_ITEM;
        boolean isOutput = raw == ExtendedAnvilGui.SLOT_OUTPUT;
        boolean isBookSlot = raw >= ExtendedAnvilGui.BOOKS_FROM && raw <= ExtendedAnvilGui.BOOKS_TO;

        if (!isItem && !isOutput && !isBookSlot) {
            e.setCancelled(true);
            return;
        }

        if (!isOutput) return;

        e.setCancelled(true);
        Inventory inv = e.getInventory();
        ItemStack target = inv.getItem(ExtendedAnvilGui.SLOT_ITEM);

        ExtendedAnvilUtil.InventoryViewAccessor accessor = new InventoryAccessor(inv);

        ItemStack enchantedBook = ExtendedAnvilUtil.findFirstEnchantedBook(accessor, ExtendedAnvilGui.BOOKS_FROM, ExtendedAnvilGui.BOOKS_TO);
        int emptyBooks = ExtendedAnvilUtil.countEmptyBooks(inv.getContents(), ExtendedAnvilGui.BOOKS_FROM, ExtendedAnvilGui.BOOKS_TO);

        // If an enchanted book exists, attempt apply/combine
        if (enchantedBook != null) {
            ExtendedAnvilService.ApplyResult res = service.applyFromBook(player, target, enchantedBook);
            if (!res.ok()) {
                player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RESET + res.message());
                return;
            }

            // consume one enchanted book used
            removeOneMatchingEnchantedBook(inv, ExtendedAnvilGui.BOOKS_FROM, ExtendedAnvilGui.BOOKS_TO);

            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RESET + res.message());
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
            return;
        }

        // Disenchant path requires empty books
        if (emptyBooks <= 0) {
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RED
                    + "Put empty books (disenchant) or an enchanted book (apply/combine) in the book slots.");
            return;
        }

        boolean removeAll = emptyBooks == 1;

        // consume 1 empty book per click
        ExtendedAnvilUtil.takeOneEmptyBook(accessor, ExtendedAnvilGui.BOOKS_FROM, ExtendedAnvilGui.BOOKS_TO);

        ExtendedAnvilService.DisenchantResult res = service.disenchant(player, target, removeAll);
        if (!res.ok()) {
            giveBookBack(inv);
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RESET + res.message());
            return;
        }

        // Give output book
        ItemStack book = res.book();
        var leftovers = player.getInventory().addItem(book);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        }

        if (res.refundLevels() > 0) {
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.GREEN
                    + "Disenchanted " + res.removedCount() + " enchant(s). Refunded " + res.refundLevels() + " level(s).");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.75f, 1.2f);
        } else {
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.GREEN
                    + "Disenchanted " + res.removedCount() + " enchant(s).");
            player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExtendedAnvilHolder holder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        if (holder.getType() != ExtendedAnvilHolder.Type.PLAYER) return;

        Inventory inv = e.getInventory();

        // Return item slot
        ItemStack item = inv.getItem(ExtendedAnvilGui.SLOT_ITEM);
        if (item != null && !item.getType().isAir()) {
            var left = player.getInventory().addItem(item);
            if (!left.isEmpty()) left.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
            inv.setItem(ExtendedAnvilGui.SLOT_ITEM, null);
        }

        // Return books row
        for (int i = ExtendedAnvilGui.BOOKS_FROM; i <= ExtendedAnvilGui.BOOKS_TO; i++) {
            ItemStack b = inv.getItem(i);
            if (b != null && !b.getType().isAir()) {
                var left = player.getInventory().addItem(b);
                if (!left.isEmpty()) left.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
                inv.setItem(i, null);
            }
        }
    }

    private void removeOneMatchingEnchantedBook(Inventory inv, int from, int to) {
        for (int i = from; i <= to; i++) {
            ItemStack it = inv.getItem(i);
            if (ExtendedAnvilUtil.isEnchantedBook(it)) {
                if (it.getAmount() <= 1) inv.setItem(i, null);
                else {
                    it.setAmount(it.getAmount() - 1);
                    inv.setItem(i, it);
                }
                return;
            }
        }
    }

    private void giveBookBack(Inventory inv) {
        for (int i = ExtendedAnvilGui.BOOKS_FROM; i <= ExtendedAnvilGui.BOOKS_TO; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                inv.setItem(i, new ItemStack(Material.BOOK));
                return;
            }
            if (it.getType() == Material.BOOK && it.getAmount() < it.getMaxStackSize()) {
                it.setAmount(it.getAmount() + 1);
                inv.setItem(i, it);
                return;
            }
        }
    }

    // -------------------------
    // ADMIN GUI (Bedrock-friendly +/- buttons)
    // -------------------------

    private void handleAdminGuiClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;

        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        // Toggle
        if (raw == ExtendedAnvilAdminGui.SLOT_CURSE_TOGGLE) {
            config.setAllowCurseRemoval(!config.isAllowCurseRemoval());
            adminGui.refresh(e.getInventory());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            return;
        }

        // Open editors
        if (raw == ExtendedAnvilAdminGui.SLOT_EDIT_PRIORITY) {
            priorityGui.open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            return;
        }
        if (raw == ExtendedAnvilAdminGui.SLOT_EDIT_ENCHANT_COSTS) {
            costGui.openList(player, 0);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            return;
        }

        // Save/reset
        if (raw == ExtendedAnvilAdminGui.SLOT_SAVE) {
            config.save();
            player.sendMessage(ChatColor.GREEN + "[EA] Saved extendedanvil.yml");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            return;
        }
        if (raw == ExtendedAnvilAdminGui.SLOT_RESET) {
            ExtendedAnvilConfig fresh = new ExtendedAnvilConfig((org.bukkit.plugin.java.JavaPlugin) plugin);
            fresh.load();

            config.setRefundPercentFirst(fresh.getRefundPercentFirst());
            config.setRefundPercentSecond(fresh.getRefundPercentSecond());
            config.setRefundPercentLater(fresh.getRefundPercentLater());
            config.setRefundLevelsPerEnchantLevel(fresh.getRefundLevelsPerEnchantLevel());
            config.setAllowCurseRemoval(fresh.isAllowCurseRemoval());

            config.setApplyCostGlobalBaseLevels(fresh.getApplyCostGlobalBaseLevels());
            config.setApplyCostPerEnchantAdd(fresh.getApplyCostPerEnchantAdd());
            config.setApplyCostPerStoredLevelAdd(fresh.getApplyCostPerStoredLevelAdd());

            config.setPriorWorkCostPerStep(fresh.getPriorWorkCostPerStep());
            config.setPriorWorkIncrementPerApply(fresh.getPriorWorkIncrementPerApply());

            config.getEnchantBaseCostMap().clear();
            config.getEnchantBaseCostMap().putAll(fresh.getEnchantBaseCostMap());
            config.getEnchantPerLevelCostMap().clear();
            config.getEnchantPerLevelCostMap().putAll(fresh.getEnchantPerLevelCostMap());

            config.getPriority().clear();
            config.getPriority().addAll(fresh.getPriority());

            config.save();
            adminGui.refresh(e.getInventory());
            player.sendMessage(ChatColor.GREEN + "[EA] Reset to defaults.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            return;
        }

        // +/- buttons map to nearest "value slot"
        Integer delta = resolveAdminDelta(raw);
        if (delta == null) return;

        Integer valueSlot = resolveAdminValueSlotFromDeltaButton(raw);
        if (valueSlot == null) return;

        // Apply change
        if (valueSlot == ExtendedAnvilAdminGui.SLOT_REFUND_FIRST_VALUE) {
            config.setRefundPercentFirst(config.getRefundPercentFirst() + delta);
        } else if (valueSlot == ExtendedAnvilAdminGui.SLOT_REFUND_SECOND_VALUE) {
            config.setRefundPercentSecond(config.getRefundPercentSecond() + delta);
        } else if (valueSlot == ExtendedAnvilAdminGui.SLOT_REFUND_LATER_VALUE) {
            config.setRefundPercentLater(config.getRefundPercentLater() + delta);
        } else if (valueSlot == ExtendedAnvilAdminGui.SLOT_REFUND_FALLBACK_VALUE) {
            config.setRefundLevelsPerEnchantLevel(config.getRefundLevelsPerEnchantLevel() + delta);
        } else if (valueSlot == ExtendedAnvilAdminGui.SLOT_GLOBAL_BASE_VALUE) {
            config.setApplyCostGlobalBaseLevels(config.getApplyCostGlobalBaseLevels() + delta);
        } else if (valueSlot == ExtendedAnvilAdminGui.SLOT_ADD_PER_ENCHANT_VALUE) {
            config.setApplyCostPerEnchantAdd(config.getApplyCostPerEnchantAdd() + delta);
        } else if (valueSlot == ExtendedAnvilAdminGui.SLOT_ADD_PER_STORED_LEVEL_VALUE) {
            config.setApplyCostPerStoredLevelAdd(config.getApplyCostPerStoredLevelAdd() + delta);
        } else if (valueSlot == ExtendedAnvilAdminGui.SLOT_PRIOR_WORK_COST_VALUE) {
            config.setPriorWorkCostPerStep(config.getPriorWorkCostPerStep() + delta);
        } else if (valueSlot == ExtendedAnvilAdminGui.SLOT_PRIOR_WORK_INC_VALUE) {
            config.setPriorWorkIncrementPerApply(config.getPriorWorkIncrementPerApply() + delta);
        }

        adminGui.refresh(e.getInventory());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    private Integer resolveAdminDelta(int raw) {
        // AdminGui places +/- buttons around value slots:
        // -10 at value-18, -1 at value-9, +1 at value+9, +10 at value+18
        // We detect which offset it is by scanning known offsets.
        // This is bedrock-safe (simple tap).
        // Offsets:
        //   -18 => -10
        //   -9  => -1
        //   +9  => +1
        //   +18 => +10
        // We'll infer delta based on "relative position" to the nearest value slot.
        return switch (raw % 9) {
            // We can't reliably use %9 alone, so we resolve via explicit offsets below.
            default -> {
                // check explicit offsets by comparing against each value slot
                Integer d = null;
                for (int v : adminValueSlots()) {
                    if (raw == v - 18) d = -10;
                    else if (raw == v - 9) d = -1;
                    else if (raw == v + 9) d = 1;
                    else if (raw == v + 18) d = 10;
                    if (d != null) break;
                }
                yield d;
            }
        };
    }

    private Integer resolveAdminValueSlotFromDeltaButton(int raw) {
        for (int v : adminValueSlots()) {
            if (raw == v - 18 || raw == v - 9 || raw == v + 9 || raw == v + 18) return v;
        }
        return null;
    }

    private int[] adminValueSlots() {
        return new int[] {
                ExtendedAnvilAdminGui.SLOT_REFUND_FIRST_VALUE,
                ExtendedAnvilAdminGui.SLOT_REFUND_SECOND_VALUE,
                ExtendedAnvilAdminGui.SLOT_REFUND_LATER_VALUE,
                ExtendedAnvilAdminGui.SLOT_REFUND_FALLBACK_VALUE,
                ExtendedAnvilAdminGui.SLOT_GLOBAL_BASE_VALUE,
                ExtendedAnvilAdminGui.SLOT_ADD_PER_ENCHANT_VALUE,
                ExtendedAnvilAdminGui.SLOT_ADD_PER_STORED_LEVEL_VALUE,
                ExtendedAnvilAdminGui.SLOT_PRIOR_WORK_COST_VALUE,
                ExtendedAnvilAdminGui.SLOT_PRIOR_WORK_INC_VALUE
        };
    }

    // -------------------------
    // PRIORITY GUIS (Bedrock-friendly)
    // -------------------------

    private void handlePriorityListClick(InventoryClickEvent e, Player player) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        if (raw == ExtendedAnvilPriorityGui.SLOT_BACK) {
            adminGui.open(player);
            return;
        }

        if (raw >= 45) return;
        if (raw >= config.getPriority().size()) return;

        String key = config.getPriority().get(raw);
        priorityGui.openEdit(player, key);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    private void handlePriorityEditClick(InventoryClickEvent e, Player player, ExtendedAnvilHolder holder) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        String key = holder.getContextKey();
        if (key == null) {
            priorityGui.open(player);
            return;
        }

        int idx = config.getPriority().indexOf(key);
        if (idx < 0) {
            priorityGui.open(player);
            return;
        }

        if (raw == ExtendedAnvilPriorityGui.SLOT_EDIT_BACK) {
            priorityGui.open(player);
            return;
        }

        if (raw == ExtendedAnvilPriorityGui.SLOT_MOVE_UP) {
            config.movePriority(key, idx - 1);
        } else if (raw == ExtendedAnvilPriorityGui.SLOT_MOVE_DOWN) {
            config.movePriority(key, idx + 1);
        } else if (raw == ExtendedAnvilPriorityGui.SLOT_MOVE_TOP) {
            config.movePriority(key, 0);
        } else if (raw == ExtendedAnvilPriorityGui.SLOT_MOVE_BOTTOM) {
            config.movePriority(key, config.getPriority().size() - 1);
        } else {
            return;
        }

        // redraw
        priorityGui.drawEdit(e.getInventory(), key);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    // -------------------------
    // ENCHANT COST GUIS (Bedrock-friendly)
    // -------------------------

    private void handleCostListClick(InventoryClickEvent e, Player player, ExtendedAnvilHolder holder) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            player.closeInventory();
            return;
        }

        int page = holder.getPage();

        if (raw == ExtendedAnvilEnchantCostGui.SLOT_BACK) {
            adminGui.open(player);
            return;
        }
        if (raw == ExtendedAnvilEnchantCostGui.SLOT_PREV) {
            int next = Math.max(0, page - 1);
            costGui.openList(player, next);
            return;
        }
        if (raw == ExtendedAnvilEnchantCostGui.SLOT_NEXT) {
            costGui.openList(player, page + 1);
            return;
        }

        if (raw >= 45) return;

        // Get sorted list, select based on page and slot
        List<String> keys = new ArrayList<>(config.getEnchantBaseCostMap().keySet());
        Collections.sort(keys);

        int idx = page * 45 + raw;
        if (idx < 0 || idx >= keys.size()) return;

        String key = keys.get(idx);
        costGui.openEdit(player, key);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    private void handleCostEditClick(InventoryClickEvent e, Player player, ExtendedAnvilHolder holder) {
        int raw = e.getRawSlot();
        if (raw < 0) return;
        if (raw >= e.getInventory().getSize()) {
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
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
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            return;
        }

        boolean changed = false;

        // Base cost buttons
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

        // Per-level buttons
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
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
        }
    }

    // -------------------------
    // Small inventory accessor
    // -------------------------

    private static final class InventoryAccessor implements ExtendedAnvilUtil.InventoryViewAccessor {
        private final Inventory inv;
        private InventoryAccessor(Inventory inv) { this.inv = inv; }
        @Override public ItemStack getItem(int slot) { return inv.getItem(slot); }
        @Override public void setItem(int slot, ItemStack item) { inv.setItem(slot, item); }
    }
}
