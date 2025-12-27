package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.AnvilInventory;
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

    // Track *the exact inventory instance* we opened via /ea for each player.
    // This avoids Bedrock title issues and avoids catching normal anvils.
    private final Map<UUID, Inventory> playerEaInv = new HashMap<>();

    // Track caps page per player (admin UI)
    private final Map<UUID, Integer> capsPage = new HashMap<>();

    // Track which admin GUI is open
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

    /* =========================================================
       OPEN MENUS
       ========================================================= */

    public void openPlayerMenu(Player player) {
        Inventory inv = PlayerMenu.create(player);

        // Store THIS inventory as the session
        playerEaInv.put(player.getUniqueId(), inv);
        openGui.put(player.getUniqueId(), GuiType.PLAYER);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.0f);

        // Force a prepare pass next tick so Bedrock sees output/cost
        Bukkit.getScheduler().runTask(plugin, () -> forcePrepare(inv));
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
       HELPERS
       ========================================================= */

    private boolean isPlayerEaInventory(Player player, Inventory top) {
        Inventory ours = playerEaInv.get(player.getUniqueId());
        return ours != null && ours == top;
    }

    private void forcePrepare(Inventory inv) {
        if (inv instanceof AnvilInventory anvil) {
            // Remove the 39 clamp ALWAYS for /ea GUI
            anvil.setMaximumRepairCost(999999);
            // Trigger client refresh
            anvil.setRepairCost(Math.max(0, anvil.getRepairCost()));
        }
    }

    private void msgNo(Player p, String msg) {
        p.sendMessage("§e" + msg);
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    /* =========================================================
       EVENTS
       ========================================================= */

    public void handleClose(Player player, InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();

        // Only clear if they closed OUR exact /ea inventory instance
        if (isPlayerEaInventory(player, top)) {
            playerEaInv.remove(player.getUniqueId());
            openGui.remove(player.getUniqueId());
        } else {
            // closing admin GUIs
            openGui.remove(player.getUniqueId());
        }
    }

    public void handleDrag(Player player, InventoryDragEvent event) {
        GuiType t = openGui.get(player.getUniqueId());
        if (t == null) return;

        if (t == GuiType.PLAYER) {
            Inventory top = event.getView().getTopInventory();
            if (!isPlayerEaInventory(player, top)) return;

            // Block dragging into output slot
            for (int raw : event.getRawSlots()) {
                if (raw == PlayerMenu.SLOT_OUTPUT) {
                    event.setCancelled(true);
                    return;
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> forcePrepare(top));
            return;
        }

        // admin GUIs: no dragging
        if (t == GuiType.ADMIN && AdminMenu.isThis(event.getView())) event.setCancelled(true);
        if (t == GuiType.PRIORITY && PriorityMenu.isThis(event.getView())) event.setCancelled(true);
        if (t == GuiType.CAPS && CapsMenu.isThis(event.getView())) event.setCancelled(true);
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        GuiType t = openGui.get(player.getUniqueId());
        if (t == null) return;

        if (t == GuiType.PLAYER) {
            Inventory top = event.getView().getTopInventory();
            if (!isPlayerEaInventory(player, top)) return;

            handlePlayerEaClick(player, event);
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

    /**
     * THIS is what makes Bedrock behave: drive result via PrepareAnvilEvent,
     * and on clicks, only intercept when our modes are active.
     */
    private void handlePlayerEaClick(Player player, InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();

        // Always remove 39 clamp for this GUI
        forcePrepare(top);

        // Bedrock safety: block shift-clicks entirely in our GUI
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();

        // If click is in player inventory (bottom), allow normal (non-shift)
        if (raw >= top.getSize()) return;

        // If they click result slot:
        if (raw == PlayerMenu.SLOT_OUTPUT) {
            PlayerMenu.Mode mode = PlayerMenu.inferMode(top);

            // If mode NONE, let vanilla anvil do repair/merge/rename (but with clamp removed)
            if (mode == PlayerMenu.Mode.NONE) {
                // Do NOT cancel. Vanilla will take over.
                return;
            }

            // Otherwise, our custom actions
            event.setCancelled(true);

            ItemStack out = top.getItem(PlayerMenu.SLOT_OUTPUT);
            if (out == null || out.getType() == Material.AIR) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            if (mode == PlayerMenu.Mode.DISENCHANT) {
                performDisenchant(player, top);
            } else if (mode == PlayerMenu.Mode.APPLY) {
                performApply(player, top);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }

            Bukkit.getScheduler().runTask(plugin, () -> forcePrepare(top));
            return;
        }

        // For any input click, just force a prepare after changes apply
        Bukkit.getScheduler().runTask(plugin, () -> forcePrepare(top));
    }

    /* =========================================================
       PrepareAnvilEvent: sets output + cost for our custom modes
       ========================================================= */

    public void handlePrepare(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        if (inv == null) return;

        // Check if this anvil inventory belongs to one of our /ea sessions
        Player viewer = null;
        for (UUID id : playerEaInv.keySet()) {
            Inventory ours = playerEaInv.get(id);
            if (ours == inv) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getOpenInventory() != null && p.getOpenInventory().getTopInventory() == inv) {
                    viewer = p;
                }
                break;
            }
        }
        if (viewer == null) return; // not our GUI

        // Always uncap for /ea GUI (prevents Too Expensive)
        inv.setMaximumRepairCost(999999);

        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack right = inv.getItem(PlayerMenu.SLOT_BOOK);

        PlayerMenu.Mode mode = PlayerMenu.inferMode(inv);

        // If NONE, we let vanilla do repair/merge/rename; but clamp is removed above.
        if (mode == PlayerMenu.Mode.NONE) {
            return;
        }

        // Our modes override vanilla output
        if (item == null || item.getType() == Material.AIR) {
            event.setResult(null);
            inv.setRepairCost(0);
            return;
        }

        if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) {
            event.setResult(errorItem("Can't use books as item"));
            inv.setRepairCost(0);
            return;
        }

        if (mode == PlayerMenu.Mode.DISENCHANT) {
            if (right == null || right.getType() != Material.BOOK || right.getAmount() <= 0) {
                event.setResult(null);
                inv.setRepairCost(0);
                return;
            }

            Map<Enchantment, Integer> ench = item.getEnchantments();
            if (ench == null || ench.isEmpty()) {
                event.setResult(errorItem("No enchantments"));
                inv.setRepairCost(0);
                return;
            }

            boolean removeAll = (right.getAmount() == 1);

            LinkedHashMap<Enchantment, Integer> toRemove;
            if (removeAll) {
                toRemove = PlayerMenu.sortedAllForBook(ench);
            } else {
                Enchantment chosen = config.chooseNextDisenchant(ench.keySet());
                if (chosen == null) {
                    event.setResult(errorItem("No target enchant"));
                    inv.setRepairCost(0);
                    return;
                }
                toRemove = new LinkedHashMap<>();
                toRemove.put(chosen, ench.get(chosen));
            }

            ItemStack outBook = PlayerMenu.buildEnchantedBook(toRemove);
            ItemMeta meta = outBook.getItemMeta();
            if (meta != null) {
                meta.setLore(List.of(
                        "§7Mode: §bDisenchant",
                        "§7Consumes: §f1 book",
                        removeAll ? "§7Removes: §fall enchants" : "§7Removes: §fone enchant (priority)"
                ));
                outBook.setItemMeta(meta);
            }

            event.setResult(outBook);
            inv.setRepairCost(1);
            return;
        }

        if (mode == PlayerMenu.Mode.APPLY) {
            if (right == null || right.getType() != Material.ENCHANTED_BOOK) {
                event.setResult(null);
                inv.setRepairCost(0);
                return;
            }

            EnchantCostService.ApplyPreview preview = costService.previewApply(viewer, item, right);
            if (!preview.canApply || preview.result == null || preview.result.getType() == Material.AIR) {
                event.setResult(errorItem("Nothing to apply"));
                inv.setRepairCost(0);
                return;
            }

            // Show the true vanilla-scaled cost, no clamp
            inv.setRepairCost(Math.max(1, preview.levelCost));

            ItemStack out = preview.result.clone();
            ItemMeta meta = out.getItemMeta();
            if (meta != null) {
                meta.setLore(List.of(
                        "§7Mode: §dApply",
                        "§7Cost: §f" + preview.levelCost + " levels",
                        "§7(Uses vanilla scaling)",
                        "§7Click result to craft."
                ));
                out.setItemMeta(meta);
            }

            event.setResult(out);
        }
    }

    /* =========================================================
       CUSTOM ACTIONS
       ========================================================= */

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
        player.updateInventory();
    }

    private static ItemStack errorItem(String msg) {
        ItemStack it = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c" + msg);
            meta.setLore(List.of("§7Fix inputs and try again."));
            it.setItemMeta(meta);
        }
        return it;
    }

    /* =========================================================
       ADMIN MENUS (same logic as before)
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
