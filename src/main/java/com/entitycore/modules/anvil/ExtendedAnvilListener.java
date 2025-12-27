package com.entitycore.modules.anvil;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilListener implements Listener {

    private final JavaPlugin plugin;
    private final XpRefundService refundService;

    private final Map<Integer, Mode> modes = new HashMap<Integer, Mode>();

    public ExtendedAnvilListener(JavaPlugin plugin, XpRefundService refundService) {
        this.plugin = plugin;
        this.refundService = refundService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) event.getInventory();

        // Always allow “expensive” anvils (no 39-level cap)
        try {
            inv.setMaximumRepairCost(Integer.MAX_VALUE);
        } catch (Throwable ignored) {
            // Older API / forks: ignore
        }

        int key = System.identityHashCode(inv);
        modes.remove(key);

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() == Material.AIR) return;

        // Hard block book+book (prevents upgrading/combining books)
        if (isEnchantedBook(left) && isEnchantedBook(right)) {
            event.setResult(null);
            modes.put(key, Mode.BLOCKED);
            return;
        }

        // ============================================================
        // DISENCHANT MODE (right is plain BOOK)
        // ============================================================
        if (right.getType() == Material.BOOK) {
            Map<Enchantment, Integer> current = new HashMap<Enchantment, Integer>(left.getEnchantments());
            if (current.isEmpty()) {
                event.setResult(null);
                return;
            }

            int books = Math.max(1, right.getAmount());
            LinkedHashMap<Enchantment, Integer> extracted = (books == 1)
                    ? extractAll(current)
                    : extractOne(current);

            if (extracted.isEmpty()) {
                event.setResult(null);
                return;
            }

            // Show output book in result slot
            event.setResult(buildEnchantedBook(extracted));

            // IMPORTANT: Bedrock often won’t allow taking result if cost is 0.
            // We set cost to 1 ONLY so the UI works; click is cancelled, so no XP is consumed.
            try {
                inv.setRepairCost(1);
            } catch (Throwable ignored) {
            }

            modes.put(key, Mode.DISENCHANT);
            return;
        }

        // ============================================================
        // APPLY MODE (right is ENCHANTED_BOOK) - LET VANILLA COSTS APPLY
        // ============================================================
        if (isEnchantedBook(right) && !isBook(left)) {
            // We DO NOT set repair cost here.
            // Vanilla will compute normal scaling cost, and maxRepairCost above prevents “Too Expensive”.

            // But we DO enforce caps by modifying vanilla's result.
            ItemStack vanillaResult = event.getResult();
            if (vanillaResult == null || vanillaResult.getType() == Material.AIR) {
                // vanilla says no valid result (conflicts, etc.)
                return;
            }

            ItemMeta meta = vanillaResult.getItemMeta();
            if (meta == null) return;

            Map<Enchantment, Integer> enchants = new HashMap<Enchantment, Integer>(meta.getEnchants());
            boolean changed = false;

            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                Enchantment ench = entry.getKey();
                int level = entry.getValue();

                if (ench == null) continue;

                int capped = applyCap(ench, level);
                if (capped <= 0) {
                    meta.removeEnchant(ench); // cap 0 = blocked
                    changed = true;
                } else if (capped != level) {
                    meta.addEnchant(ench, capped, false);
                    changed = true;
                }
            }

            if (changed) {
                vanillaResult.setItemMeta(meta);
                event.setResult(vanillaResult);
            }

            modes.put(key, Mode.APPLY_BOOK);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        InventoryView view = event.getView();
        if (!(view.getTopInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) view.getTopInventory();

        // Anvil result slot is raw slot 2
        if (event.getRawSlot() != 2) return;

        int key = System.identityHashCode(inv);
        Mode mode = modes.get(key);
        if (mode == null) return;

        if (mode == Mode.BLOCKED) {
            event.setCancelled(true);
            return;
        }

        // APPLY_BOOK: let vanilla handle the take + cost + rename (normal behavior)
        if (mode == Mode.APPLY_BOOK) {
            return;
        }

        // DISENCHANT: we perform operation ourselves
        if (mode != Mode.DISENCHANT) return;

        ItemStack result = inv.getItem(2);
        if (result == null || result.getType() == Material.AIR) return;

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() != Material.BOOK) return;

        // Cancel vanilla take (prevents XP cost + prevents vanilla consumption)
        event.setCancelled(true);

        Map<Enchantment, Integer> current = new HashMap<Enchantment, Integer>(left.getEnchantments());
        if (current.isEmpty()) return;

        int books = Math.max(1, right.getAmount());
        LinkedHashMap<Enchantment, Integer> extracted = (books == 1)
                ? extractAll(current)
                : extractOne(current);

        if (extracted.isEmpty()) return;

        // Cursor must be empty or stackable
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            if (!canStack(cursor, result)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }

        // Consume 1 plain book
        if (right.getAmount() < 1) return;
        right.setAmount(right.getAmount() - 1);
        if (right.getAmount() <= 0) inv.setItem(1, null);
        else inv.setItem(1, right);

        // Remove extracted enchants from left item
        ItemStack newLeft = left.clone();
        ItemMeta meta = newLeft.getItemMeta();
        if (meta == null) return;

        for (Enchantment ench : extracted.keySet()) {
            meta.removeEnchant(ench);
        }
        newLeft.setItemMeta(meta);
        inv.setItem(0, newLeft);

        // Give enchanted book to cursor
        if (cursor == null || cursor.getType() == Material.AIR) {
            event.setCursor(result.clone());
        } else {
            cursor.setAmount(cursor.getAmount() + result.getAmount());
            event.setCursor(cursor);
        }

        // Clear result slot
        inv.setItem(2, null);

        // XP refund (diminishing returns tracked on item PDC)
        int refundedLevels = refundService.refundForRemoval(player, newLeft, extracted);
        if (refundedLevels > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        }

        player.updateInventory();
    }

    /* =========================
       Caps
       ========================= */

    private int applyCap(Enchantment ench, int level) {
        String k = ench.getKey().toString();
        String path = "extendedanvil.caps." + k;

        int override;
        if (plugin.getConfig().contains(path)) {
            override = plugin.getConfig().getInt(path);
        } else {
            override = ench.getMaxLevel(); // default to vanilla max unless overridden
        }

        if (override < 0) override = ench.getMaxLevel();
        if (override == 0) return 0;

        if (level > override) return override;
        return level;
    }

    /* =========================
       Extraction order
       ========================= */

    private LinkedHashMap<Enchantment, Integer> extractAll(Map<Enchantment, Integer> current) {
        List<Enchantment> order = new ArrayList<Enchantment>(current.keySet());
        Collections.sort(order, new Comparator<Enchantment>() {
            @Override
            public int compare(Enchantment a, Enchantment b) {
                return a.getKey().toString().compareTo(b.getKey().toString());
            }
        });

        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<Enchantment, Integer>();
        for (Enchantment e : order) out.put(e, current.get(e));
        return out;
    }

    private LinkedHashMap<Enchantment, Integer> extractOne(Map<Enchantment, Integer> current) {
        Enchantment chosen = DisenchantPriority.chooseOne(plugin, current.keySet());
        if (chosen == null) return new LinkedHashMap<Enchantment, Integer>();

        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<Enchantment, Integer>();
        out.put(chosen, current.get(chosen));
        return out;
    }

    /* =========================
       Book building
       ========================= */

    private ItemStack buildEnchantedBook(LinkedHashMap<Enchantment, Integer> enchants) {
        ItemStack out = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = out.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta esm = (EnchantmentStorageMeta) meta;
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                esm.addStoredEnchant(e.getKey(), e.getValue(), false);
            }
            out.setItemMeta(esm);
        }
        return out;
    }

    /* =========================
       Helpers
       ========================= */

    private boolean isBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.BOOK || it.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isEnchantedBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean canStack(ItemStack a, ItemStack b) {
        if (a.getType() != b.getType()) return false;
        if (a.getMaxStackSize() <= 1) return false;
        if (!Objects.equals(a.getItemMeta(), b.getItemMeta())) return false;
        return a.getAmount() + b.getAmount() <= a.getMaxStackSize();
    }

    private enum Mode {
        DISENCHANT,
        APPLY_BOOK,
        BLOCKED
    }
}
