package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilSessionManager {

    private final JavaPlugin plugin;
    private final ExtendedAnvilConfig cfg;
    private final EnchantCostService costService;
    private final XpRefundService refundService;

    private final Set<Inventory> playerMenus = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Inventory> adminMenus = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Inventory> capsMenus = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Inventory> priorityMenus = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Map<UUID, Integer> capsPage = new HashMap<>();

    public ExtendedAnvilSessionManager(JavaPlugin plugin,
                                      ExtendedAnvilConfig cfg,
                                      EnchantCostService costService,
                                      XpRefundService refundService) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.costService = costService;
        this.refundService = refundService;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void shutdownAll() {
        playerMenus.clear();
        adminMenus.clear();
        capsMenus.clear();
        priorityMenus.clear();
        capsPage.clear();
    }

    public boolean isAnyEaInventory(InventoryView view) {
        if (view == null) return false;
        Inventory top = view.getTopInventory();
        return playerMenus.contains(top)
                || adminMenus.contains(top)
                || capsMenus.contains(top)
                || priorityMenus.contains(top);
    }

    // ===== OPEN =====

    public void openPlayer(Player player) {
        if (!player.hasPermission("entitycore.extendedanvil.use")) {
            player.sendMessage("§cYou do not have permission to use Extended Anvil.");
            return;
        }
        Inventory inv = PlayerMenu.create(player);
        playerMenus.add(inv);
        player.openInventory(inv);
        PlayerMenu.renderStatic(inv);
        refreshPlayerMenu(player, inv);
    }

    public void openAdmin(Player player) {
        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cOperator only.");
            return;
        }
        Inventory inv = AdminMenu.create(player, cfg);
        adminMenus.add(inv);
        player.openInventory(inv);
    }

    private void openCaps(Player player, int page) {
        if (!player.hasPermission("entitycore.extendedanvil.admin")) return;
        int p = Math.max(0, page);
        capsPage.put(player.getUniqueId(), p);
        Inventory inv = CapsMenu.create(player, cfg, p);
        capsMenus.add(inv);
        player.openInventory(inv);
    }

    private void openPriority(Player player) {
        if (!player.hasPermission("entitycore.extendedanvil.admin")) return;
        Inventory inv = PriorityMenu.create(player, cfg);
        priorityMenus.add(inv);
        player.openInventory(inv);
    }

    // ===== CLOSE =====

    public void handleClose(Player player, Inventory top) {
        if (playerMenus.contains(top)) {
            returnInputs(player, top);
            playerMenus.remove(top);
            return;
        }
        adminMenus.remove(top);
        capsMenus.remove(top);
        priorityMenus.remove(top);
    }

    public void forceClose(Player player) {
        InventoryView view = player.getOpenInventory();
        if (view == null) return;

        Inventory top = view.getTopInventory();
        if (playerMenus.contains(top)) {
            returnInputs(player, top);
            playerMenus.remove(top);
        }
        adminMenus.remove(top);
        capsMenus.remove(top);
        priorityMenus.remove(top);
        player.closeInventory();
    }

    // ===== DRAG =====

    public void handleDrag(Player player, InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();

        if (playerMenus.contains(top)) {
            for (int rawSlot : e.getRawSlots()) {
                if (rawSlot < top.getSize() && !PlayerMenu.isInputSlot(rawSlot)) {
                    e.setCancelled(true);
                    return;
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> refreshPlayerMenu(player, top));
            return;
        }

        if (adminMenus.contains(top) || capsMenus.contains(top) || priorityMenus.contains(top)) {
            e.setCancelled(true);
        }
    }

    // ===== CLICK =====

    public void handleClick(Player player, InventoryClickEvent e) {
        InventoryView view = e.getView();
        Inventory top = view.getTopInventory();
        int raw = e.getRawSlot();

        // ========= PLAYER MENU =========
        if (playerMenus.contains(top)) {

            // prevent double-click & hotbar-swap into GUI (common desync triggers)
            if (e.getClick() == ClickType.DOUBLE_CLICK
                    || e.getClick() == ClickType.NUMBER_KEY
                    || e.getAction() == InventoryAction.HOTBAR_SWAP
                    || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
                e.setCancelled(true);
                return;
            }

            // Player inventory area
            if (raw >= top.getSize()) {
                if (e.isShiftClick()) {
                    ItemStack moving = e.getCurrentItem();
                    if (moving == null || moving.getType() == Material.AIR) return;

                    e.setCancelled(true);
                    if (tryShiftIntoInputs(top, moving.clone())) {
                        e.setCurrentItem(null);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                        Bukkit.getScheduler().runTask(plugin, () -> refreshPlayerMenu(player, top));
                    }
                }
                return;
            }

            // Top inventory area
            if (raw == PlayerMenu.SLOT_OUTPUT) {
                e.setCancelled(true);
                completeCraft(player, top);
                return;
            }

            if (raw == PlayerMenu.SLOT_CLOSE) {
                e.setCancelled(true);
                player.closeInventory();
                return;
            }

            // allow only input slots
            if (PlayerMenu.isInputSlot(raw)) {
                // allow normal interaction
                Bukkit.getScheduler().runTask(plugin, () -> refreshPlayerMenu(player, top));
                return;
            }

            // block filler clicks
            e.setCancelled(true);
            return;
        }

        // ========= ADMIN MENU =========
        if (adminMenus.contains(top)) {
            e.setCancelled(true);
            AdminMenu.Click click = AdminMenu.getClicked(raw);
            if (click == null) return;

            switch (click) {
                case CLOSE:
                    player.closeInventory();
                    return;
                case CAPS:
                    openCaps(player, capsPage.getOrDefault(player.getUniqueId(), 0));
                    return;
                case PRIORITY:
                    openPriority(player);
                    return;
                case TOGGLE_REFUNDS:
                    cfg.setRefundsEnabled(!cfg.isRefundsEnabled());
                    cfg.save();
                    Inventory inv = AdminMenu.create(player, cfg);
                    adminMenus.add(inv);
                    player.openInventory(inv);
                    return;
                default:
                    return;
            }
        }

        // ========= CAPS MENU =========
        if (capsMenus.contains(top)) {
            e.setCancelled(true);

            int page = capsPage.getOrDefault(player.getUniqueId(), 0);
            CapsMenu.Click click = CapsMenu.getClicked(top, raw, page);
            if (click == null) return;

            if (click.type == CapsMenu.ClickType.BACK) {
                openAdmin(player);
                return;
            }

            if (click.type == CapsMenu.ClickType.PAGE) {
                openCaps(player, click.page);
                return;
            }

            if (click.type == CapsMenu.ClickType.ENTRY && click.enchantKey != null) {
                int delta = 0;
                boolean shift = e.isShiftClick();
                boolean right = e.isRightClick();

                if (!right && !shift) delta = -1;
                if (right && !shift) delta = +1;
                if (!right && shift) delta = -10;
                if (right && shift) delta = +10;

                cfg.adjustCap(click.enchantKey, delta);
                cfg.save();
                CapsMenu.render(top, cfg, page);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            }
            return;
        }

        // ========= PRIORITY MENU =========
        if (priorityMenus.contains(top)) {
            e.setCancelled(true);

            PriorityMenu.Click click = PriorityMenu.getClicked(top, raw);
            if (click == null) return;

            if (click.type == PriorityMenu.ClickType.BACK) {
                openAdmin(player);
                return;
            }

            if (click.type == PriorityMenu.ClickType.MOVE_UP && click.index >= 0) {
                cfg.movePriority(click.index, -1);
                cfg.save();
                PriorityMenu.render(top, cfg);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                return;
            }

            if (click.type == PriorityMenu.ClickType.MOVE_DOWN && click.index >= 0) {
                cfg.movePriority(click.index, +1);
                cfg.save();
                PriorityMenu.render(top, cfg);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            }
        }
    }

    private boolean tryShiftIntoInputs(Inventory top, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;

        int[] targets = new int[]{PlayerMenu.SLOT_ITEM, PlayerMenu.SLOT_BOOK};

        for (int slot : targets) {
            ItemStack cur = top.getItem(slot);
            if (cur == null || cur.getType() == Material.AIR) {
                top.setItem(slot, stack);
                return true;
            }
        }
        return false;
    }

    private void returnInputs(Player player, Inventory top) {
        ItemStack a = top.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack b = top.getItem(PlayerMenu.SLOT_BOOK);

        top.setItem(PlayerMenu.SLOT_ITEM, null);
        top.setItem(PlayerMenu.SLOT_BOOK, null);
        top.setItem(PlayerMenu.SLOT_OUTPUT, null);

        giveOrDrop(player, a);
        giveOrDrop(player, b);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (leftovers != null && !leftovers.isEmpty()) {
            for (ItemStack it : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), it);
            }
        }
    }

    // ===== PREVIEW =====

    private void refreshPlayerMenu(Player viewer, Inventory inv) {
        if (inv == null) return;

        // never touch inputs here; ONLY output
        inv.setItem(PlayerMenu.SLOT_OUTPUT, null);

        PlayerMenu.Mode mode = PlayerMenu.inferMode(inv);
        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack right = inv.getItem(PlayerMenu.SLOT_BOOK);

        if (mode == PlayerMenu.Mode.NONE) {
            inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.errorItem("Put item + book"));
            return;
        }

        if (item == null || item.getType() == Material.AIR) {
            inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.errorItem("Place an item"));
            return;
        }

        // no book-on-book
        if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) {
            inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.errorItem("Books can't be item"));
            return;
        }

        if (mode == PlayerMenu.Mode.DISENCHANT) {
            if (right == null || right.getType() != Material.BOOK || right.getAmount() <= 0) {
                inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.errorItem("Place books"));
                return;
            }

            Map<org.bukkit.enchantments.Enchantment, Integer> ench = item.getEnchantments();
            if (ench == null || ench.isEmpty()) {
                inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.errorItem("No enchantments"));
                return;
            }

            boolean removeAll = (right.getAmount() == 1);

            LinkedHashMap<org.bukkit.enchantments.Enchantment, Integer> removed;
            if (removeAll) {
                removed = PlayerMenu.sortedAllForBook(ench);
            } else {
                org.bukkit.enchantments.Enchantment chosen = cfg.chooseNextDisenchant(ench.keySet());
                if (chosen == null || !ench.containsKey(chosen)) {
                    inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.errorItem("No target enchant"));
                    return;
                }
                removed = new LinkedHashMap<>();
                removed.put(chosen, ench.get(chosen));
            }

            inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.previewDisenchantBook(removed, removeAll));
            return;
        }

        if (mode == PlayerMenu.Mode.APPLY) {
            if (right == null || right.getType() != Material.ENCHANTED_BOOK) {
                inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.errorItem("Place enchanted book"));
                return;
            }

            EnchantCostService.ApplyPreview preview = costService.previewApply(viewer, item, right);
            if (!preview.canApply || preview.result == null || preview.result.getType() == Material.AIR) {
                inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.errorItem("Nothing to apply"));
                return;
            }

            inv.setItem(PlayerMenu.SLOT_OUTPUT, PlayerMenu.previewApplyResult(preview.result, preview.levelCost));
        }
    }

    // ===== COMMIT =====

    private void completeCraft(Player player, Inventory inv) {
        PlayerMenu.Mode mode = PlayerMenu.inferMode(inv);
        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack right = inv.getItem(PlayerMenu.SLOT_BOOK);
        ItemStack out = inv.getItem(PlayerMenu.SLOT_OUTPUT);

        if (mode == PlayerMenu.Mode.NONE) return;
        if (item == null || item.getType() == Material.AIR) return;
        if (out == null || out.getType() == Material.AIR) return;

        if (mode == PlayerMenu.Mode.DISENCHANT) {
            if (right == null || right.getType() != Material.BOOK || right.getAmount() <= 0) return;

            Map<org.bukkit.enchantments.Enchantment, Integer> ench = item.getEnchantments();
            if (ench == null || ench.isEmpty()) return;

            boolean removeAll = (right.getAmount() == 1);

            LinkedHashMap<org.bukkit.enchantments.Enchantment, Integer> removed;
            if (removeAll) {
                removed = PlayerMenu.sortedAllForBook(ench);
            } else {
                org.bukkit.enchantments.Enchantment chosen = cfg.chooseNextDisenchant(ench.keySet());
                if (chosen == null || !ench.containsKey(chosen)) return;
                removed = new LinkedHashMap<>();
                removed.put(chosen, ench.get(chosen));
            }

            // Build output
            ItemStack bookOut = PlayerMenu.buildEnchantedBook(removed);

            // Modify item IN PLACE (critical for Bedrock stability)
            ItemStack newItem = item.clone();
            for (org.bukkit.enchantments.Enchantment e : removed.keySet()) {
                newItem.removeEnchantment(e);
            }

            // consume exactly 1 book
            ItemStack newRight = right.clone();
            newRight.setAmount(right.getAmount() - 1);
            inv.setItem(PlayerMenu.SLOT_BOOK, newRight.getAmount() > 0 ? newRight : null);

            // KEEP ITEM IN GUI ALWAYS (do not give back during click)
            inv.setItem(PlayerMenu.SLOT_ITEM, newItem);

            // clear output
            inv.setItem(PlayerMenu.SLOT_OUTPUT, null);

            // give enchanted book to player
            giveOrDrop(player, bookOut);

            // refund hook (safe)
            if (cfg.isRefundsEnabled()) {
                refundService.refundForDisenchant(player, removed, newItem);
            }

            player.playSound(player.getLocation(), Sound.ANVIL_USE, 1f, 1.0f);

            Bukkit.getScheduler().runTask(plugin, () -> refreshPlayerMenu(player, inv));
            return;
        }

        if (mode == PlayerMenu.Mode.APPLY) {
            if (right == null || right.getType() != Material.ENCHANTED_BOOK) return;

            EnchantCostService.ApplyPreview preview = costService.previewApply(player, item, right);
            if (!preview.canApply || preview.result == null || preview.result.getType() == Material.AIR) return;

            int cost = Math.max(0, preview.levelCost);
            if (player.getLevel() < cost) {
                player.sendMessage("§cNot enough levels. Need " + cost + ".");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            player.setLevel(player.getLevel() - cost);

            // consume 1 enchanted book
            ItemStack newRight = right.clone();
            newRight.setAmount(right.getAmount() - 1);

            inv.setItem(PlayerMenu.SLOT_ITEM, preview.result);
            inv.setItem(PlayerMenu.SLOT_BOOK, newRight.getAmount() > 0 ? newRight : null);
            inv.setItem(PlayerMenu.SLOT_OUTPUT, null);

            player.playSound(player.getLocation(), Sound.ANVIL_USE, 1f, 1.0f);

            Bukkit.getScheduler().runTask(plugin, () -> refreshPlayerMenu(player, inv));
        }
    }

    // External calls (commands)
    public void commandOpenPlayer(Player p) { openPlayer(p); }
    public void commandOpenAdmin(Player p) { openAdmin(p); }
}
