package com.entitycore.modules.provenance;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class ProvenanceService {

    public static final String STATE_UNNATURAL = "UNNATURAL";

    private final JavaPlugin plugin;
    private final ProvenanceConfig config;
    private final ProvenanceKeys keys;

    public ProvenanceService(JavaPlugin plugin, ProvenanceConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.keys = new ProvenanceKeys(plugin);
    }

    public ProvenanceKeys keys() {
        return keys;
    }

    public boolean isUnnatural(ItemStack it) {
        if (it == null) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String state = pdc.get(keys.provState(), PersistentDataType.STRING);
        return STATE_UNNATURAL.equalsIgnoreCase(state);
    }

    public UUID getStamp(ItemStack it) {
        String raw = getString(it, keys.provStamp());
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    public UUID getParentStamp(ItemStack it) {
        String raw = getString(it, keys.provParentStamp());
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getSource(ItemStack it) {
        return getString(it, keys.provSource());
    }

    private String getString(ItemStack it, NamespacedKey key) {
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /**
     * Tag an item stack as UNNATURAL with a fresh prov_stamp.
     * Optional parentStamp records the taint lineage.
     */
    public ItemStack tagUnnatural(ItemStack it, String source, UUID parentStamp) {
        if (it == null) return null;

        ItemStack out = it.clone();

        // Some items can return null ItemMeta depending on implementation.
        // Force-create one so PDC always works.
        ItemMeta meta = out.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(out.getType());
            if (meta == null) {
                // Extremely rare: if factory can't provide meta, we can't store PDC.
                return out;
            }
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.provState(), PersistentDataType.STRING, STATE_UNNATURAL);
        pdc.set(keys.provSource(), PersistentDataType.STRING, source == null ? "unknown" : source);

        UUID stamp = UUID.randomUUID();
        pdc.set(keys.provStamp(), PersistentDataType.STRING, stamp.toString());

        if (parentStamp != null) {
            pdc.set(keys.provParentStamp(), PersistentDataType.STRING, parentStamp.toString());
        } else {
            pdc.remove(keys.provParentStamp());
        }

        out.setItemMeta(meta);
        return out;
    }

    public ItemStack clearProvenance(ItemStack it) {
        if (it == null) return null;

        ItemStack out = it.clone();

        ItemMeta meta = out.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(out.getType());
            if (meta == null) return out;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(keys.provState());
        pdc.remove(keys.provSource());
        pdc.remove(keys.provStamp());
        pdc.remove(keys.provParentStamp());
        pdc.remove(keys.provPlacedBy());

        out.setItemMeta(meta);
        return out;
    }

    public void alertOps(String msg) {
        if (!config.alertsEnabled()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp() || p.hasPermission(config.alertsPermission())) {
                p.sendMessage(msg);
            }
        }
        plugin.getLogger().info("[Provenance] " + stripColor(msg));
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    /**
     * Try resolve selector/target token to player list using Bukkit.selectEntities.
     * Works for @a/@p/name, etc. Returns only Players.
     */
    public List<Player> resolvePlayers(CommandSender sender, String token) {
        if (token == null || token.isEmpty()) return List.of();

        try {
            return Bukkit.selectEntities(sender, token).stream()
                    .filter(e -> e instanceof Player)
                    .map(e -> (Player) e)
                    .collect(Collectors.toList());
        } catch (Throwable ignored) {
            Player p = Bukkit.getPlayerExact(token);
            return p == null ? List.of() : List.of(p);
        }
    }

    /**
     * Get a readable dump of PDC keys for /pdc.
     * If allKeys=false, filters to EntityCore keys.
     */
    public List<String> dumpPdc(ItemStack it, boolean allKeys) {
        if (it == null) return List.of("No item.");

        ItemMeta meta = it.getItemMeta();
        if (meta == null) return List.of("Item has no meta.");

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Set<NamespacedKey> keysSet = pdc.getKeys();
        if (keysSet.isEmpty()) return List.of("No PDC keys.");

        List<NamespacedKey> list = new ArrayList<>(keysSet);
        list.sort(Comparator.comparing(NamespacedKey::toString));

        List<String> out = new ArrayList<>();
        for (NamespacedKey k : list) {
            if (!allKeys) {
                if (!"entitycore".equalsIgnoreCase(k.getNamespace())) continue;
            }
            String v = readBestEffort(pdc, k);
            out.add(k + " = " + v);
        }
        if (out.isEmpty()) return List.of("No matching keys.");
        return out;
    }

    private static String readBestEffort(PersistentDataContainer pdc, NamespacedKey k) {
        try {
            String s = pdc.get(k, PersistentDataType.STRING);
            if (s != null) return s;
        } catch (Exception ignored) {}

        try {
            Integer i = pdc.get(k, PersistentDataType.INTEGER);
            if (i != null) return String.valueOf(i);
        } catch (Exception ignored) {}

        try {
            Long l = pdc.get(k, PersistentDataType.LONG);
            if (l != null) return String.valueOf(l);
        } catch (Exception ignored) {}

        try {
            Double d = pdc.get(k, PersistentDataType.DOUBLE);
            if (d != null) return String.valueOf(d);
        } catch (Exception ignored) {}

        return "(unreadable)";
    }
}
