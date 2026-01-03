package com.entitycore.modules.questbuilder.editor;

import com.entitycore.modules.questbuilder.script.QuestScriptRegistry;
import com.entitycore.modules.questbuilder.trigger.QuestTriggerType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestBuilderEditorListener implements Listener {

    private final QuestScriptRegistry scripts;
    private final Map<UUID, PendingEdit> pending = new ConcurrentHashMap<>();

    public QuestBuilderEditorListener(QuestScriptRegistry scripts) {
        this.scripts = scripts;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String title = e.getView().getTitle();
        if (!title.startsWith(QuestBuilderEditorGui.TITLE_PREFIX)) return;

        e.setCancelled(true);

        String areaId = title.substring(QuestBuilderEditorGui.TITLE_PREFIX.length()).trim();
        if (areaId.isEmpty()) return;

        int slot = e.getRawSlot();

        if (slot == 22) {
            scripts.reload();
            player.sendMessage(ChatColor.AQUA + "QuestBuilder scripts reloaded.");
            player.closeInventory();
            return;
        }

        if (slot == 11) {
            beginEdit(player, areaId, QuestTriggerType.ENTER_AREA);
            player.closeInventory();
            return;
        }

        if (slot == 15) {
            beginEdit(player, areaId, QuestTriggerType.EXIT_AREA);
            player.closeInventory();
        }
    }

    private void beginEdit(Player player, String areaId, QuestTriggerType type) {
        pending.put(player.getUniqueId(), new PendingEdit(areaId, type));

        player.sendMessage(ChatColor.AQUA + "Editing actions for area '" + areaId + "' trigger " + type.name());
        player.sendMessage(ChatColor.GRAY + "Paste action lines separated by ' | ' (pipe).");
        player.sendMessage(ChatColor.GRAY + "This will " + ChatColor.GREEN + "APPEND" + ChatColor.GRAY + " (deduped) by default.");
        player.sendMessage(ChatColor.DARK_GRAY + "Example: player:say hi | console:say {player} entered | bq:some_event_id");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "set:" + ChatColor.GRAY + " prefix to replace instead.");
        player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.RED + "cancel" + ChatColor.GRAY + " to abort.");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        PendingEdit edit = pending.get(player.getUniqueId());
        if (edit == null) return;

        e.setCancelled(true);

        String msg = e.getMessage() == null ? "" : e.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel")) {
            pending.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "Edit cancelled.");
            return;
        }

        boolean replace = false;
        if (msg.toLowerCase(Locale.ROOT).startsWith("set:")) {
            replace = true;
            msg = msg.substring("set:".length()).trim();
        }

        List<String> actions = new ArrayList<>();
        for (String part : msg.split("\\|")) {
            String line = part.trim();
            if (!line.isEmpty()) actions.add(line);
        }

        if (replace) {
            scripts.setActionsForAreaTrigger(edit.areaId, edit.type, actions);
            player.sendMessage(ChatColor.GREEN + "Replaced actions with " + actions.size() + " line(s) for "
                    + edit.type.name() + " in area '" + edit.areaId + "'.");
        } else {
            scripts.appendActionsForAreaTrigger(edit.areaId, edit.type, actions);
            int total = scripts.getActionsForAreaTrigger(edit.areaId, edit.type).size();
            player.sendMessage(ChatColor.GREEN + "Added " + actions.size() + " line(s) (deduped). Total now: " + total);
        }

        pending.remove(player.getUniqueId());
    }

    private static final class PendingEdit {
        final String areaId;
        final QuestTriggerType type;

        PendingEdit(String areaId, QuestTriggerType type) {
            this.areaId = areaId;
            this.type = type;
        }
    }
}
