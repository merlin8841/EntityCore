package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class ExtendedAnvilGui {

    // Single chest (Bedrock-friendly)
    private static final int SIZE = 27;
    private static final String TITLE = "Extended Anvil";

    // Slot mapping per your spec:
    // Slot 2 => index 1
    private static final int SLOT_TARGET = 1;

    // Slots 9-16 => indices 8..15 (left -> right processing queue)
    private static final int WORK_START = 8;
    private static final int WORK_END = 15;

    // Preview display
    private static final int SLOT_PREVIEW_ITEM = 4;

    // Buttons
    private static final int SLOT_PREVIEW = 24;
    private static final int SLOT_COMMIT = 25;
    private static final int SLOT_CLOSE = 26;

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
        Inventory inv = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inv);

        build(inv);
        player.openInventory(inv);

        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] Opened GUI for " + player.getName());
        }
    }

    private static void build(Inventory inv) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // Inputs
        inv.setItem(SLOT_TARGET, null);
        for (int i = WORK_START; i <= WORK_END; i++) inv.setItem(i, null);

        // Labels / preview
        inv.setItem(0, named(Material.ANVIL, "Target Item (Slot 2)"));
        inv.setItem(7, named(Material.BOOKSHELF, "Queue (Slots 9-16)"));
        inv.setItem(SLOT_PREVIEW_ITEM, named(Material.BLACK_STAINED_GLASS_PANE, "Preview"));

        // Buttons
        inv.setItem(SLOT_PREVIEW, named(Material.SPYGLASS, "Preview"));
        inv.setItem(SLOT_COMMIT, named(Material.LIME_CONCRETE, "Commit"));
        inv.setItem(SLOT_CLOSE, named(Material.BARRIER, "Close"));
    }

    public static void handleClick(Player player, InventoryClickEvent event, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;

        if (!holder.owner().getUniqueId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();

        // Block interaction with non-input slots in top inventory
        if (rawSlot < top.getSize()) {
            if (!isInputSlot(rawSlot) && rawSlot != SLOT_PREVIEW && rawSlot != SLOT_COMMIT && rawSlot != SLOT_CLOSE) {
                event.setCancelled(true);
            }
        }

        if (rawSlot == SLOT_CLOSE) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (rawSlot == SLOT_PREVIEW) {
            event.setCancelled(true);
            doPreview(player, top, plugin, config, service);
            return;
        }

        if (rawSlot == SLOT_COMMIT) {
            event.setCancelled(true);
            doCommit(player, top, plugin, config, service);
            return;
        }

        // Any change to input slots -> refresh preview next tick
        if (rawSlot < top.getSize() && isInputSlot(rawSlot)) {
            Bukkit.getScheduler().runTask(plugin, () -> doPreview(player, top, plugin, config, service));
        }
    }

    public static void handleDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;

        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize() && !isInputSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public static void handleClose(Player player, InventoryCloseEvent event, JavaPlugin plugin, ExtendedAnvilConfig config) {
        Inventory top = event.getInventory();

        // return inputs
        giveBack(player, top.getItem(SLOT_TARGET));
        top.setItem(SLOT_TARGET, null);

        for (int i = WORK_START; i <= WORK_END; i++) {
            giveBack(player, top.getItem(i));
            top.setItem(i, null);
        }

        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] Closed GUI for " + player.getName());
        }
    }

    private static boolean isInputSlot(int slot) {
        if (slot == SLOT_TARGET) return true;
        return slot >= WORK_START && slot <= WORK_END;
    }

    private static void doPreview(Player player, Inventory top, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        PreviewResult pr = simulate(top, config, service);

        if (!pr.ok) {
            top.setItem(SLOT_PREVIEW_ITEM, named(Material.RED_STAINED_GLASS_PANE, pr.error == null ? "Invalid" : pr.error));

            if (config.debug()) {
                plugin.getLogger().info("[ExtendedAnvil][DEBUG] Preview failed for " + player.getName() + ": " + pr.error);
            }

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
            return;
        }

        ItemStack preview = pr.resultItem.clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("Net: " + (pr.netLevels >= 0 ? ("-" + pr.netLevels) : ("+" + (-pr.netLevels))) + " levels");
            lore.add("Cost: " + pr.costLevels + " | Return: " + pr.returnLevels);
            lore.add("Your levels: " + player.getLevel());
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }

        top.setItem(SLOT_PREVIEW_ITEM, preview);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    private static void doCommit(Player player, Inventory top, JavaPlugin plugin, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        PreviewResult pr = simulate(top, config, service);

        if (!pr.ok) {
            if (config.debug()) {
                plugin.getLogger().info("[ExtendedAnvil][DEBUG] Commit blocked for " + player.getName() + ": " + pr.error);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int net = pr.netLevels;
        if (net > 0 && player.getLevel() < net) {
            if (config.debug()) {
                plugin.getLogger().info("[ExtendedAnvil][DEBUG] Commit blocked (levels) for " + player.getName()
                    + ": need=" + net + " have=" + player.getLevel());
            }
            player.sendMessage("Not enough levels. Need " + net + " (you have " + player.getLevel() + ").");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Apply XP change
        if (net > 0) player.setLevel(player.getLevel() - net);
        if (net < 0) player.setLevel(player.getLevel() + (-net));

        // Apply item result to target
        top.setItem(SLOT_TARGET, pr.resultItem);

        // Consume inputs as recorded
        for (Consume c : pr.consumes) {
            if (c.slot < 0 || c.slot >= top.getSize()) continue;
            ItemStack it = top.getItem(c.slot);
            if (it == null) continue;
            if (c.amount <= 0) continue;

            if (it.getAmount() <= c.amount) {
                top.setItem(c.slot, null);
            } else {
                ItemStack left = it.clone();
                left.setAmount(it.getAmount() - c.amount);
                top.setItem(c.slot, left);
            }
        }

        // Give output books
        for (ItemStack outBook : pr.outBooks) {
            if (outBook == null) continue;
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(outBook);
            for (ItemStack of : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), of);
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);

        // ✅ Player chat summary
        if (pr.netLevels > 0) {
            player.sendMessage("Extended Anvil: Cost " + pr.costLevels + " levels, Return " + pr.returnLevels + " levels (Net -" + pr.netLevels + ").");
        } else if (pr.netLevels < 0) {
            player.sendMessage("Extended Anvil: Cost " + pr.costLevels + " levels, Return " + pr.returnLevels + " levels (Net +" + (-pr.netLevels) + ").");
        } else {
            player.sendMessage("Extended Anvil: Cost " + pr.costLevels + " levels, Return " + pr.returnLevels + " levels (Net 0).");
        }

        // ✅ Debug safety log
        if (config.debug()) {
            plugin.getLogger().info("[ExtendedAnvil][DEBUG] Commit ok for " + player.getName()
                + " cost=" + pr.costLevels
                + " return=" + pr.returnLevels
                + " net=" + pr.netLevels
                + " booksOut=" + pr.outBooks.size());
        }

        // Refresh preview after commit
        Bukkit.getScheduler().runTask(plugin, () -> doPreview(player, top, plugin, config, service));
    }

    /**
     * Simulate left-to-right operations:
     * - BOOK: disenchant (1 => all, 2+ => one by priority), consumes 1 book each step
     * - ENCHANTED_BOOK or ITEM: merge enchants (keep higher, no upgrading), conflicts blocked
     * - MATERIAL: repair with material
     * - SAME ITEM: repair + merge enchants (no upgrading)
     *
     * IMPORTANT: To get correct scaling (prior-work penalty) during a queue preview,
     * we must mutate the working clone's PDC counts as we apply steps.
     */
    private static PreviewResult simulate(Inventory top, ExtendedAnvilConfig config, ExtendedAnvilService service) {
        ItemStack base = top.getItem(SLOT_TARGET);
        if (base == null || base.getType() == Material.AIR) {
            return PreviewResult.fail("Put an item in Slot 2.");
        }

        ItemStack working = base.clone();

        int cost = 0;
        int ret = 0;

        List<Consume> consumes = new ArrayList<>();
        List<ItemStack> outBooks = new ArrayList<>();

        for (int slot = WORK_START; slot <= WORK_END; slot++) {
            ItemStack op = top.getItem(slot);
            if (op == null || op.getType() == Material.AIR) continue;

            // Disenchant via BOOK stack
            if (op.getType() == Material.BOOK) {
                int count = op.getAmount();
                ExtendedAnvilService.DisenchantOpResult dr = service.disenchant(working, count, config.priorityList());
                if (!dr.ok() || dr.newItem() == null) {
                    return PreviewResult.fail(dr.error() == null ? "Disenchant failed." : dr.error());
                }

                working = dr.newItem();
                ret += dr.returnLevels();
                consumes.add(new Consume(slot, 1));
                outBooks.addAll(dr.outBooks());
                continue;
            }

            // Repair with same item
            if (op.getType() == working.getType() && op.getType() != Material.ENCHANTED_BOOK && service.isDamageable(working)) {
                ExtendedAnvilService.RepairResult rr = service.repairWithSameItem(working, op);
                if (rr.ok() && rr.newItem() != null) {
                    working = rr.newItem();
                    cost += service.computeRepairCostLevels(working);
                    service.incrementRepairCount(working);
                    consumes.add(new Consume(slot, 1));
                    continue;
                }
            }

            // Repair with material
            if (service.isDamageable(working)) {
                ExtendedAnvilService.RepairResult rr = service.repairWithMaterial(working, op);
                if (rr.ok() && rr.newItem() != null && rr.amountConsumed() > 0) {
                    working = rr.newItem();
                    cost += service.computeRepairCostLevels(working);
                    service.incrementRepairCount(working);
                    consumes.add(new Consume(slot, rr.amountConsumed()));
                    continue;
                }
            }

            // Enchant merge (book or enchant-bearing item)
            if (op.getType() == Material.ENCHANTED_BOOK || op.getType() == working.getType() || op.getItemMeta() != null) {
                ExtendedAnvilService.MergeResult mr = service.mergeInto(working, op);
                if (!mr.ok() || mr.newItem() == null) {
                    return PreviewResult.fail(mr.error() == null ? "Enchant merge failed." : mr.error());
                }
                working = mr.newItem();
                cost += mr.costLevels();
                consumes.add(new Consume(slot, 1));
                continue;
            }

            return PreviewResult.fail("Slot " + (slot + 1) + " item can't be used.");
        }

        int net = cost - ret;
        return PreviewResult.ok(working, cost, ret, net, consumes, outBooks);
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

    private record Consume(int slot, int amount) {}

    private static final class PreviewResult {
        final boolean ok;
        final String error;
        final ItemStack resultItem;
        final int costLevels;
        final int returnLevels;
        final int netLevels;
        final List<Consume> consumes;
        final List<ItemStack> outBooks;

        private PreviewResult(boolean ok, String error, ItemStack resultItem, int costLevels, int returnLevels, int netLevels,
                              List<Consume> consumes, List<ItemStack> outBooks) {
            this.ok = ok;
            this.error = error;
            this.resultItem = resultItem;
            this.costLevels = costLevels;
            this.returnLevels = returnLevels;
            this.netLevels = netLevels;
            this.consumes = consumes;
            this.outBooks = outBooks;
        }

        static PreviewResult ok(ItemStack result, int cost, int ret, int net, List<Consume> consumes, List<ItemStack> outBooks) {
            return new PreviewResult(true, null, result, cost, ret, net, consumes, outBooks);
        }

        static PreviewResult fail(String error) {
            return new PreviewResult(false, error, null, 0, 0, 0, List.of(), List.of());
        }
    }
}
