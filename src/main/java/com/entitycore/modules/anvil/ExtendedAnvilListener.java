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

    // Context per open anvil (keyed by view instance id)
    private final Map<Integer, Context> contexts = new HashMap<>();

    public ExtendedAnvilListener(JavaPlugin plugin, XpRefundService refundService) {
        this.plugin = plugin;
        this.refundService = refundService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory inv)) return;

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        int viewKey = System.identityHashCode(inv);
        contexts.remove(viewKey);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() == Material.AIR) return;

        // Block enchanted book + enchanted book combining entirely (prevents upgrades)
        if (isEnchantedBook(left) && isEnchantedBook(right)) {
            event.setResult(null);
            inv.setRepairCost(0);
            contexts.put(viewKey, Context.blocked("Book+Book blocked"));
            return;
        }

        // DISENCHANT MODE: right is plain BOOK(s)
        if (right.getType() == Material.BOOK) {
            Map<Enchantment, Integer> current = safeGetItemEnchants(left);
            if (current.isEmpty()) {
                event.setResult(null);
                inv.setRepairCost(0);
                return;
            }

            int books = Math.max(1, right.getAmount());
            Extraction extraction = (books == 1)
                    ? Extraction.all(current)
                    : Extraction.oneByPriority(current);

            if (extraction.extracted.isEmpty()) {
                event.setResult(null);
                inv.setRepairCost(0);
                return;
            }

            ItemStack outBook = buildEnchantedBook(extraction.extracted);
            event.setResult(outBook);

            // No anvil cost for disenchanting (we refund XP instead)
            inv.setRepairCost(0);

            contexts.put(viewKey, Context.disenchant(extraction, books));
            return;
        }

        // APPLY BOOK MODE: right is enchanted book, left is non-book item
        if (isEnchantedBook(right) && !isBook(left)) {
            Map<Enchantment, Integer> bookEnchants = safeGetBookEnchants(right);
            if (bookEnchants.isEmpty()) return;

            ItemStack result = left.clone();
            ItemMeta meta = result.getItemMeta();
            if (meta == null) return;

            Map<Enchantment, Integer> existing = safeGetItemEnchants(result);

            // Apply with vanilla conflict rules + "replace lower level"
            int appliedCount = 0;

            for (Map.Entry<Enchantment, Integer> e : bookEnchants.entrySet()) {
                Enchantment ench = e.getKey();
                int level = e.getValue();

                if (!canApplyToItemVanilla(ench, result)) continue;

                // conflicts with existing enchants?
                boolean conflict = false;
                for (Enchantment ex : existing.keySet()) {
                    if (ex.equals(ench)) continue;
                    if (ex.conflictsWith(ench)) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) continue;

                int currentLevel = existing.getOrDefault(ench, 0);
                if (level > currentLevel) {
                    meta.addEnchant(ench, level, false);
                    existing.put(ench, level);
                    appliedCount++;
                }
            }

            if (appliedCount == 0) {
                event.setResult(null);
                inv.setRepairCost(0);
                contexts.put(viewKey, Context.blocked("No applicable enchants"));
                return;
            }

            result.setItemMeta(meta);
            event.setResult(result);

            // Let vanilla rename text still apply naturally (we do not touch it here).
            // Set a reasonable repair cost; vanilla will also enforce level checks at click time.
            inv.setRepairCost(Math.max(1, appliedCount));

            contexts.put(viewKey, Context.applyBook());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryView view = event.getView();
        if (!(view.getTopInventory() instanceof AnvilInventory inv)) return;

        // Result slot is raw slot 2 in anvil top inventory
        if (event.getRawSlot() != 2) return;

        int viewKey = System.identityHashCode(inv);
        Context ctx = contexts.get(viewKey);
        if (ctx == null) return;

        ItemStack result = inv.getItem(2);
        if (result == null || result.getType() == Material.AIR) return;

        // Handle DISENCHANT click
        if (ctx.mode == Mode.DISENCHANT) {
            event.setCancelled(true);

            ItemStack left = inv.getItem(0);
            ItemStack right = inv.getItem(1);

            if (left == null || left.getType() == Material.AIR) return;
            if (right == null || right.getType() != Material.BOOK) return;

            int books = Math.max(1, right.getAmount());

            // Validate extraction based on current left enchants
            Map<Enchantment, Integer> current = safeGetItemEnchants(left);
            if (current.isEmpty()) return;

            Extraction extraction = (books == 1)
                    ? Extraction.all(current)
                    : Extraction.oneByPriority(current);

            if (extraction.extracted.isEmpty()) return;

            // Must have cursor free or compatible stacking
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                // Only allow if same enchanted book and can stack
                if (!canStack(cursor, result)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
            }

            // Consume books (1 per operation, even in "remove all")
            int consume = 1;
            if (right.getAmount() < consume) return;
            right.setAmount(right.getAmount() - consume);
            if (right.getAmount() <= 0) inv.setItem(1, null);
            else inv.setItem(1, right);

            // Remove enchant(s) from left item
            ItemStack newLeft = left.clone();
            ItemMeta meta = newLeft.getItemMeta();
            if (meta == null) return;

            for (Map.Entry<Enchantment, Integer> e : extraction.extracted.entrySet()) {
                meta.removeEnchant(e.getKey());
            }
            newLeft.setItemMeta(meta);
            inv.setItem(0, newLeft);

            // Give output book onto cursor
            if (cursor == null || cursor.getType() == Material.AIR) {
                event.setCursor(result.clone());
            } else {
                cursor.setAmount(cursor.getAmount() + result.getAmount());
                event.setCursor(cursor);
            }

            // Clear result slot
            inv.setItem(2, null);

            // Refund XP (levels) based on removed enchants + diminishing returns tracked in item PDC
            int refundedLevels = refundService.refundForRemoval(player, newLeft, extraction.extracted);
            if (refundedLevels > 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            } else {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            }

            // Force client update
            player.updateInventory();
            return;
        }

        // APPLY BOOK click: we largely let vanilla handle consumption/cost,
        // but we must ensure book+book stays blocked and doesn't slip through.
        if (ctx.mode == Mode.APPLY_BOOK) {
            // If the result exists, vanilla will handle the merge/cost on its own.
            // We do not cancel.
            return;
        }

        // BLOCKED: prevent taking result if we intentionally blocked something
        if (ctx.mode == Mode.BLOCKED) {
            event.setCancelled(true);
        }
    }

    /* ============================
       Helpers
       ============================ */

    private boolean isBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.BOOK || it.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isEnchantedBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.ENCHANTED_BOOK;
    }

    private Map<Enchantment, Integer> safeGetItemEnchants(ItemStack it) {
        if (it == null) return Collections.emptyMap();
        Map<Enchantment, Integer> m = it.getEnchantments();
        return (m == null) ? Collections.emptyMap() : new HashMap<>(m);
    }

    private Map<Enchantment, Integer> safeGetBookEnchants(ItemStack it) {
        if (it == null || it.getType() != Material.ENCHANTED_BOOK) return Collections.emptyMap();
        ItemMeta meta = it.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta esm)) return Collections.emptyMap();
        Map<Enchantment, Integer> stored = esm.getStoredEnchants();
        return (stored == null) ? Collections.emptyMap() : new HashMap<>(stored);
    }

    private ItemStack buildEnchantedBook(Map<Enchantment, Integer> enchants) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta esm)) return book;

        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            esm.addStoredEnchant(e.getKey(), e.getValue(), false);
        }
        book.setItemMeta(esm);
        return book;
    }

    private boolean canApplyToItemVanilla(Enchantment ench, ItemStack item) {
        // Uses Bukkit's vanilla applicability.
        return ench != null && item != null && ench.canEnchantItem(item);
    }

    private boolean canStack(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        if (a.getMaxStackSize() <= 1) return false;
        if (!Objects.equals(a.getItemMeta(), b.getItemMeta())) return false;
        return a.getAmount() + b.getAmount() <= a.getMaxStackSize();
    }

    /* ============================
       Context + extraction
       ============================ */

    private enum Mode { DISENCHANT, APPLY_BOOK, BLOCKED }

    private static final class Context {
        final Mode mode;
        final String note;
        final int booksHint;
        final Extraction extractionHint;

        private Context(Mode mode, String note, int booksHint, Extraction extractionHint) {
            this.mode = mode;
            this.note = note;
            this.booksHint = booksHint;
            this.extractionHint = extractionHint;
        }

        static Context disenchant(Extraction ex, int books) {
            return new Context(Mode.DISENCHANT, "", books, ex);
        }

        static Context applyBook() {
            return new Context(Mode.APPLY_BOOK, "", 0, null);
        }

        static Context blocked(String why) {
            return new Context(Mode.BLOCKED, why, 0, null);
        }
    }

    private static final class Extraction {
        final LinkedHashMap<Enchantment, Integer> extracted;

        private Extraction(LinkedHashMap<Enchantment, Integer> extracted) {
            this.extracted = extracted;
        }

        static Extraction all(Map<Enchantment, Integer> current) {
            LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<>();
            // fixed deterministic order
            List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(current.entrySet());
            entries.sort(Comparator.comparing(e -> e.getKey().getKey().toString()));
            for (var e : entries) out.put(e.getKey(), e.getValue());
            return new Extraction(out);
        }

        static Extraction oneByPriority(Map<Enchantment, Integer> current) {
            Enchantment chosen = DisenchantPriority.chooseOne(current.keySet());
            if (chosen == null) return new Extraction(new LinkedHashMap<>());
            LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<>();
            out.put(chosen, current.getOrDefault(chosen, 1));
            return new Extraction(out);
        }
    }
}
