package com.entitycore.modules.extendedanvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PlayerMenu {

    // Bedrock (Geyser) may ignore custom titles for anvil windows and always show "Anvil".
    // We DO NOT rely on title for identification anymore.
    public static final String TITLE = "Extended Anvil";

    // Real anvil inventory slots
    public static final int SLOT_ITEM = 0;   // left
    public static final int SLOT_BOOK = 1;   // right
    public static final int SLOT_OUTPUT = 2; // result

    public enum Mode { NONE, DISENCHANT, APPLY }

    public static Inventory create(Player player) {
        // Still set a title for Java clients; Bedrock may ignore it.
        return Bukkit.createInventory(player, InventoryType.ANVIL, TITLE);
    }

    public static boolean isThis(InventoryView view) {
        // IMPORTANT: do not check title (Bedrock forces "Anvil")
        return view != null
                && view.getTopInventory() != null
                && view.getTopInventory().getType() == InventoryType.ANVIL;
    }

    /**
     * Mode is inferred automatically:
     * - Right slot = BOOK => Disenchant
     * - Right slot = ENCHANTED_BOOK => Apply
     */
    public static Mode inferMode(Inventory inv) {
        if (inv == null || inv.getType() != InventoryType.ANVIL) return Mode.NONE;

        ItemStack right = inv.getItem(SLOT_BOOK);
        if (right == null || right.getType() == Material.AIR) return Mode.NONE;

        if (right.getType() == Material.BOOK) return Mode.DISENCHANT;
        if (right.getType() == Material.ENCHANTED_BOOK) return Mode.APPLY;

        return Mode.NONE;
    }

    public static void refreshPreview(Player viewer,
                                      Inventory inv,
                                      JavaPlugin plugin,
                                      ExtendedAnvilConfig cfg,
                                      EnchantCostService costService) {

        if (inv == null || inv.getType() != InventoryType.ANVIL) return;

        // CRITICAL: remove vanilla 39-level clamp inside THIS GUI so Bedrock doesn't show "Too Expensive!"
        if (inv instanceof AnvilInventory anvilInv) {
            anvilInv.setMaximumRepairCost(999999); // effectively unlimited
        }

        ItemStack item = inv.getItem(SLOT_ITEM);
        ItemStack right = inv.getItem(SLOT_BOOK);

        // Default: clear output
        inv.setItem(SLOT_OUTPUT, null);

        Mode mode = inferMode(inv);
        if (mode == Mode.NONE) {
            if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(0);
            return;
        }

        if (item == null || item.getType() == Material.AIR) {
            if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(0);
            return;
        }

        // no book-on-book behavior
        if (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK) {
            inv.setItem(SLOT_OUTPUT, errorItem("Can't use books as item"));
            if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(0);
            return;
        }

        if (mode == Mode.DISENCHANT) {
            if (right == null || right.getType() != Material.BOOK || right.getAmount() <= 0) {
                if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(0);
                return;
            }

            Map<Enchantment, Integer> ench = item.getEnchantments();
            if (ench == null || ench.isEmpty()) {
                inv.setItem(SLOT_OUTPUT, errorItem("No enchantments"));
                if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(0);
                return;
            }

            boolean removeAll = (right.getAmount() == 1);

            LinkedHashMap<Enchantment, Integer> toRemove;
            if (removeAll) {
                toRemove = sortedAllForBook(ench);
            } else {
                Enchantment chosen = cfg.chooseNextDisenchant(ench.keySet());
                if (chosen == null) {
                    inv.setItem(SLOT_OUTPUT, errorItem("No target enchant"));
                    if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(0);
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
                        right.getAmount() == 1
                                ? "§7Removes: §fall enchants"
                                : "§7Removes: §fone enchant (priority)"
                ));
                outBook.setItemMeta(meta);
            }

            inv.setItem(SLOT_OUTPUT, outBook);

            // Show some cost line instead of "Too Expensive!" on Bedrock
            if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(1);
            return;
        }

        // APPLY (vanilla-exact scaling via your service)
        if (mode == Mode.APPLY) {
            if (right == null || right.getType() != Material.ENCHANTED_BOOK) {
                if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(0);
                return;
            }

            EnchantCostService.ApplyPreview preview = costService.previewApply(viewer, item, right);
            if (!preview.canApply || preview.result == null || preview.result.getType() == Material.AIR) {
                inv.setItem(SLOT_OUTPUT, errorItem("Nothing to apply"));
                if (inv instanceof AnvilInventory anvilInv) anvilInv.setRepairCost(0);
                return;
            }

            // Put result in slot 2 (client will now allow taking it because max repair cost is huge)
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
            inv.setItem(SLOT_OUTPUT, out);

            // Make Bedrock show "Enchantment Cost: X" instead of "Too Expensive!"
            if (inv instanceof AnvilInventory anvilInv) {
                anvilInv.setRepairCost(Math.max(1, preview.levelCost));
            }
        }
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

    private PlayerMenu() {}
}
