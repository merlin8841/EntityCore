package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
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

    // We identify /ea sessions by player UUID (Bedrock titles are unreliable)
    private final Set<UUID> active = new HashSet<>();

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
       OPEN
       ========================================================= */

    public void openPlayerMenu(Player player) {
        InventoryView view = PlayerMenu.open(player);
        if (view == null) {
            player.sendMessage("§cFailed to open anvil.");
            return;
        }

        // Mark as active session
        active.add(player.getUniqueId());

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.0f);

        // Force a refresh next tick (Geyser likes that)
        Bukkit.getScheduler().runTask(plugin, () -> {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top instanceof AnvilInventory anvil) {
                anvil.setMaximumRepairCost(999999);
                anvil.setRepairCost(Math.max(0, anvil.getRepairCost()));
            }
        });
    }

    /* =========================================================
       SESSION CHECK
       ========================================================= */

    private boolean isEa(Player player, InventoryView view) {
        if (player == null || view == null) return false;
        if (!active.contains(player.getUniqueId())) return false;
        return view.getTopInventory() instanceof AnvilInventory;
    }

    private void uncap(AnvilInventory anvil) {
        anvil.setMaximumRepairCost(999999);
    }

    private PlayerMenu.Mode inferMode(AnvilInventory inv) {
        ItemStack right = inv.getItem(PlayerMenu.SLOT_BOOK);
        if (right == null || right.getType() == Material.AIR) return PlayerMenu.Mode.NONE;
        if (right.getType() == Material.BOOK) return PlayerMenu.Mode.DISENCHANT;
        if (right.getType() == Material.ENCHANTED_BOOK) return PlayerMenu.Mode.APPLY;
        return PlayerMenu.Mode.NONE;
    }

    /* =========================================================
       CLOSE
       ========================================================= */

    public void handleClose(Player player, InventoryCloseEvent event) {
        if (!active.contains(player.getUniqueId())) return;
        // If they closed an anvil while active, end session
        if (event.getView().getTopInventory() instanceof AnvilInventory) {
            active.remove(player.getUniqueId());
        }
    }

    /* =========================================================
       DRAG/CLICK
       ========================================================= */

    public void handleDrag(Player player, InventoryDragEvent event) {
        if (!isEa(player, event.getView())) return;

        // Block dragging into result slot
        if (event.getRawSlots().contains(PlayerMenu.SLOT_OUTPUT)) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getView().getTopInventory() instanceof AnvilInventory anvil) {
                uncap(anvil);
            }
        });
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        if (!isEa(player, event.getView())) return;

        AnvilInventory anvil = (AnvilInventory) event.getView().getTopInventory();
        uncap(anvil);

        // No shift-click in our /ea anvil (Bedrock gets weird)
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();

        // Allow normal clicks in bottom inventory (except shift)
        if (raw >= anvil.getSize()) return;

        // Result slot logic
        if (raw == PlayerMenu.SLOT_OUTPUT) {
            PlayerMenu.Mode mode = inferMode(anvil);

            // If not our modes, let vanilla do repair/combine/rename (but uncapped)
            if (mode == PlayerMenu.Mode.NONE) {
                return; // don't cancel
            }

            event.setCancelled(true);

            ItemStack result = anvil.getItem(PlayerMenu.SLOT_OUTPUT);
            if (result == null || result.getType() == Material.AIR) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            if (mode == PlayerMenu.Mode.DISENCHANT) {
                performDisenchant(player, anvil);
            } else if (mode == PlayerMenu.Mode.APPLY) {
                performApply(player, anvil);
            }

            Bukkit.getScheduler().runTask(plugin, () -> uncap(anvil));
            return;
        }

        // Any input click: force refresh next tick
        Bukkit.getScheduler().runTask(plugin, () -> uncap(anvil));
    }

    /* =========================================================
       PREPARE (this drives the result slot reliably for Bedrock)
       ========================================================= */

    public void handlePrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) return;

        // Find an active player viewing THIS anvil
        Player viewer = null;
        for (HumanEntity he : anvil.getViewers()) {
            if (he instanceof Player p && active.contains(p.getUniqueId())) {
                viewer = p;
                break;
            }
        }
        if (viewer == null) return; // not our /ea session

        uncap(anvil);

        ItemStack item = anvil.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack right = anvil.getItem(PlayerMenu.SLOT_BOOK);

        PlayerMenu.Mode mode = inferMode(anvil);

        // If NONE: allow vanilla output, just uncapped
        if (mode == PlayerMenu.Mode.NONE) {
            return;
        }

        // Override vanilla output for our two modes
        if (item == null || item.getType() == Material.AIR) {
            event.setResult(null);
            anvil.setRepairCost(0);
            return;
        }

        if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) {
            event.setResult(errorItem("Can't use books as item"));
            anvil.setRepairCost(0);
            return;
        }

        if (mode == PlayerMenu.Mode.DISENCHANT) {
            if (right == null || right.getType() != Material.BOOK || right.getAmount() <= 0) {
                event.setResult(null);
                anvil.setRepairCost(0);
                return;
            }

            Map<Enchantment, Integer> ench = item.getEnchantments();
            if (ench == null || ench.isEmpty()) {
                event.setResult(errorItem("No enchantments"));
                anvil.setRepairCost(0);
                return;
            }

            boolean removeAll = (right.getAmount() == 1);

            LinkedHashMap<Enchantment, Integer> toRemove;
            if (removeAll) {
                toRemove = sortedAllForBook(ench);
            } else {
                Enchantment chosen = config.chooseNextDisenchant(ench.keySet());
                if (chosen == null) {
                    event.setResult(errorItem("No target enchant"));
                    anvil.setRepairCost(0);
                    return;
                }
                toRemove = new LinkedHashMap<>();
                toRemove.put(chosen, ench.get(chosen));
            }

            ItemStack outBook = buildEnchantedBook(toRemove);
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
            anvil.setRepairCost(1);
            return;
        }

        // APPLY
        if (mode == PlayerMenu.Mode.APPLY) {
            if (right == null || right.getType() != Material.ENCHANTED_BOOK) {
                event.setResult(null);
                anvil.setRepairCost(0);
                return;
            }

            EnchantCostService.ApplyPreview preview = costService.previewApply(viewer, item, right);
            if (!preview.canApply || preview.result == null || preview.result.getType() == Material.AIR) {
                event.setResult(errorItem("Nothing to apply"));
                anvil.setRepairCost(0);
                return;
            }

            event.setResult(preview.result.clone());
            anvil.setRepairCost(Math.max(1, preview.levelCost)); // show real cost (no clamp)
        }
    }

    /* =========================================================
       ACTIONS
       ========================================================= */

    private void performDisenchant(Player player, AnvilInventory inv) {
        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack books = inv.getItem(PlayerMenu.SLOT_BOOK);

        if (item == null || item.getType() == Material.AIR) return;
        if (books == null || books.getType() != Material.BOOK || books.getAmount() <= 0) return;

        Map<Enchantment, Integer> ench = item.getEnchantments();
        if (ench == null || ench.isEmpty()) return;

        boolean removeAll = (books.getAmount() == 1);

        LinkedHashMap<Enchantment, Integer> toRemove;
        if (removeAll) {
            toRemove = sortedAllForBook(ench);
        } else {
            Enchantment chosen = config.chooseNextDisenchant(ench.keySet());
            if (chosen == null) return;
            toRemove = new LinkedHashMap<>();
            toRemove.put(chosen, ench.get(chosen));
        }

        ItemStack outBook = buildEnchantedBook(toRemove);

        // give book
        if (!giveToPlayer(player, outBook)) {
            player.sendMessage("§cNo inventory space.");
            return;
        }

        // consume exactly 1 book
        books.setAmount(books.getAmount() - 1);
        if (books.getAmount() <= 0) inv.setItem(PlayerMenu.SLOT_BOOK, null);
        else inv.setItem(PlayerMenu.SLOT_BOOK, books);

        // remove enchants from item
        ItemStack newItem = item.clone();
        ItemMeta meta = newItem.getItemMeta();
        if (meta != null) {
            for (Enchantment e : toRemove.keySet()) meta.removeEnchant(e);
            newItem.setItemMeta(meta);
        }
        inv.setItem(PlayerMenu.SLOT_ITEM, newItem);

        refundService.refundForRemoval(player, newItem, toRemove);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
        player.updateInventory();
    }

    private void performApply(Player player, AnvilInventory inv) {
        ItemStack item = inv.getItem(PlayerMenu.SLOT_ITEM);
        ItemStack book = inv.getItem(PlayerMenu.SLOT_BOOK);

        if (item == null || item.getType() == Material.AIR) return;
        if (book == null || book.getType() != Material.ENCHANTED_BOOK) return;

        EnchantCostService.ApplyPreview preview = costService.previewApply(player, item, book);
        if (!preview.canApply || preview.result == null) return;

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (!creative && player.getLevel() < preview.levelCost) {
            player.sendMessage("§cNot enough levels. Need: " + preview.levelCost);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!giveToPlayer(player, preview.result.clone())) {
            player.sendMessage("§cNo inventory space.");
            return;
        }

        // consume 1 book
        book.setAmount(book.getAmount() - 1);
        if (book.getAmount() <= 0) inv.setItem(PlayerMenu.SLOT_BOOK, null);
        else inv.setItem(PlayerMenu.SLOT_BOOK, book);

        // consume item
        inv.setItem(PlayerMenu.SLOT_ITEM, null);

        if (!creative && preview.levelCost > 0) player.giveExpLevels(-preview.levelCost);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
        player.updateInventory();
    }

    /* =========================================================
       UTIL
       ========================================================= */

    private static LinkedHashMap<Enchantment, Integer> sortedAllForBook(Map<Enchantment, Integer> ench) {
        List<Enchantment> list = new ArrayList<>(ench.keySet());
        list.sort(Comparator.comparing(a -> a.getKey().toString()));
        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<>();
        for (Enchantment e : list) out.put(e, ench.get(e));
        return out;
    }

    private static ItemStack buildEnchantedBook(LinkedHashMap<Enchantment, Integer> enchants) {
        return PlayerMenuHelper.buildEnchantedBook(enchants);
    }

    private static boolean giveToPlayer(Player player, ItemStack item) {
        return player.getInventory().addItem(item).isEmpty();
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
}
