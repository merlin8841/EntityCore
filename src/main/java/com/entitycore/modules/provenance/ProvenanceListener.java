package com.entitycore.modules.provenance;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ProvenanceListener implements Listener {

    private final JavaPlugin plugin;
    private final ProvenanceConfig config;
    private final ProvenanceService service;

    public ProvenanceListener(JavaPlugin plugin, ProvenanceConfig config, ProvenanceService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    // ---------
    // CREATIVE tagging (preferred: InventoryCreativeEvent)
    // ---------

    @EventHandler(ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() != GameMode.CREATIVE) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        // Only tag when click is NOT inside the player's own inventory
        if (clicked.equals(player.getInventory())) return;

        boolean changed = false;

        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR && !service.isUnnatural(cursor)) {
            event.setCursor(service.tagUnnatural(cursor, "creative", null));
            changed = true;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null && current.getType() != Material.AIR && !service.isUnnatural(current)) {
            event.setCurrentItem(service.tagUnnatural(current, "creative", null));
            changed = true;
        }

        if (changed) {
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            if (config.debug()) {
                plugin.getLogger().info("[Provenance][DEBUG] CreativeEvent tagged item(s) for " + player.getName());
            }
        }
    }

    // ---------
    // CREATIVE tagging fallback (Geyser sometimes doesn't fire InventoryCreativeEvent)
    // ---------

    @EventHandler(ignoreCancelled = true)
    public void onCreativeFallbackClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() != GameMode.CREATIVE) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        // If the player is clicking inside THEIR inventory, do nothing (avoid tagging rearranges)
        if (clicked.equals(player.getInventory())) return;

        // Only act on actions that typically pull from a menu into player inventory/cursor
        InventoryAction action = event.getAction();
        boolean isTakeAction =
                action == InventoryAction.PICKUP_ALL
                        || action == InventoryAction.PICKUP_HALF
                        || action == InventoryAction.PICKUP_ONE
                        || action == InventoryAction.PICKUP_SOME
                        || action == InventoryAction.CLONE_STACK
                        || action == InventoryAction.MOVE_TO_OTHER_INVENTORY;

        if (!isTakeAction) return;

        // Next tick: tag cursor if player grabbed something, and also tag the target slot if MOVE_TO_OTHER_INVENTORY happened
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean changed = false;

            ItemStack cur = event.getView().getCursor();
            if (cur != null && cur.getType() != Material.AIR && !service.isUnnatural(cur)) {
                event.getView().setCursor(service.tagUnnatural(cur, "creative", null));
                changed = true;
            }

            // Also scan a tiny subset: hotbar + selected slot (cheap and usually where items land)
            int held = player.getInventory().getHeldItemSlot();
            ItemStack hot = player.getInventory().getItem(held);
            if (hot != null && hot.getType() != Material.AIR && !service.isUnnatural(hot)) {
                player.getInventory().setItem(held, service.tagUnnatural(hot, "creative", null));
                changed = true;
            }

            if (changed) {
                player.updateInventory();
                if (config.debug()) {
                    plugin.getLogger().info("[Provenance][DEBUG] CreativeFallback tagged item(s) for " + player.getName());
                }
            }
        }, 1L);
    }

    // ---------
    // /give and /item hooks
    // ---------

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!config.hookGiveCommands()) return;
        handleGiveLike(event.getPlayer(), event.getMessage());
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if (!config.hookGiveCommands()) return;
        handleGiveLike(event.getSender(), event.getCommand());
    }

    private void handleGiveLike(CommandSender sender, String raw) {
        if (raw == null) return;

        String msg = raw.startsWith("/") ? raw.substring(1) : raw;
        String lower = msg.toLowerCase(Locale.ROOT).trim();

        boolean isGive = lower.startsWith("give ") || lower.startsWith("minecraft:give ");
        boolean isItem = lower.startsWith("item ") || lower.startsWith("minecraft:item ");
        if (!isGive && !isItem) return;

        // IMPORTANT CHANGE:
        // Do NOT require OP/permission here. If the command is allowed to run, we tag its results.
        // (Your perms system already controls who can /give.)

        List<String> parts = tokenize(msg);
        if (parts.size() < 2) return;

        String targetToken;

        if (isGive) {
            if (parts.size() < 3) return;
            targetToken = parts.get(1);
        } else {
            if (parts.size() < 4) return;
            String sub = parts.get(1).toLowerCase(Locale.ROOT);
            if (!sub.equals("give")) return;
            targetToken = parts.get(2);
        }

        targetToken = stripQuotes(targetToken);

        List<Player> targets = resolveTargetsSmart(sender, targetToken);
        if (targets.isEmpty()) {
            if (config.debug()) {
                plugin.getLogger().warning("[Provenance][DEBUG] Could not resolve targets for token='" + targetToken + "' raw='" + msg + "'");
            }
            return;
        }

        final String finalTargetToken = targetToken;
        final String finalSource = isGive ? "command:/give" : "command:/item";

        Map<UUID, ItemStack[]> before = new HashMap<>();
        for (Player t : targets) {
            before.put(t.getUniqueId(), cloneContents(t.getInventory().getContents()));
        }

        // Delay 2 ticks for Bedrock/Geyser inventory timing
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player t : targets) {
                ItemStack[] pre = before.get(t.getUniqueId());
                if (pre == null) continue;
                applyTagDiff(t, pre, finalSource);
                t.updateInventory();
            }

            if (config.debug()) {
                plugin.getLogger().info("[Provenance][DEBUG] Tagged give-like command by " + sender.getName()
                        + " -> targets=" + targets.size()
                        + " token=" + finalTargetToken
                        + " source=" + finalSource);
            }
        }, 2L);
    }

    private List<Player> resolveTargetsSmart(CommandSender sender, String token) {
        if (token == null || token.isEmpty()) return List.of();

        if (token.equalsIgnoreCase("@s") && sender instanceof Player p) return List.of(p);

        List<Player> out = service.resolvePlayers(sender, token);
        if (!out.isEmpty()) return out;

        String stripped = stripQuotes(token);
        Player exact = Bukkit.getPlayerExact(stripped);
        if (exact != null) return List.of(exact);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(stripped)) return List.of(p);
        }

        return List.of();
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static List<String> tokenize(String input) {
        List<String> out = new ArrayList<>();
        if (input == null || input.isEmpty()) return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    cur.append(c);
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(c);
        }

        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private static ItemStack[] cloneContents(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = (src[i] == null) ? null : src[i].clone();
        }
        return out;
    }

    private void applyTagDiff(Player target, ItemStack[] before, String source) {
        Inventory inv = target.getInventory();
        ItemStack[] after = inv.getContents();

        for (int slot = 0; slot < after.length && slot < before.length; slot++) {
            ItemStack pre = before[slot];
            ItemStack post = after[slot];

            if (post == null || post.getType() == Material.AIR) continue;

            if (pre == null || pre.getType() == Material.AIR) {
                if (!service.isUnnatural(post)) {
                    inv.setItem(slot, service.tagUnnatural(post, source, null));
                }
                continue;
            }

            if (pre.isSimilar(post)) {
                int delta = post.getAmount() - pre.getAmount();
                if (delta <= 0) continue;

                if (service.isUnnatural(post)) continue;

                ItemStack restored = pre.clone();
                restored.setAmount(pre.getAmount());
                inv.setItem(slot, restored);

                ItemStack added = post.clone();
                added.setAmount(delta);
                ItemStack tagged = service.tagUnnatural(added, source, null);

                HashMap<Integer, ItemStack> overflow = target.getInventory().addItem(tagged);
                for (ItemStack of : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), of);
                }
                continue;
            }

            if (!service.isUnnatural(post)) {
                inv.setItem(slot, service.tagUnnatural(post, source, null));
            }
        }
    }

    // ---------
    // Prevent “unnatural” from moving into shop-like inventories (generic)
    // ---------

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!config.shopBlockEnabled()) return;

        ItemStack it = event.getItem();
        if (!service.isUnnatural(it)) return;

        Inventory dest = event.getDestination();
        if (!isShopInventory(dest)) return;

        event.setCancelled(true);

        if (event.getSource().getHolder() instanceof Player p) {
            p.sendMessage("That item cannot be sold (unnatural).");
        }

        String src = service.getSource(it);
        UUID stamp = service.getStamp(it);
        service.alertOps("§c[Provenance] Blocked UNNATURAL item into shop inventory: "
                + it.getType() + " x" + it.getAmount()
                + " source=" + (src == null ? "?" : src)
                + " stamp=" + (stamp == null ? "?" : stamp));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config.shopBlockEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        if (!isShopInventory(top)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (service.isUnnatural(cursor) || service.isUnnatural(current)) {
            event.setCancelled(true);
            player.sendMessage("That item cannot be sold (unnatural).");

            ItemStack bad = service.isUnnatural(cursor) ? cursor : current;

            String src = service.getSource(bad);
            UUID stamp = service.getStamp(bad);

            service.alertOps("§c[Provenance] " + player.getName() + " attempted to sell UNNATURAL item: "
                    + bad.getType()
                    + " source=" + (src == null ? "?" : src)
                    + " stamp=" + (stamp == null ? "?" : stamp));
        }
    }

    private boolean isShopInventory(Inventory inv) {
        if (inv == null) return false;

        Object holder = inv.getHolder();
        String holderName = holder == null ? "" : holder.getClass().getName().toLowerCase(Locale.ROOT);

        for (String s : config.shopHolderClassContains()) {
            if (s == null) continue;
            if (holderName.contains(s.toLowerCase(Locale.ROOT))) return true;
        }

        return false;
    }
}
