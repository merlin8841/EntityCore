package com.entitycore.modules.extendedanvil;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

public final class PlayerMenu {

    public static final int SLOT_ITEM = 0;
    public static final int SLOT_BOOK = 1;
    public static final int SLOT_RESULT = 2;

    /**
     * Opens a REAL anvil container.
     * This is REQUIRED for Bedrock/Geyser.
     */
    public static InventoryView open(Player player) {
        Location loc = player.getLocation();
        player.openAnvil(loc, true); // true = virtual but REAL container
        return player.getOpenInventory();
    }

    private PlayerMenu() {}
}
