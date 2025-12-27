package com.entitycore.modules.extendedanvil;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder used to identify ExtendedAnvil inventories and keep a bit of state.
 *
 * This is intentionally tiny and self-contained (EntityCore design rule: no heavy abstractions).
 */
public final class ExtendedAnvilHolder implements InventoryHolder {

    public enum Type {
        PLAYER,
        ADMIN,
        PRIORITY,
        PRIORITY_EDIT,
        ENCHANT_COST_LIST,
        ENCHANT_COST_EDIT
    }

    private final Type type;
    private final UUID owner;

    private Inventory inventory;

    // Optional GUI state
    private int page = 0;
    private String contextKey = null;

    public ExtendedAnvilHolder(Type type, UUID owner) {
        this.type = type;
        this.owner = owner;
    }

    public Type getType() { return type; }
    public UUID getOwner() { return owner; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(0, page); }

    public String getContextKey() { return contextKey; }
    public void setContextKey(String contextKey) { this.contextKey = contextKey; }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
