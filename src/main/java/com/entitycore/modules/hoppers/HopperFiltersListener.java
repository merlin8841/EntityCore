package com.entitycore.modules.hoppers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HopperFiltersListener implements Listener {

    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    private static final int SLOT_TOGGLE = 22;
    private static final int SLOT_CLOSE  = 26;

    private static final String GUI_TITLE = ChatColor.DARK_GREEN + "Hopper Filter";

    public HopperFiltersListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    boolean isHopperBlock(Block b) {
        return b != null && b.getType() == Material.HOPPER;
    }

    // Sneak-tap hopper => open filter GUI (cross-play safe)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneakInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!p.isSneaking()) return;

        Block clicked = event.getClickedBlock();
        if (!isHopperBlock(clicked)) return;

        if (!p.hasPermission(HopperFiltersCommand.PERM_USE)) {
            p.sendMessage(ChatColor.RED + "You don't have permission.");
            return;
        }

        event.setCancelled(true);
        openFilterGui(p, clicked);
    }

    // If filter is enabled, prevent using hopper inventory as storage/bypass
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaHopperClickWhenEnabled(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.HOPPER) return;

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Hopper hopperState)) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTile(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();
        if (!HopperFilterStorage.isEnabled(ts)) return;

        // Block all interactions with hopper top inventory slots 0..4 if filter enabled
        int raw = event.getRawSlot();
        if (raw >= 0 && raw <= 4) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "This hopper is in Filter Mode. Sneak-tap or /hf to configure.");
        }
    }

    // -------------------------
    // GUI
    // -------------------------

    public void openFilterGui(Player p, Block hopperBlock) {
        Optional<TileState> tsOpt = HopperFilterStorage.getTile(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();

        Inventory gui = Bukkit.createInventory(new HopperGuiHolder(hopperBlock), 27, GUI_TITLE);

        // Load templates 0..4
        for (int i = 0; i < 5; i++) {
            String rule = HopperFilterStorage.getRule(ts, i).orElse(null);
            gui.setItem(i, ruleToDisplayItem(rule));
        }

        // Toggle button
        boolean enabled = HopperFilterStorage.isEnabled(ts);
        gui.setItem(SLOT_TOGGLE, toggleItem(enabled));

        // Close
        gui.setItem(SLOT_CLOSE, closeItem());

        p.openInventory(gui);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HopperGuiHolder holder)) return;

        if (!p.hasPermission(HopperFiltersCommand.PERM_USE)) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "You don't have permission.");
            return;
        }

        int raw = event.getRawSlot();

        // Click inside our GUI
        if (raw >= 0 && raw < top.getSize()) {
            // Toggle
            if (raw == SLOT_TOGGLE) {
                event.setCancelled(true);
                Optional<TileState> tsOpt = HopperFilterStorage.getTile(holder.hopperBlock());
                if (tsOpt.isEmpty()) return;

                TileState ts = tsOpt.get();
                boolean newEnabled = !HopperFilterStorage.isEnabled(ts);
                HopperFilterStorage.setEnabled(ts, newEnabled);

                top.setItem(SLOT_TOGGLE, toggleItem(newEnabled));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, newEnabled ? 1.2f : 0.8f);
                return;
            }

            // Close
            if (raw == SLOT_CLOSE) {
                event.setCancelled(true);
                p.closeInventory();
                return;
            }

            // Template slots 0..4
            if (raw >= 0 && raw <= 4) {
                event.setCancelled(true);

                ItemStack cursor = event.getCursor();
                ItemStack current = top.getItem(raw);

                boolean cursorEmpty = (cursor == null || cursor.getType().isAir());
                boolean currentEmpty = (current == null || current.getType().isAir());

                Optional<TileState> tsOpt = HopperFilterStorage.getTile(holder.hopperBlock());
                if (tsOpt.isEmpty()) return;
                TileState ts = tsOpt.get();

                // If cursor empty -> pick up existing template (clear)
                if (cursorEmpty) {
                    if (!currentEmpty) {
                        event.setCursor(current.clone());
                        top.setItem(raw, null);
                        HopperFilterStorage.setRule(ts, raw, null);
                    }
                    return;
                }

                // cursor has item -> set template
                String rule = toRule(cursor);
                if (rule == null) {
                    p.sendMessage(ChatColor.RED + "That item cannot be used as a filter template.");
                    return;
                }

                ItemStack display = ruleToDisplayItem(rule);
                top.setItem(raw, display);
                HopperFilterStorage.setRule(ts, raw, rule);

                // Consume 1 from cursor
                if (cursor.getAmount() <= 1) {
                    event.setCursor(null);
                } else {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.setCursor(cursor);
                }
                return;
            }

            // Block other GUI slots
            event.setCancelled(true);
            return;
        }

        // Shift-click from player inventory into GUI: put into first empty template slot
        if (event.isShiftClick()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;

            String rule = toRule(clicked);
            if (rule == null) return;

            int slot = firstEmptyTemplateSlot(top);
            if (slot == -1) return;

            event.setCancelled(true);

            Optional<TileState> tsOpt = HopperFilterStorage.getTile(holder.hopperBlock());
            if (tsOpt.isEmpty()) return;
            TileState ts = tsOpt.get();

            top.setItem(slot, ruleToDisplayItem(rule));
            HopperFilterStorage.setRule(ts, slot, rule);

            // consume 1 from clicked stack
            clicked.setAmount(clicked.getAmount() - 1);
            if (clicked.getAmount() <= 0) event.setCurrentItem(null);
            else event.setCurrentItem(clicked);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGuiClose(InventoryCloseEvent event) {
        // No-op for now (state is saved on click)
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof HopperGuiHolder)) return;
    }

    private int firstEmptyTemplateSlot(Inventory gui) {
        for (int i = 0; i < 5; i++) {
            ItemStack it = gui.getItem(i);
            if (it == null || it.getType().isAir()) return i;
        }
        return -1;
    }

    // -------------------------
    // Routing / Filtering
    // -------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveIntoHopper(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        InventoryHolder holder = dest.getHolder();
        if (!(holder instanceof Hopper hopperState)) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTile(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();
        if (!HopperFilterStorage.isEnabled(ts)) return;

        // If enabled but no templates, act vanilla
        if (!HopperFilterStorage.hasAnyRule(ts)) return;

        ItemStack moving = event.getItem();
        if (moving == null || moving.getType().isAir()) return;

        if (!matchesAny(ts, moving)) {
            event.setCancelled(true);
            return;
        }

        Inventory out = getHopperOutputInventory(hopperBlock);
        if (out == null) {
            event.setCancelled(true);
            return;
        }

        // Route (cancel vanilla insertion)
        event.setCancelled(true);

        int requested = moving.getAmount();
        ItemStack toInsert = moving.clone();

        Map<Integer, ItemStack> remainder = out.addItem(toInsert);
        int rem = remainderAmount(remainder);
        int inserted = requested - rem;

        if (inserted <= 0) return;

        removeSimilar(event.getSource(), moving, inserted);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (!(holder instanceof Hopper hopperState)) return;

        Block hopperBlock = hopperState.getBlock();
        Optional<TileState> tsOpt = HopperFilterStorage.getTile(hopperBlock);
        if (tsOpt.isEmpty()) return;

        TileState ts = tsOpt.get();
        if (!HopperFilterStorage.isEnabled(ts)) return;
        if (!HopperFilterStorage.hasAnyRule(ts)) return;

        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (stack == null || stack.getType().isAir()) return;

        if (!matchesAny(ts, stack)) {
            event.setCancelled(true);
            return;
        }

        Inventory out = getHopperOutputInventory(hopperBlock);
        if (out == null) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        int requested = stack.getAmount();
        ItemStack toInsert = stack.clone();

        Map<Integer, ItemStack> remainder = out.addItem(toInsert);
        int rem = remainderAmount(remainder);
        int inserted = requested - rem;

        if (inserted <= 0) return;

        if (inserted >= requested) {
            entity.remove();
        } else {
            ItemStack left = stack.clone();
            left.setAmount(requested - inserted);
            entity.setItemStack(left);
        }
    }

    private Inventory getHopperOutputInventory(Block hopperBlock) {
        if (!(hopperBlock.getBlockData() instanceof Directional dir)) return null;
        Block face = hopperBlock.getRelative(dir.getFacing());

        BlockState st = face.getState();
        if (st instanceof Container c) return c.getInventory();
        if (st instanceof InventoryHolder ih) return ih.getInventory();
        return null;
    }

    private static int remainderAmount(Map<Integer, ItemStack> remainder) {
        if (remainder == null || remainder.isEmpty()) return 0;
        int rem = 0;
        for (ItemStack it : remainder.values()) {
            if (it == null || it.getType().isAir()) continue;
            rem += it.getAmount();
        }
        return rem;
    }

    private static void removeSimilar(Inventory source, ItemStack like, int amount) {
        int remaining = amount;
        ItemStack[] contents = source.getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            if (!it.isSimilar(like)) continue;

            int take = Math.min(remaining, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) contents[i] = null;
            remaining -= take;

            if (remaining <= 0) break;
        }
        source.setContents(contents);
    }

    // -------------------------
    // Rules / matching
    // -------------------------

    private boolean matchesAny(TileState ts, ItemStack item) {
        // Tools/armor: custom named OR enchanted => unsortable
        if (isToolOrArmor(item.getType())) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) return false;
                if (meta.hasEnchants() && !meta.getEnchants().isEmpty()) return false;
            }
        }

        for (int slot = 0; slot < 5; slot++) {
            String rule = HopperFilterStorage.getRule(ts, slot).orElse(null);
            if (rule == null) continue;
            if (matchesRule(rule, item)) return true;
        }
        return false;
    }

    private boolean matchesRule(String rule, ItemStack item) {
        if (rule.startsWith("MAT:")) {
            Material mat = Material.matchMaterial(rule.substring("MAT:".length()));
            return mat != null && item.getType() == mat;
        }

        if (rule.startsWith("ENCH:")) {
            if (item.getType() != Material.ENCHANTED_BOOK) return false;
            String keyStr = rule.substring("ENCH:".length());
            Enchantment ench = Enchantment.getByKey(NamespacedKey.fromString(keyStr));
            if (ench == null) return false;

            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof EnchantmentStorageMeta esm)) return false;
            return esm.hasStoredEnchant(ench);
        }

        return false;
    }

    private String toRule(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;

        if (stack.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof EnchantmentStorageMeta esm)) return null;
            Map<Enchantment, Integer> stored = esm.getStoredEnchants();
            if (stored.isEmpty()) return null;

            Enchantment first = stored.keySet().iterator().next();
            if (first.getKey() == null) return null;
            return "ENCH:" + first.getKey().toString();
        }

        return "MAT:" + stack.getType().name();
    }

    private ItemStack ruleToDisplayItem(String rule) {
        if (rule == null) return null;

        if (rule.startsWith("MAT:")) {
            Material mat = Material.matchMaterial(rule.substring("MAT:".length()));
            if (mat == null) return null;
            return new ItemStack(mat, 1);
        }

        if (rule.startsWith("ENCH:")) {
            String keyStr = rule.substring("ENCH:".length());
            Enchantment ench = Enchantment.getByKey(NamespacedKey.fromString(keyStr));
            if (ench == null) return null;

            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
            ItemMeta meta = book.getItemMeta();
            if (meta instanceof EnchantmentStorageMeta esm) {
                esm.addStoredEnchant(ench, 1, true);
                book.setItemMeta(esm);
            }
            return book;
        }

        return null;
    }

    private ItemStack toggleItem(boolean enabled) {
        ItemStack it = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(enabled ? (ChatColor.GREEN + "Filter Mode: ON") : (ChatColor.GRAY + "Filter Mode: OFF"));
            meta.setLore(List.of(
                    ChatColor.DARK_GRAY + "Sneak-tap hopper or /hf to configure.",
                    ChatColor.DARK_GRAY + "When ON: routes only matching items."
            ));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack closeItem() {
        ItemStack it = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Close");
            it.setItemMeta(meta);
        }
        return it;
    }

    private static boolean isToolOrArmor(Material mat) {
        String n = mat.name();
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) return true;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) return true;
        if (n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT")) return true;
        if (n.equals("SHIELD") || n.equals("ELYTRA")) return true;
        return false;
    }

    // Holder to tie GUI to a specific hopper block
    private record HopperGuiHolder(Block hopperBlock) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
