package com.entitycore.modules.provenance;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
    // CREATIVE tagging (pulling items from creative menu)
    // ---------

    @EventHandler(ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (player.getGameMode() != GameMode.CREATIVE) return;

        // IMPORTANT:
        // Only tag when the click is NOT inside the player's own inventory.
        // This prevents falsely tagging natural items just because the player rearranged stacks in creative.
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;
        if (clicked.equals(player.getInventory())) return;

        // Item being placed onto the cursor from the creative menu
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR && !service.isUnnatural(cursor)) {
            event.setCursor(service.tagUnnatural(cursor, "creative", null));
            if (config.debug()) {
                plugin.getLogger().info("[Provenance][DEBUG] Tagged CREATIVE cursor item for " + player.getName()
                        + " item=" + cursor.getType() + " x" + cursor.getAmount());
            }
        }

        // Some creative interactions affect current item as well; keep it safe.
        ItemStack current = event.getCurrentItem();
        if (current != null && current.getType() != Material.AIR && !service.isUnnatural(current)) {
            event.setCurrentItem(service.tagUnnatural(current, "creative", null));
            if (config.debug()) {
                plugin.getLogger().info("[Provenance][DEBUG] Tagged CREATIVE current item for " + player.getName()
                        + " item=" + current.getType() + " x" + current.getAmount());
            }
        }
    }

    // ---------
    // /give and /item hooks (no new spawn commands)
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

        // Only tag if sender is an operator OR has explicit permission
        boolean allowed = (sender instanceof Player p && (p.isOp() || p.hasPermission("entitycore.provenance.hook")))
                || (!(sender instanceof Player)); // console allowed
        if (!allowed) return;

        // Tokenize with quote support (Bedrock sometimes uses quotes)
        List<String> parts = tokenize(msg);
        if (parts.size() < 2) return;

        String targetToken;

        // /give <targets> <item> [count...]
        // /item give <targets> <item> [count...]
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

        // Snapshot inventories now; then rescan next tick after command applies
        Map<UUID, ItemStack[]> before = new HashMap<>();
        for (Player t : targets) {
            before.put(t.getUniqueId(), cloneContents(t.getInventory().getContents()));
        }

        String source = isGive ? "command:/give" : "command:/item";

        // Delay 1 tick to ensure the command has applied inventory changes.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player t : targets) {
                ItemStack[] pre = before.get(t.getUniqueId());
                if (pre == null) continue;
                applyTagDiff(t, pre, source);
            }

            if (config.debug()) {
                plugin.getLogger().info("[Provenance][DEBUG] Tagged give-like command by " + sender.getName()
                        + " -> targets=" + targets.size()
                        + " token=" + targetToken
                        + " source=" + source);
            }
        }, 1L);
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

    /**
     * After /give, detect which slots increased and tag ONLY the added delta.
     * If it merged into an existing natural stack, we split out the delta into its own tagged stack.
     */
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
