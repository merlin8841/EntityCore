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

    private final Map<Integer, Mode> modes = new HashMap<>();

    public ExtendedAnvilListener(JavaPlugin plugin, XpRefundService refundService) {
        this.plugin = plugin;
        this.refundService = refundService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        AnvilInventory inv = (AnvilInventory) event.getInventory();

        int key = System.identityHashCode(inv);
        modes.remove(key);

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() == Material.AIR) return;

        // Block book+book entirely (prevents upgrading by combining books)
        if (isEnchantedBook(left) && isEnchantedBook(right)) {
            event.setResult(null);
            inv.setRepairCost(0);
            modes.put(key, Mode.BLOCKED);
            return;
        }

        // Disenchant mode: right is plain BOOK(s)
        if (right.getType() == Material.BOOK) {
            Map<Enchantment, Integer> current = new HashMap<>(left.getEnchantments());
            if (current.isEmpty()) {
                event.setResult(null);
                inv.setRepairCost(0);
                return;
            }

            int books = Math.max(1, right.getAmount());
            LinkedHashMap<Enchantment, Integer> extracted = (books == 1)
                    ? extractAll(current)
                    : extractOne(current);

            if (extracted.isEmpty()) {
                event.setResult(null);
                inv.setRepairCost(0);
                return;
            }

            event.setResult(buildEnchantedBook(extracted));
            inv.setRepairCost(0);

            modes.put(key, Mode.DISENCHANT);
            return;
        }

        // Apply book mode: right is enchanted book
        if (isEnchantedBook(right) && !isBook(left)) {
            Map<Enchantment, Integer> bookEnchants = getStoredEnchants(right);
            if (bookEnchants.isEmpty()) return;

            ItemStack result = left.clone();
            ItemMeta meta = result.getItemMeta();
            if (meta == null) return;

            Map<Enchantment, Integer> existing = new HashMap<>(result.getEnchantments());

            int applied = 0;
            for (Map.Entry<Enchantment, Integer> entry : bookEnchants.entrySet()) {
                Enchantment ench = entry.getKey();
                int level = entry.getValue();

                if (ench == null) continue;
                if (!ench.canEnchantItem(result)) continue;

                boolean conflict = false;
                for (Enchantment ex : existing.keySet()) {
                    if (ex.equals(ench)) continue;
                    if (ex.conflictsWith(ench)) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) continue;

                int currentLevel = existing.containsKey(ench) ? existing.get(ench) : 0;
                if (level > currentLevel) {
                    meta.addEnchant(ench, level, false);
                    existing.put(ench, level);
                    applied++;
                }
            }

            if (applied == 0) {
                event.setResult(null);
                inv.setRepairCost(0);
                modes.put(key, Mode.BLOCKED);
                return;
            }

            result.setItemMeta(meta);
            event.setResult(result);
            inv.setRepairCost(Math.max(1, applied));

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

        if (event.getRawSlot() != 2) return; // result slot

        int key = System.identityHashCode(inv);
        Mode mode = modes.get(key);
        if (mode == null) return;

        if (mode == Mode.BLOCKED) {
            event.setCancelled(true);
            return;
        }

        if (mode != Mode.DISENCHANT) return;

        ItemStack result = inv.getItem(2);
        if (result == null || result.getType() == Material.AIR) return;

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() != Material.BOOK) return;

        // cancel vanilla take, we will perform the operation
        event.setCancelled(true);

        Map<Enchantment, Integer> current = new HashMap<>(left.getEnchantments());
        if (current.isEmpty()) return;

        int books = Math.max(1, right.getAmount());
        LinkedHashMap<Enchantment, Integer> extracted = (books == 1)
                ? extractAll(current)
                : extractOne(current);

        if (extracted.isEmpty()) return;

        // cursor must be empty or stackable
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            if (!canStack(cursor, result)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }

        // consume 1 book
        if (right.getAmount() < 1) return;
        right.setAmount(right.getAmount() - 1);
        if (right.getAmount() <= 0) inv.setItem(1, null);
        else inv.setItem(1, right);

        // remove extracted enchants from item
        ItemStack newLeft = left.clone();
        ItemMeta meta = newLeft.getItemMeta();
        if (meta == null) return;

        for (Enchantment ench : extracted.keySet()) {
            meta.removeEnchant(ench);
        }
        newLeft.setItemMeta(meta);
        inv.setItem(0, newLeft);

        // give enchanted book to cursor
        if (cursor == null || cursor.getType() == Material.AIR) {
            event.setCursor(result.clone());
        } else {
            cursor.setAmount(cursor.getAmount() + result.getAmount());
            event.setCursor(cursor);
        }

        // clear result slot
        inv.setItem(2, null);

        // XP refund
        int refundedLevels = refundService.refundForRemoval(player, newLeft, extracted);
        if (refundedLevels > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        }

        player.updateInventory();
    }

    private boolean isBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.BOOK || it.getType() == Material.ENCHANTED_BOOK;
    }

    private boolean isEnchantedBook(ItemStack it) {
        if (it == null) return false;
        return it.getType() == Material.ENCHANTED_BOOK;
    }

    private Map<Enchantment, Integer> getStoredEnchants(ItemStack book) {
        ItemMeta meta = book.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta)) return Collections.emptyMap();
        EnchantmentStorageMeta esm = (EnchantmentStorageMeta) meta;
        return new HashMap<>(esm.getStoredEnchants());
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

    private LinkedHashMap<Enchantment, Integer> extractAll(Map<Enchantment, Integer> current) {
        List<Enchantment> order = new ArrayList<>(current.keySet());
        Collections.sort(order, new Comparator<Enchantment>() {
            @Override
            public int compare(Enchantment a, Enchantment b) {
                return a.getKey().toString().compareTo(b.getKey().toString());
            }
        });

        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<>();
        for (Enchantment e : order) {
            out.put(e, current.get(e));
        }
        return out;
    }

    private LinkedHashMap<Enchantment, Integer> extractOne(Map<Enchantment, Integer> current) {
        Enchantment chosen = DisenchantPriority.chooseOne(current.keySet());
        if (chosen == null) return new LinkedHashMap<>();
        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<>();
        out.put(chosen, current.get(chosen));
        return out;
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
