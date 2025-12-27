package com.entitycore.modules.extendedanvil;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder used to identify ExtendedAnvil inventories and keep a bit of state.
 */
public final class ExtendedAnvilHolder implements InventoryHolder {

    public enum Type {
        PLAYER,
        ADMIN,
        PRIORITY
    }

    private final Type type;
    private final UUID owner;

    private Inventory inventory;

    public ExtendedAnvilHolder(Type type, UUID owner) {
        this.type = type;
        this.owner = owner;
    }

    public Type getType() { return type; }
    public UUID getOwner() { return owner; }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
