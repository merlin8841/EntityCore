package com.entitycore.modules.provenance;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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

        // We only care about:
        // - give / minecraft:give
        // - item / minecraft:item (we’ll treat “item give” similarly)
        boolean isGive = lower.startsWith("give ") || lower.startsWith("minecraft:give ");
        boolean isItem = lower.startsWith("item ") || lower.startsWith("minecraft:item ");

        if (!isGive && !isItem) return;

        // Only tag if sender is an operator OR has explicit permission
        boolean allowed = (sender instanceof Player p && (p.isOp() || p.hasPermission("entitycore.provenance.hook")))
            || (!(sender instanceof Player)); // console allowed
        if (!allowed) return;

        // Tokenize
        String[] parts = msg.split("\\s+");
        if (parts.length < 2) return;

        // /give <targets> <item> [count...]
        // /item give <targets> <item> [count...]
        String targetToken;
        if (isGive) {
            if (parts.length < 3) return;
            targetToken = parts[1];
        } else {
            // item ...
            if (parts.length < 4) return;
            String sub = parts[1].toLowerCase(Locale.ROOT);
            if (!sub.equals("give")) return; // we only hook item give
            targetToken = parts[2];
        }

        List<Player> targets = service.resolvePlayers(sender, targetToken);
        if (targets.isEmpty()) return;

        // Snapshot inventories now; then rescan next tick after command applies
        Map<UUID, ItemStack[]> before = new HashMap<>();
        for (Player t : targets) {
            before.put(t.getUniqueId(), cloneContents(t.getInventory().getContents()));
        }

        String source = isGive ? "command:/give" : "command:/item";
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player t : targets) {
                ItemStack[] pre = before.get(t.getUniqueId());
                if (pre == null) continue;
                applyTagDiff(t, pre, source);
            }

            if (config.debug()) {
                plugin.getLogger().info("[Provenance][DEBUG] Tagged give-like command by " + sender.getName()
                    + " -> targets=" + targets.size() + " source=" + source);
            }
        });
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

            // If slot was empty before -> whole stack is new -> tag whole stack (unless already tagged)
            if (pre == null || pre.getType() == Material.AIR) {
                if (!service.isUnnatural(post)) {
                    inv.setItem(slot, service.tagUnnatural(post, source, null));
                }
                continue;
            }

            // Same material and similar meta?
            if (pre.isSimilar(post)) {
                int delta = post.getAmount() - pre.getAmount();
                if (delta <= 0) continue;

                // If post already tagged unnatural, keep as-is
                if (service.isUnnatural(post)) continue;

                // Split: restore original natural stack amount, and create new tagged stack for delta
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

            // Slot changed to a different item -> tag the new item if not already tagged
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

            String src = service.getSource(service.isUnnatural(cursor) ? cursor : current);
            UUID stamp = service.getStamp(service.isUnnatural(cursor) ? cursor : current);

            service.alertOps("§c[Provenance] " + player.getName() + " attempted to sell UNNATURAL item: "
                + (service.isUnnatural(cursor) ? cursor.getType() : current.getType())
                + " source=" + (src == null ? "?" : src)
                + " stamp=" + (stamp == null ? "?" : stamp));
        }
    }

    private boolean isShopInventory(Inventory inv) {
        if (inv == null) return false;

        String title = "";
        try {
            title = Bukkit.getServer().getItemFactory() != null ? "" : "";
        } catch (Throwable ignored) {}

        // safer: use view title via click event normally; here we rely on holder class and fallback titles list
        Object holder = inv.getHolder();
        String holderName = holder == null ? "" : holder.getClass().getName().toLowerCase(Locale.ROOT);

        for (String s : config.shopHolderClassContains()) {
            if (s == null) continue;
            if (holderName.contains(s.toLowerCase(Locale.ROOT))) return true;
        }

        // title-based check is best inside click event; for move event we can’t read view title reliably.
        // We still keep a weak fallback on holder name only here.
        return false;
    }
}
