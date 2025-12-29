package com.entitycore.modules.flyingallowed;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FlyingAllowedService {

    private final JavaPlugin plugin;
    private final FlyingAllowedConfig config;

    private int taskId = -1;

    // fall grace expiry (millis)
    private final Map<UUID, Long> noFallUntil = new HashMap<>();

    // message throttle (millis)
    private final Map<UUID, Long> msgCooldownUntil = new HashMap<>();

    private static final long MSG_COOLDOWN_MS = 1500;

    public FlyingAllowedService(JavaPlugin plugin, FlyingAllowedConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, config.tickIntervalTicks());
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        noFallUntil.clear();
        msgCooldownUntil.clear();
    }

    public boolean isInFallGraceWindow(Player p) {
        Long until = noFallUntil.get(p.getUniqueId());
        return until != null && until >= System.currentTimeMillis();
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOnline()) continue;

            // Never interfere with Creative/Spectator
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;

            // Bypass (Operator-only)
            if (p.hasPermission("entitycore.flyingallowed.bypass")) continue;

            Location loc = p.getLocation();

            FlyingAllowedWorldGuardBridge.FlyDecision decision = FlyingAllowedWorldGuardBridge.queryDecision(p, loc);

            if (decision == FlyingAllowedWorldGuardBridge.FlyDecision.NONE) {
                // Not in a controlled region -> do not grant flight.
                // If they are currently flying due to our module, we revoke it.
                // (If another plugin manages flight, they can re-enable afterwards.)
                if (p.getAllowFlight() || p.isFlying()) {
                    revoke(p, null, null);
                }
                continue;
            }

            if (decision == FlyingAllowedWorldGuardBridge.FlyDecision.DENY) {
                revoke(p, FlyingAllowedWorldGuardBridge.queryMessage(p, loc), config.msgDenied());
                continue;
            }

            // ALLOW variants
            if (decision == FlyingAllowedWorldGuardBridge.FlyDecision.ALLOW_BASIC) {
                if (p.hasPermission("entitycore.fly")) {
                    grant(p);
                } else {
                    revoke(p, FlyingAllowedWorldGuardBridge.queryMessage(p, loc), config.msgDenied());
                }
                continue;
            }

            if (decision == FlyingAllowedWorldGuardBridge.FlyDecision.ALLOW_VIP) {
                if (p.hasPermission("entitycore.fly.vip") || p.hasPermission("entitycore.fly.admin")) {
                    grant(p);
                } else {
                    revoke(p, FlyingAllowedWorldGuardBridge.queryMessage(p, loc), config.msgDeniedVip());
                }
                continue;
            }

            if (decision == FlyingAllowedWorldGuardBridge.FlyDecision.ALLOW_ADMIN) {
                if (p.hasPermission("entitycore.fly.admin")) {
                    grant(p);
                } else {
                    revoke(p, FlyingAllowedWorldGuardBridge.queryMessage(p, loc), config.msgDeniedAdmin());
                }
            }
        }
    }

    private void grant(Player p) {
        if (!p.getAllowFlight()) {
            p.setAllowFlight(true);
        }
        if (config.autoEnableFlight() && !p.isFlying()) {
            p.setFlying(true);
        }
    }

    private void revoke(Player p, String regionMsg, String fallbackMsg) {
        if (p.isFlying()) {
            p.setFlying(false);
            long until = System.currentTimeMillis() + (config.fallGraceSeconds() * 1000L);
            if (config.fallGraceSeconds() > 0) {
                noFallUntil.put(p.getUniqueId(), until);
            }
        }

        if (p.getAllowFlight()) {
            p.setAllowFlight(false);
        }

        // throttle messages
        if (fallbackMsg != null) {
            long now = System.currentTimeMillis();
            long cd = msgCooldownUntil.getOrDefault(p.getUniqueId(), 0L);
            if (now >= cd) {
                msgCooldownUntil.put(p.getUniqueId(), now + MSG_COOLDOWN_MS);
                p.sendMessage(regionMsg != null && !regionMsg.isBlank() ? regionMsg : fallbackMsg);
            }
        }
    }
}
