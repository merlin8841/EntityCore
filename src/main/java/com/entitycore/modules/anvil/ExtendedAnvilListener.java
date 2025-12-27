package com.entitycore.modules.anvil;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilListener implements Listener {

    private final JavaPlugin plugin;
    private final XpRefundService refundService;

    // key = identityHashCode(AnvilInventory)
    private final Map<Integer, Ctx> ctxMap = new HashMap<Integer, Ctx>();

    public ExtendedAnvilListener(JavaPlugin plugin, XpRefundService refundService) {
        this.plugin = plugin;
        this.refundService = refundService;
    }

    /* =========================================================
       PREVIEW
       ========================================================= */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) event.getInventory();

        int key = System.identityHashCode(inv);
        ctxMap.remove(key);

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() == Material.AIR) return;

        // Block book + book always (no book upgrading / combining)
        if (isEnchantedBook(left) && isEnchantedBook(right)) {
            event.setResult(null);
            safeSetRepairCost(inv, 0);
            ctxMap.put(key, Ctx.blocked());
            return;
        }

        // =====================================================
        // DISENCHANT: item + BOOK(s) -> enchanted book
        // =====================================================
        if (right.getType() == Material.BOOK) {
            Map<Enchantment, Integer> current = new HashMap<Enchantment, Integer>(left.getEnchantments());
            if (current.isEmpty()) {
                event.setResult(null);
                safeSetRepairCost(inv, 0);
                return;
            }

            int books = Math.max(1, right.getAmount());
            LinkedHashMap<Enchantment, Integer> extracted = (books == 1)
                    ? extractAll(current)
                    : extractOne(current);

            if (extracted.isEmpty()) {
                event.setResult(null);
                safeSetRepairCost(inv, 0);
                return;
            }

            ItemStack outBook = buildEnchantedBook(extracted);

            event.setResult(outBook);

            // Bedrock needs a non-zero cost to treat result as “takeable”
            safeSetRepairCost(inv, 1);

            Ctx ctx = Ctx.disenchant(extracted);
            ctx.previewResult = outBook.clone();
            ctxMap.put(key, ctx);
            return;
        }

        // =====================================================
        // APPLY: item + ENCHANTED_BOOK -> item (supports >39)
        // =====================================================
        if (isEnchantedBook(right) && !isBook(left)) {
            // Try to use vanilla preview result if it exists (best compatibility)
            ItemStack vanillaResult = event.getResult();

            MergeOutcome outcome;
            if (vanillaResult != null && vanillaResult.getType() != Material.AIR) {
                ItemStack capped = enforceCapsOnResult(vanillaResult.clone());
                outcome = new MergeOutcome(capped, computeTrueCostFromResult(left, capped));
            } else {
                // Vanilla might have returned null; build merge ourselves
                outcome = manualMerge(left, right, inv);
                if (outcome == null || outcome.result == null) {
                    event.setResult(null);
                    safeSetRepairCost(inv, 0);
                    ctxMap.put(key, Ctx.blocked());
                    return;
                }
            }

            event.setResult(outcome.result);

            // Keep preview <= 39 so Bedrock never blocks; charge true cost on take.
            int preview = Math.min(39, Math.max(1, outcome.trueCost));
            safeSetRepairCost(inv, preview);

            Ctx ctx = Ctx.apply(outcome.trueCost);
            ctx.previewResult = outcome.result.clone();
            ctxMap.put(key, ctx);
        }
    }

    /* =========================================================
       TAKE RESULT (Bedrock-safe)
       ========================================================= */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!(event.getView().getTopInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) event.getView().getTopInventory();

        // Version-safe result-slot detection
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        int key = System.identityHashCode(inv);
        Ctx ctx = ctxMap.get(key);
        if (ctx == null) return;

        if (ctx.mode == Mode.BLOCKED) {
            event.setCancelled(true);
            return;
        }

        ItemStack result = inv.getItem(2);
        if (result == null || result.getType() == Material.AIR) return;

        // Always take over on result take for our modes (Bedrock consistency)
        if (ctx.mode == Mode.DISENCHANT) {
            event.setCancelled(true);
            handleDisenchantTake(player, inv, event);
            return;
        }

        if (ctx.mode == Mode.APPLY_BOOK) {
            event.setCancelled(true);
            handleApplyTake(player, inv, event, ctx);
        }
    }

    /* =========================================================
       DISENCHANT APPLY
       ========================================================= */

    private void handleDisenchantTake(Player player, AnvilInventory inv, InventoryClickEvent event) {
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() != Material.BOOK) return;

        Map<Enchantment, Integer> current = new HashMap<Enchantment, Integer>(left.getEnchantments());
        if (current.isEmpty()) return;

        int books = Math.max(1, right.getAmount());
        LinkedHashMap<Enchantment, Integer> extracted = (books == 1)
                ? extractAll(current)
                : extractOne(current);

        if (extracted.isEmpty()) return;

        ItemStack outBook = buildEnchantedBook(extracted);

        if (!giveResultToPlayer(player, event.getAction(), outBook, event)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // consume 1 book
        right.setAmount(right.getAmount() - 1);
        if (right.getAmount() <= 0) inv.setItem(1, null);
        else inv.setItem(1, right);

        // remove enchants
        ItemStack newLeft = left.clone();
        ItemMeta meta = newLeft.getItemMeta();
        if (meta == null) return;

        for (Enchantment ench : extracted.keySet()) {
            meta.removeEnchant(ench);
        }
        newLeft.setItemMeta(meta);
        inv.setItem(0, newLeft);

        inv.setItem(2, null);

        int refundedLevels = refundService.refundForRemoval(player, newLeft, extracted);
        if (refundedLevels > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        }

        player.updateInventory();
    }

    /* =========================================================
       APPLY BOOK
       ========================================================= */

    private void handleApplyTake(Player player, AnvilInventory inv, InventoryClickEvent event, Ctx ctx) {
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() != Material.ENCHANTED_BOOK) return;

        ItemStack out = (ctx.previewResult != null) ? ctx.previewResult.clone() : inv.getItem(2);
        if (out == null || out.getType() == Material.AIR) return;

        int trueCost = Math.max(0, ctx.trueCost);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        if (!creative && player.getLevel() < trueCost) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (!giveResultToPlayer(player, event.getAction(), out, event)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // consume 1 enchanted book
        if (right.getAmount() <= 1) inv.setItem(1, null);
        else {
            right.setAmount(right.getAmount() - 1);
            inv.setItem(1, right);
        }

        // consume left
        inv.setItem(0, null);

        // clear result
        inv.setItem(2, null);

        // charge levels (500+ supported)
        if (!creative && trueCost > 0) {
            player.giveExpLevels(-trueCost);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
        player.updateInventory();
    }

    /* =========================================================
       GIVE RESULT (Bedrock actions)
       ========================================================= */

    private boolean giveResultToPlayer(Player player, InventoryAction action, ItemStack result, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();

        boolean wantsToCursor = (action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR
                || action == InventoryAction.COLLECT_TO_CURSOR);

        if (wantsToCursor && (cursor == null || cursor.getType() == Material.AIR)) {
            event.setCursor(result);
            return true;
        }

        Map<Integer, ItemStack> left = player.getInventory().addItem(result);
        return left == null || left.isEmpty();
    }

    /* =========================================================
       Merge / caps / cost
       ========================================================= */

    private ItemStack enforceCapsOnResult(ItemStack result) {
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        Map<Enchantment, Integer> ench = new HashMap<Enchantment, Integer>(meta.getEnchants());
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> e : ench.entrySet()) {
            Enchantment en = e.getKey();
            int lvl = e.getValue();
            if (en == null) continue;

            int capped = applyCap(en, lvl);
            if (capped <= 0) {
                meta.removeEnchant(en);
                changed = true;
            } else if (capped != lvl) {
                meta.addEnchant(en, capped, false);
                changed = true;
            }
        }

        if (changed) result.setItemMeta(meta);
        return result;
    }

    private int computeTrueCostFromResult(ItemStack before, ItemStack after) {
        Map<Enchantment, Integer> a = before.getEnchantments();
        Map<Enchantment, Integer> b = after.getEnchantments();

        int cost = 0;
        for (Map.Entry<Enchantment, Integer> e : b.entrySet()) {
            Enchantment ench = e.getKey();
            int newLevel = e.getValue();
            int oldLevel = a.containsKey(ench) ? a.get(ench) : 0;

            if (newLevel > oldLevel) {
                cost += computeApplyCost(ench, newLevel, oldLevel);
            }
        }

        cost += 1;
        return Math.max(1, cost);
    }

    private MergeOutcome manualMerge(ItemStack left, ItemStack right, AnvilInventory inv) {
        Map<Enchantment, Integer> stored = getStoredEnchants(right);
        if (stored.isEmpty()) return null;

        ItemStack merged = left.clone();
        ItemMeta meta = merged.getItemMeta();
        if (meta == null) return null;

        Map<Enchantment, Integer> existing = new HashMap<Enchantment, Integer>(merged.getEnchantments());

        int applied = 0;
        int cost = 0;

        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            Enchantment ench = entry.getKey();
            int level = entry.getValue();
            if (ench == null) continue;

            if (!ench.canEnchantItem(merged)) continue;

            boolean conflict = false;
            for (Enchantment ex : existing.keySet()) {
                if (ex.equals(ench)) continue;
                if (ex.conflictsWith(ench)) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;

            int capped = applyCap(ench, level);
            if (capped <= 0) continue;

            int oldLevel = existing.containsKey(ench) ? existing.get(ench) : 0;
            if (capped > oldLevel) {
                meta.addEnchant(ench, capped, false);
                existing.put(ench, capped);
                applied++;
                cost += computeApplyCost(ench, capped, oldLevel);
            }
        }

        String rename = safeGetRenameText(inv);
        if (rename != null && !rename.trim().isEmpty()) {
            meta.setDisplayName(rename);
            cost += 1;
        }

        if (applied == 0 && (rename == null || rename.trim().isEmpty())) return null;

        merged.setItemMeta(meta);
        return new MergeOutcome(merged, Math.max(1, cost));
    }

    private int computeApplyCost(Enchantment ench, int newLevel, int oldLevel) {
        int delta = Math.max(1, newLevel - oldLevel);

        int weight;
        if (ench.isCursed()) weight = 1;
        else if (ench.isTreasure()) weight = 8;
        else weight = 4;

        return weight * newLevel * delta;
    }

    private int applyCap(Enchantment ench, int level) {
        String key = ench.getKey().toString();
        String path = "extendedanvil.caps." + key;

        int cap;
        if (plugin.getConfig().contains(path)) cap = plugin.getConfig().getInt(path);
        else cap = ench.getMaxLevel();

        if (cap < 0) cap = ench.getMaxLevel();
        if (cap == 0) return 0;

        return Math.min(level, cap);
    }

    private void safeSetRepairCost(AnvilInventory inv, int cost) {
        try {
            inv.setRepairCost(cost);
        } catch (Throwable ignored) {
        }
    }

    private String safeGetRenameText(AnvilInventory inv) {
        try {
            return inv.getRenameText(); // Paper
        } catch (Throwable ignored) {
            return null;
        }
    }

    /* =========================================================
       Disenchant ordering + book utils
       ========================================================= */

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

    private Map<Enchantment, Integer> getStoredEnchants(ItemStack book) {
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) return Collections.emptyMap();
        return new HashMap<Enchantment, Integer>(((EnchantmentStorageMeta) meta).getStoredEnchants());
    }

    private boolean isBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.BOOK || it.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isEnchantedBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.ENCHANTED_BOOK;
    }

    /* =========================================================
       Context
       ========================================================= */

    private enum Mode {
        DISENCHANT,
        APPLY_BOOK,
        BLOCKED
    }

    private static final class Ctx {
        final Mode mode;
        final int trueCost;

        ItemStack previewResult;
        LinkedHashMap<Enchantment, Integer> extracted;

        private Ctx(Mode mode, int trueCost) {
            this.mode = mode;
            this.trueCost = trueCost;
        }

        static Ctx blocked() {
            return new Ctx(Mode.BLOCKED, 0);
        }

        static Ctx disenchant(LinkedHashMap<Enchantment, Integer> extracted) {
            Ctx c = new Ctx(Mode.DISENCHANT, 0);
            c.extracted = extracted;
            return c;
        }

        static Ctx apply(int trueCost) {
            return new Ctx(Mode.APPLY_BOOK, trueCost);
        }
    }

    private static final class MergeOutcome {
        final ItemStack result;
        final int trueCost;

        MergeOutcome(ItemStack result, int trueCost) {
            this.result = result;
            this.trueCost = trueCost;
        }
    }
}
