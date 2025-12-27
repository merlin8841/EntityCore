package com.entitycore.modules.extendedanvil;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class ExtendedAnvilListener implements Listener {

    private final Plugin plugin;
    private final ExtendedAnvilConfig config;
    private final ExtendedAnvilService service;
    private final ExtendedAnvilGui playerGui;
    private final ExtendedAnvilAdminGui adminGui;
    private final ExtendedAnvilPriorityGui priorityGui;

    public ExtendedAnvilListener(
            Plugin plugin,
            ExtendedAnvilConfig config,
            ExtendedAnvilService service,
            ExtendedAnvilGui playerGui,
            ExtendedAnvilAdminGui adminGui,
            ExtendedAnvilPriorityGui priorityGui
    ) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
        this.playerGui = playerGui;
        this.adminGui = adminGui;
        this.priorityGui = priorityGui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof ExtendedAnvilHolder holder)) return;

        if (holder.getType() == ExtendedAnvilHolder.Type.PLAYER) {
            handlePlayerGuiClick(e, player);
        } else if (holder.getType() == ExtendedAnvilHolder.Type.ADMIN) {
            handleAdminGuiClick(e, player);
        } else if (holder.getType() == ExtendedAnvilHolder.Type.PRIORITY) {
            handlePriorityGuiClick(e, player);
        }
    }

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

        if (enchantedBook != null) {
            ExtendedAnvilService.ApplyResult res = service.applyFromBook(player, target, enchantedBook);
            if (!res.ok()) {
                player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RESET + res.message());
                return;
            }

            removeOneMatchingEnchantedBook(inv, ExtendedAnvilGui.BOOKS_FROM, ExtendedAnvilGui.BOOKS_TO);

            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RESET + res.message());
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
            return;
        }

        if (emptyBooks <= 0) {
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RED
                    + "Put empty books (for disenchant) or an enchanted book (to apply/combine) in the book slots.");
            return;
        }

        boolean removeAll = emptyBooks == 1;
        ExtendedAnvilUtil.takeOneEmptyBook(accessor, ExtendedAnvilGui.BOOKS_FROM, ExtendedAnvilGui.BOOKS_TO);

        ExtendedAnvilService.DisenchantResult res = service.disenchant(player, target, removeAll);
        if (!res.ok()) {
            giveBookBack(inv);
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.RESET + res.message());
            return;
        }

        ItemStack book = res.book();
        var leftovers = player.getInventory().addItem(book);
        if (!leftovers.isEmpty()) leftovers.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));

        if (res.refundLevels() > 0) {
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.GREEN
                    + "Disenchanted " + res.removedCount() + " enchant(s). Refunded " + res.refundLevels() + " level(s).");
        } else {
            player.sendMessage(ChatColor.DARK_PURPLE + "[EA] " + ChatColor.GREEN
                    + "Disenchanted " + res.removedCount() + " enchant(s).");
        }

        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.2f);
    }

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

        ClickType click = e.getClick();
        int deltaBig = click.isLeftClick() ? 5 : (click.isRightClick() ? -5 : 0);
        int deltaSmall = click.isShiftClick() ? (click.isLeftClick() ? 1 : (click.isRightClick() ? -1 : 0)) : 0;
        int delta = (deltaSmall != 0) ? deltaSmall : deltaBig;

        switch (raw) {
            case ExtendedAnvilAdminGui.SLOT_REFUND_FIRST -> config.setRefundPercentFirst(config.getRefundPercentFirst() + delta);
            case ExtendedAnvilAdminGui.SLOT_REFUND_SECOND -> config.setRefundPercentSecond(config.getRefundPercentSecond() + delta);
            case ExtendedAnvilAdminGui.SLOT_REFUND_LATER -> config.setRefundPercentLater(config.getRefundPercentLater() + delta);
            case ExtendedAnvilAdminGui.SLOT_REFUND_LEVELS_PER -> config.setRefundLevelsPerEnchantLevel(config.getRefundLevelsPerEnchantLevel() + delta);

            case ExtendedAnvilAdminGui.SLOT_CURSES -> config.setAllowCurseRemoval(!config.isAllowCurseRemoval());

            case ExtendedAnvilAdminGui.SLOT_APPLY_BASE -> config.setApplyCostBaseLevels(config.getApplyCostBaseLevels() + delta);
            case ExtendedAnvilAdminGui.SLOT_APPLY_PER_ENCHANT -> config.setApplyCostPerEnchant(config.getApplyCostPerEnchant() + delta);
            case ExtendedAnvilAdminGui.SLOT_APPLY_PER_LEVEL -> config.setApplyCostPerStoredLevel(config.getApplyCostPerStoredLevel() + delta);

            case ExtendedAnvilAdminGui.SLOT_PRIORITY -> {
                priorityGui.open(player);
                return;
            }
            case ExtendedAnvilAdminGui.SLOT_SAVE -> {
                config.save();
                player.sendMessage(ChatColor.GREEN + "[EA] Saved extendedanvil.yml");
            }
            case ExtendedAnvilAdminGui.SLOT_RESET -> {
                ExtendedAnvilConfig fresh = new ExtendedAnvilConfig((org.bukkit.plugin.java.JavaPlugin) plugin);
                fresh.load();

                config.setRefundPercentFirst(fresh.getRefundPercentFirst());
                config.setRefundPercentSecond(fresh.getRefundPercentSecond());
                config.setRefundPercentLater(fresh.getRefundPercentLater());
                config.setRefundLevelsPerEnchantLevel(fresh.getRefundLevelsPerEnchantLevel());
                config.setAllowCurseRemoval(fresh.isAllowCurseRemoval());

                config.setApplyCostBaseLevels(fresh.getApplyCostBaseLevels());
                config.setApplyCostPerEnchant(fresh.getApplyCostPerEnchant());
                config.setApplyCostPerStoredLevel(fresh.getApplyCostPerStoredLevel());

                config.getPriority().clear();
                config.getPriority().addAll(fresh.getPriority());

                config.save();
                player.sendMessage(ChatColor.GREEN + "[EA] Reset to defaults.");
            }
            default -> { }
        }

        adminGui.refresh(e.getInventory());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    private void handlePriorityGuiClick(InventoryClickEvent e, Player player) {
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

        int newIndex;
        if (e.getClick().isShiftClick()) {
            if (e.getClick().isLeftClick()) newIndex = 0;
            else if (e.getClick().isRightClick()) newIndex = config.getPriority().size() - 1;
            else return;
        } else {
            if (e.getClick().isLeftClick()) newIndex = raw - 1;
            else if (e.getClick().isRightClick()) newIndex = raw + 1;
            else return;
        }

        config.movePriority(key, newIndex);
        priorityGui.draw(e.getInventory());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExtendedAnvilHolder holder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        if (holder.getType() != ExtendedAnvilHolder.Type.PLAYER) return;

        Inventory inv = e.getInventory();
        ItemStack item = inv.getItem(ExtendedAnvilGui.SLOT_ITEM);
        if (item != null && !item.getType().isAir()) {
            var left = player.getInventory().addItem(item);
            if (!left.isEmpty()) left.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
            inv.setItem(ExtendedAnvilGui.SLOT_ITEM, null);
        }

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

    private static final class InventoryAccessor implements ExtendedAnvilUtil.InventoryViewAccessor {
        private final Inventory inv;
        private InventoryAccessor(Inventory inv) { this.inv = inv; }
        @Override public ItemStack getItem(int slot) { return inv.getItem(slot); }
        @Override public void setItem(int slot, ItemStack item) { inv.setItem(slot, item); }
    }
}
