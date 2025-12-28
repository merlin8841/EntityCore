package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public final class ExtendedAnvilGui {

    // 6 rows
    private static final int SIZE = 54;

    // Enchanting area
    private static final int SLOT_ENCHANT_ITEM = 20;
    private static final int SLOT_ENCHANT_BOOK = 22;
    private static final int SLOT_ENCHANT_RESULT = 24;
    private static final int SLOT_ENCHANT_APPLY = 29;

    // Disenchanting area
    private static final int SLOT_DISENCHANT_ITEM = 38;
    private static final int SLOT_DISENCHANT_BOOKS = 40; // empty books
    private static final int SLOT_DISENCHANT_RESULT = 42;
    private static final int SLOT_DISENCHANT_APPLY = 47;

    private static final int SLOT_CLOSE = 49;

    private ExtendedAnvilGui() {}

    public static final class Holder implements InventoryHolder {
        private final Player owner;
        private Inventory inv;

        public Holder(Player owner) {
            this.owner = owner;
        }

        public Player owner() {
            return owner;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        public void setInventory(Inventory inv) {
            this.inv = inv;
        }
    }

    public static void open(Player player, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        Holder holder = new Holder(player);
        Inventory inv = Bukkit.createInventory(holder, SIZE, "Extended Anvil");
        holder.setInventory(inv);

        build(inv);

        player.openInventory(inv);

        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] Opened GUI for " + player.getName());
        }
    }

    private static void build(Inventory inv) {
        // Fill background
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // Clear interactive slots
        inv.setItem(SLOT_ENCHANT_ITEM, null);
        inv.setItem(SLOT_ENCHANT_BOOK, null);
        inv.setItem(SLOT_ENCHANT_RESULT, named(Material.BLACK_STAINED_GLASS_PANE, "Result (read-only)"));
        inv.setItem(SLOT_ENCHANT_APPLY, named(Material.ANVIL, "Apply Enchant"));

        inv.setItem(SLOT_DISENCHANT_ITEM, null);
        inv.setItem(SLOT_DISENCHANT_BOOKS, null);
        inv.setItem(SLOT_DISENCHANT_RESULT, named(Material.BLACK_STAINED_GLASS_PANE, "Result (read-only)"));
        inv.setItem(SLOT_DISENCHANT_APPLY, named(Material.GRINDSTONE, "Disenchant"));

        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, "Close"));

        // Labels
        inv.setItem(11, named(Material.ENCHANTING_TABLE, "Enchanting"));
        inv.setItem(33, named(Material.BOOKSHELF, "Disenchanting"));
    }

    public static void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;

        // Only owner can interact
        if (!holder.owner().getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();

        // Prevent taking background/decor
        if (rawSlot < top.getSize()) {
            if (rawSlot != SLOT_ENCHANT_ITEM &&
                rawSlot != SLOT_ENCHANT_BOOK &&
                rawSlot != SLOT_DISENCHANT_ITEM &&
                rawSlot != SLOT_DISENCHANT_BOOKS) {

                event.setCancelled(true);
            }
        }

        // Click on buttons
        if (rawSlot == SLOT_CLOSE) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (rawSlot == SLOT_ENCHANT_APPLY) {
            event.setCancelled(true);
            doEnchant(player, top, plugin, config, service);
            return;
        }

        if (rawSlot == SLOT_DISENCHANT_APPLY) {
            event.setCancelled(true);
            doDisenchant(player, top, plugin, config, service);
            return;
        }

        // Restrict what goes where
        if (rawSlot == SLOT_ENCHANT_BOOK) {
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            // allow normal handling, but ensure only enchanted books in this slot
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack inSlot = top.getItem(SLOT_ENCHANT_BOOK);
                if (inSlot != null && !service.isEnchantedBook(inSlot)) {
                    top.setItem(SLOT_ENCHANT_BOOK, null);
                    player.getInventory().addItem(inSlot);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                refreshPreview(player, top, config, service);
            });
            return;
        }

        if (rawSlot == SLOT_DISENCHANT_BOOKS) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack inSlot = top.getItem(SLOT_DISENCHANT_BOOKS);
                if (inSlot != null && !service.isEmptyBook(inSlot)) {
                    top.setItem(SLOT_DISENCHANT_BOOKS, null);
                    player.getInventory().addItem(inSlot);
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                refreshPreview(player, top, config, service);
            });
            return;
        }

        // Refresh preview after any interaction inside top inventory
        if (rawSlot < top.getSize()) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshPreview(player, top, config, service));
        }
    }

    public static void handleDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;

        // Cancel if dragging into non-input slots
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                if (slot != SLOT_ENCHANT_ITEM &&
                    slot != SLOT_ENCHANT_BOOK &&
                    slot != SLOT_DISENCHANT_ITEM &&
                    slot != SLOT_DISENCHANT_BOOKS) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    public static void handleClose(Player player, InventoryCloseEvent event, JavaPlugin plugin, ExtendedAnvilConfig config) {
        Inventory top = event.getInventory();

        // Return any items left in input slots
        giveBack(player, top.getItem(SLOT_ENCHANT_ITEM));
        giveBack(player, top.getItem(SLOT_ENCHANT_BOOK));
        giveBack(player, top.getItem(SLOT_DISENCHANT_ITEM));
        giveBack(player, top.getItem(SLOT_DISENCHANT_BOOKS));

        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] Closed GUI for " + player.getName());
        }
    }

    private static void refreshPreview(Player player, Inventory top, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        // Enchant preview
        ItemStack item = top.getItem(SLOT_ENCHANT_ITEM);
        ItemStack book = top.getItem(SLOT_ENCHANT_BOOK);

        if (item != null && book != null && service.isEnchantedBook(book)) {
            ExtendedAnvilService.ApplyEnchantResult r = service.applyEnchantedBook(item, book);
            if (r.ok() && r.newItem() != null) {
                ItemStack preview = r.newItem().clone();
                ItemMeta meta = preview.getItemMeta();
                if (meta != null) {
                    meta.setLore(List.of(
                        "Cost: " + r.costLevels() + " levels",
                        "Your levels: " + player.getLevel()
                    ));
                    preview.setItemMeta(meta);
                }
                top.setItem(SLOT_ENCHANT_RESULT, preview);
            } else {
                top.setItem(SLOT_ENCHANT_RESULT, named(Material.RED_STAINED_GLASS_PANE, r.error() == null ? "Invalid" : r.error()));
            }
        } else {
            top.setItem(SLOT_ENCHANT_RESULT, named(Material.BLACK_STAINED_GLASS_PANE, "Result (read-only)"));
        }

        // Disenchant preview
        ItemStack disItem = top.getItem(SLOT_DISENCHANT_ITEM);
        ItemStack books = top.getItem(SLOT_DISENCHANT_BOOKS);
        int bookCount = books == null ? 0 : books.getAmount();

        if (disItem != null && bookCount >= 1 && service.isEmptyBook(books)) {
            ExtendedAnvilService.DisenchantResult r;
            if (bookCount == 1) {
                r = service.disenchantAllToOneBook(disItem, books);
            } else {
                r = service.disenchantOneByPriority(disItem, bookCount, config.priorityList());
            }

            if (r.ok() && r.outBook() != null) {
                ItemStack preview = r.outBook().clone();
                ItemMeta meta = preview.getItemMeta();
                if (meta != null) {
                    meta.setLore(List.of(
                        "Return: " + r.returnLevels() + " levels"
                    ));
                    preview.setItemMeta(meta);
                }
                top.setItem(SLOT_DISENCHANT_RESULT, preview);
            } else {
                top.setItem(SLOT_DISENCHANT_RESULT, named(Material.RED_STAINED_GLASS_PANE, r.error() == null ? "Invalid" : r.error()));
            }
        } else {
            top.setItem(SLOT_DISENCHANT_RESULT, named(Material.BLACK_STAINED_GLASS_PANE, "Result (read-only)"));
        }
    }

    private static void doEnchant(Player player, Inventory top, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        ItemStack item = top.getItem(SLOT_ENCHANT_ITEM);
        ItemStack book = top.getItem(SLOT_ENCHANT_BOOK);

        if (item == null || book == null || !service.isEnchantedBook(book)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        ExtendedAnvilService.ApplyEnchantResult r = service.applyEnchantedBook(item, book);
        if (!r.ok() || r.newItem() == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int cost = r.costLevels();
        if (player.getLevel() < cost) {
            player.sendMessage("Not enough levels.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Pay
        player.setLevel(player.getLevel() - cost);

        // Consume book
        top.setItem(SLOT_ENCHANT_BOOK, null);

        // Replace item
        top.setItem(SLOT_ENCHANT_ITEM, r.newItem());

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);

        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] " + player.getName() + " applied book cost=" + cost);
        }

        refreshPreview(player, top, config, service);
    }

    private static void doDisenchant(Player player, Inventory top, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        ItemStack item = top.getItem(SLOT_DISENCHANT_ITEM);
        ItemStack books = top.getItem(SLOT_DISENCHANT_BOOKS);

        if (item == null || books == null || !service.isEmptyBook(books) || books.getAmount() < 1) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int count = books.getAmount();

        ExtendedAnvilService.DisenchantResult r;
        if (count == 1) {
            r = service.disenchantAllToOneBook(item, books);
        } else {
            r = service.disenchantOneByPriority(item, count, config.priorityList());
        }

        if (!r.ok() || r.newItem() == null || r.outBook() == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Consume books
        int consume = r.booksConsumed();
        int remaining = count - consume;
        if (remaining <= 0) {
            top.setItem(SLOT_DISENCHANT_BOOKS, null);
        } else {
            ItemStack newBooks = books.clone();
            newBooks.setAmount(remaining);
            top.setItem(SLOT_DISENCHANT_BOOKS, newBooks);
        }

        // Apply item change
        top.setItem(SLOT_DISENCHANT_ITEM, r.newItem());

        // Give enchanted book output to player inventory
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(r.outBook());
        for (ItemStack of : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), of);
        }

        // Give levels back
        int give = r.returnLevels();
        player.setLevel(player.getLevel() + give);

        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);

        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] " + player.getName() + " disenchanted return=" + give + " books=" + consume);
        }

        refreshPreview(player, top, config, service);
    }

    private static ItemStack named(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static void giveBack(Player player, ItemStack item) {
        if (item == null) return;
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack of : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), of);
        }
    }
}
