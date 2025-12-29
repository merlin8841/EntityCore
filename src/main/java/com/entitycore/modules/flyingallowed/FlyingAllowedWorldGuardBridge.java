package com.entitycore.modules.flyingallowed;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * WorldGuard integration via reflection so we don't need WG as a compile dependency.
 *
 * Custom flags are registered on EntityCore.onLoad().
 * WorldGuard requires registering flags before it enables.  [oai_citation:2‡WorldGuard](https://worldguard.enginehub.org/en/latest/developer/regions/custom-flags/?utm_source=chatgpt.com)
 */
public final class FlyingAllowedWorldGuardBridge {

    private FlyingAllowedWorldGuardBridge() {}

    // Custom flag names (set via /rg flag)
    public static final String FLAG_FLY = "ec-fly";                 // string: allow/deny/default
    public static final String FLAG_FLY_VIP = "ec-fly-vip";         // boolean
    public static final String FLAG_FLY_ADMIN = "ec-fly-admin";     // boolean
    public static final String FLAG_FLY_MESSAGE = "ec-fly-message"; // string

    // Cached reflection handles
    private static boolean tried = false;
    private static boolean available = false;

    private static Object wgInstance;             // WorldGuard.getInstance()
    private static Object flagRegistry;           // wg.getFlagRegistry()

    private static Object stringFlagFly;
    private static Object boolFlagVip;
    private static Object boolFlagAdmin;
    private static Object stringFlagMsg;

    public enum FlyDecision {
        NONE,
        DENY,
        ALLOW_BASIC,
        ALLOW_VIP,
        ALLOW_ADMIN
    }

    public static void tryRegisterFlags(JavaPlugin plugin) {
        if (tried) return;
        tried = true;

        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getInstance = wgClass.getMethod("getInstance");
            wgInstance = getInstance.invoke(null);

            Method getFlagRegistry = wgInstance.getClass().getMethod("getFlagRegistry");
            flagRegistry = getFlagRegistry.invoke(wgInstance);

            // Create flags
            Class<?> stringFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StringFlag");
            Class<?> booleanFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.BooleanFlag");

            stringFlagFly = stringFlagClass.getConstructor(String.class).newInstance(FLAG_FLY);
            boolFlagVip = booleanFlagClass.getConstructor(String.class).newInstance(FLAG_FLY_VIP);
            boolFlagAdmin = booleanFlagClass.getConstructor(String.class).newInstance(FLAG_FLY_ADMIN);
            stringFlagMsg = stringFlagClass.getConstructor(String.class).newInstance(FLAG_FLY_MESSAGE);

            // Register flags (ignore if already registered)
            Method register = flagRegistry.getClass().getMethod("register", Class.forName("com.sk89q.worldguard.protection.flags.Flag"));

            registerFlagSafe(register, stringFlagFly);
            registerFlagSafe(register, boolFlagVip);
            registerFlagSafe(register, boolFlagAdmin);
            registerFlagSafe(register, stringFlagMsg);

            available = true;
            plugin.getLogger().info("[FlyingAllowed] WorldGuard flags registered: "
                    + FLAG_FLY + ", " + FLAG_FLY_VIP + ", " + FLAG_FLY_ADMIN + ", " + FLAG_FLY_MESSAGE);

        } catch (Throwable t) {
            available = false;
            plugin.getLogger().warning("[FlyingAllowed] WorldGuard not available or failed to register flags. WG flight control will be disabled.");
            if (plugin.getLogger().isLoggable(java.util.logging.Level.FINE)) {
                t.printStackTrace();
            }
        }
    }

    private static void registerFlagSafe(Method registerMethod, Object flagObj) {
        try {
            registerMethod.invoke(flagRegistry, flagObj);
        } catch (Throwable ignored) {
            // already registered or registry locked
        }
    }

    public static FlyDecision queryDecision(Player player, Location loc) {
        if (!available || player == null || loc == null) return FlyDecision.NONE;

        try {
            Object set = createApplicableRegionSet(player, loc);
            if (set == null) return FlyDecision.NONE;

            String flyMode = (String) queryValue(set, player, loc, stringFlagFly);
            if (flyMode == null) return FlyDecision.NONE;

            String m = flyMode.trim().toLowerCase(java.util.Locale.ROOT);
            if (m.equals("deny")) return FlyDecision.DENY;
            if (m.equals("default")) return FlyDecision.NONE;
            if (!m.equals("allow")) return FlyDecision.NONE;

            Boolean reqAdmin = (Boolean) queryValue(set, player, loc, boolFlagAdmin);
            Boolean reqVip = (Boolean) queryValue(set, player, loc, boolFlagVip);

            if (Boolean.TRUE.equals(reqAdmin)) return FlyDecision.ALLOW_ADMIN;
            if (Boolean.TRUE.equals(reqVip)) return FlyDecision.ALLOW_VIP;
            return FlyDecision.ALLOW_BASIC;

        } catch (Throwable ignored) {
            return FlyDecision.NONE;
        }
    }

    public static String queryMessage(Player player, Location loc) {
        if (!available || player == null || loc == null) return null;
        try {
            Object set = createApplicableRegionSet(player, loc);
            if (set == null) return null;
            return (String) queryValue(set, player, loc, stringFlagMsg);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object createApplicableRegionSet(Player player, Location loc) throws Throwable {
        // WorldGuardPlugin.inst().wrapPlayer(player)
        Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
        Method inst = wgPluginClass.getMethod("inst");
        Object wgPlugin = inst.invoke(null);
        Method wrapPlayer = wgPluginClass.getMethod("wrapPlayer", Player.class);
        Object localPlayer = wrapPlayer.invoke(wgPlugin, player);

        // BukkitAdapter.adapt(world)
        Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
        Method adaptWorld = bukkitAdapter.getMethod("adapt", org.bukkit.World.class);
        Object weWorld = adaptWorld.invoke(null, loc.getWorld());

        // BukkitAdapter.adapt(location) -> com.sk89q.worldedit.util.Location
        Method adaptLoc = bukkitAdapter.getMethod("adapt", Location.class);
        Object weLoc = adaptLoc.invoke(null, loc);

        // WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
        Method getPlatform = wgInstance.getClass().getMethod("getPlatform");
        Object platform = getPlatform.invoke(wgInstance);

        Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
        Object regionContainer = getRegionContainer.invoke(platform);

        Method createQuery = regionContainer.getClass().getMethod("createQuery");
        Object query = createQuery.invoke(regionContainer);

        // query.getApplicableRegions(weLoc) or query.getApplicableRegions(world, vector)
        // We'll use: query.getApplicableRegions(weLoc)
        Method getApplicableRegions = query.getClass().getMethod("getApplicableRegions", weLoc.getClass());
        Object applicableRegionSet = getApplicableRegions.invoke(query, weLoc);

        // Store localPlayer + set via return value; queryValue() will wrap player again
        // We'll just return applicableRegionSet (it already captures loc), and queryValue will use localPlayer.
        // But queryValue needs localPlayer; we'll recreate it there each time (cheap enough at our tick rate).
        return applicableRegionSet;
    }

    private static Object queryValue(Object applicableSet, Player player, Location loc, Object flag) throws Throwable {
        // Re-wrap LocalPlayer each call (safe)
        Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
        Method inst = wgPluginClass.getMethod("inst");
        Object wgPlugin = inst.invoke(null);
        Method wrapPlayer = wgPluginClass.getMethod("wrapPlayer", Player.class);
        Object localPlayer = wrapPlayer.invoke(wgPlugin, player);

        // ApplicableRegionSet has queryValue(LocalPlayer, Flag)
        Method queryValue = applicableSet.getClass().getMethod(
                "queryValue",
                localPlayer.getClass(),
                Class.forName("com.sk89q.worldguard.protection.flags.Flag")
        );
        return queryValue.invoke(applicableSet, localPlayer, flag);
    }
}
