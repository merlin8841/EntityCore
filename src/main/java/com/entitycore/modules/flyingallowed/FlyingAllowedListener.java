package com.entitycore.modules.flyingallowed;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public final class FlyingAllowedListener implements Listener {

    private final FlyingAllowedService service;

    public FlyingAllowedListener(FlyingAllowedService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        if (service.isInFallGraceWindow(player)) {
            event.setCancelled(true);
        }
    }
}
