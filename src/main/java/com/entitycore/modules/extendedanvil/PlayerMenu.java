package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PlayerMenu {

    public static final String TITLE = "Extended Anvil";
    public static final int SIZE = 54;

    public static final int SLOT_ITEM = 20;
    public static final int SLOT_BOOK = 29;
    public static final int SLOT_OUTPUT = 24;

    public static final int SLOT_MODE = 47;
    public static final int SLOT_DO = 48;
    public static final int SLOT_CLOSE = 53;

    public enum Mode { DISENCHANT, APPLY }

    public static Inventory create(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE, TITLE);

        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass());

        inv.setItem(SLOT_ITEM, null);
        inv.setItem(SLOT_BOOK, null);
        inv.setItem(SLOT_OUTPUT, outputPlaceholder());

        inv.setItem(SLOT_MODE, modeItem(Mode.DISENCHANT));
        inv.setItem(SLOT_DO, doItem(Mode.DISENCHANT, "1 book = all, 2+ = one-by-one"));
        inv.setItem(SLOT_CLOSE, button(Material.BARRIER, "§cClose", Collections.emptyList()));

        return inv;
    }

    public static boolean isThis(InventoryView view) {
        return view != null && TITLE.equals(view.getTitle());
    }

    public static Mode getMode(Inventory inv) {
        ItemStack it = inv.getItem(SLOT_MODE);
        if (it == null || it.getType() == Material.AIR) return Mode.DISENCHANT;
        ItemMeta meta = it.getItemMeta();
        if (meta == null || meta.getLore() == null) return Mode.DISENCHANT;
        for (String line : meta.getLore()) {
            if (line != null && line.contains("MODE:APPLY")) return Mode.APPLY;
        }
        return Mode.DISENCHANT;
    }

    public static void toggleMode(Inventory inv) {
        Mode m = getMode(inv);
        Mode next = (m == Mode.DISENCHANT) ? Mode.APPLY : Mode.DISENCHANT;
        inv.setItem(SLOT_MODE, modeItem(next));
    }

    public static void refreshPreview(Player viewer, Inventory inv, JavaPlugin plugin, ExtendedAnvilConfig cfg, EnchantCostService costService) {
        Mode mode = getMode(inv);

        ItemStack item = inv.getItem(SLOT_ITEM);
        ItemStack book = inv.getItem(SLOT_BOOK);

        if (mode == Mode.DISENCHANT) {
            inv.setItem(SLOT_DO, doItem(mode, "1 book = all, 2+ = one-by-one"));
            if (item == null || item.getType() == Material.AIR || book == null || book.getType() != Material.BOOK) {
                inv.setItem(SLOT_OUTPUT, outputPlaceholder());
                return;
            }
            if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) {
                inv.setItem(SLOT_OUTPUT, errorItem("No books", "You can't disenchant books."));
                return;
            }
            Map<Enchantment, Integer> ench = item.getEnchantments();
            if (ench == null || ench.isEmpty()) {
                inv.setItem(SLOT_OUTPUT, errorItem("No enchants", "Item has no enchantments."));
                return;
            }

            boolean removeAll = (book.getAmount() == 1);
            LinkedHashMap<Enchantment, Integer> toRemove;
            if (removeAll) {
                toRemove = sortedAllForBook(ench);
            } else {
                Enchantment chosen = cfg.chooseNextDisenchant(ench.keySet());
                if (chosen == null) {
                    inv.setItem(SLOT_OUTPUT, errorItem("No target", "No enchant could be chosen."));
                    return;
                }
                toRemove = new LinkedHashMap<>();
                toRemove.put(chosen, ench.get(chosen));
            }

            ItemStack out = buildEnchantedBook(toRemove);
            ItemMeta meta = out.getItemMeta();
            if (meta != null) {
                meta.setLore(List.of(
                        "§7Preview only. Click Output/Do.",
                        "§7Consumes: §f1 Book"
                ));
                out.setItemMeta(meta);
            }
            inv.setItem(SLOT_OUTPUT, out);
            return;
        }

        // APPLY (true vanilla scaling)
        inv.setItem(SLOT_DO, doItem(mode, "Consumes 1 enchanted book"));
        if (item == null || item.getType() == Material.AIR || book == null || book.getType() != Material.ENCHANTED_BOOK) {
            inv.setItem(SLOT_OUTPUT, outputPlaceholder());
            return;
        }
        if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) {
            inv.setItem(SLOT_OUTPUT, errorItem("No books", "You can't apply to books."));
            return;
        }

        EnchantCostService.ApplyPreview preview = costService.previewApply(viewer, item, book);
        if (!preview.canApply || preview.result == null || preview.result.getType() == Material.AIR) {
            inv.setItem(SLOT_OUTPUT, errorItem("Nothing to apply", "Conflicts or lower/equal levels."));
            return;
        }

        ItemStack out = preview.result.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Cost: §f" + preview.levelCost + " levels",
                    "§7Scaling: §aVanilla exact",
                    "§7Click Output/Do to apply."
            ));
            out.setItemMeta(meta);
        }
        inv.setItem(SLOT_OUTPUT, out);
    }

    public static LinkedHashMap<Enchantment, Integer> sortedAllForBook(Map<Enchantment, Integer> ench) {
        List<Enchantment> list = new ArrayList<>(ench.keySet());
        list.sort(Comparator.comparing(a -> a.getKey().toString()));
        LinkedHashMap<Enchantment, Integer> out = new LinkedHashMap<>();
        for (Enchantment e : list) out.put(e, ench.get(e));
        return out;
    }

    public static ItemStack buildEnchantedBook(LinkedHashMap<Enchantment, Integer> enchants) {
        ItemStack out = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = out.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta esm) {
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                esm.addStoredEnchant(e.getKey(), e.getValue(), false);
            }
            out.setItemMeta(esm);
        }
        return out;
    }

    public static boolean giveToPlayer(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        return leftovers == null || leftovers.isEmpty();
    }

    private static ItemStack glass() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack outputPlaceholder() {
        return button(Material.BLACK_STAINED_GLASS_PANE, "§8Output", List.of("§7Put items in the input slots."));
    }

    private static ItemStack modeItem(Mode mode) {
        if (mode == Mode.DISENCHANT) {
            return button(Material.BOOK, "§bMode: Disenchant", List.of("§7MODE:DISENCHANT", "§7Click to switch"));
        }
        return button(Material.ENCHANTED_BOOK, "§dMode: Apply", List.of("§7MODE:APPLY", "§7Click to switch"));
    }

    private static ItemStack doItem(Mode mode, String hint) {
        if (mode == Mode.DISENCHANT) {
            return button(Material.ANVIL, "§aDisenchant", List.of("§7" + hint, "§7Click to perform."));
        }
        return button(Material.ANVIL, "§aApply Book", List.of("§7" + hint, "§7Click to perform."));
    }

    private static ItemStack errorItem(String title, String line) {
        return button(Material.RED_STAINED_GLASS_PANE, "§c" + title, List.of("§7" + line));
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private PlayerMenu() {}
}
