package com.entitycore.modules.extendedanvil;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;

/**
 * IMPORTANT:
 * Do NOT use Bukkit.createInventory(InventoryType.ANVIL) for Bedrock/Geyser.
 * That isn't a real anvil container, so max-cost overrides won't apply.
 *
 * We must open a real (virtual) anvil container via player.openAnvil(..., true).
 */
public final class PlayerMenu {

    public static final int SLOT_ITEM = 0;   // left
    public static final int SLOT_BOOK = 1;   // right
    public static final int SLOT_OUTPUT = 2; // result

    public enum Mode { NONE, DISENCHANT, APPLY }

    private PlayerMenu() {}

    public static InventoryView open(Player player) {
        // Virtual anvil: force = true
        Location loc = player.getLocation();
        InventoryView view = player.openAnvil(loc, true);

        if (view != null && view.getTopInventory() instanceof AnvilInventory anvil) {
            // Remove 39 clamp
            anvil.setMaximumRepairCost(999999);
            anvil.setRepairCost(0);
            // Clear any leftover
            anvil.setItem(SLOT_ITEM, null);
            anvil.setItem(SLOT_BOOK, null);
            anvil.setItem(SLOT_OUTPUT, null);
        }

        return view;
    }
}
