package com.entitycore.modules.anvil;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtendedAnvilAdminListener implements Listener {

    private final JavaPlugin plugin;
    private final ExtendedAnvilAdminMenu menu;

    public ExtendedAnvilAdminListener(JavaPlugin plugin, ExtendedAnvilAdminMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!isEaTitle(title)) return;

        event.setCancelled(true);

        if (!player.hasPermission("entitycore.extendedanvil.admin")) {
            player.sendMessage("§cYou don't have permission to use this.");
            player.closeInventory();
            return;
        }

        Inventory inv = event.getView().getTopInventory();
        int raw = event.getRawSlot();

        ExtendedAnvilAdminMenu.ViewState vs = menu.getState(player);

        // Close
        if (raw == ExtendedAnvilAdminMenu.NAV_CLOSE || raw == ExtendedAnvilAdminMenu.MAIN_CLOSE) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        // Main menu buttons
        if (ExtendedAnvilAdminMenu.TITLE_MAIN.equals(title)) {
            if (raw == ExtendedAnvilAdminMenu.MAIN_REFUND) {
                menu.openRefund(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }
            if (raw == ExtendedAnvilAdminMenu.MAIN_CAPS) {
                vs.page = 0;
                vs.selectedKey = null;
                menu.openCaps(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }
            if (raw == ExtendedAnvilAdminMenu.MAIN_PRIO) {
                vs.page = 0;
                vs.selectedKey = null;
                menu.openPriority(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }
            return;
        }

        // Back to main
        if (raw == ExtendedAnvilAdminMenu.NAV_BACK) {
            menu.openMain(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        // Paging
        if (raw == ExtendedAnvilAdminMenu.NAV_PREV) {
            if (vs.page > 0) vs.page--;
            reopenSame(player, vs);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }
        if (raw == ExtendedAnvilAdminMenu.NAV_NEXT) {
            vs.page++;
            reopenSame(player, vs);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        // Refund interactions
        if (ExtendedAnvilAdminMenu.TITLE_REFUND.equals(title)) {
            boolean changed = false;

            if (raw == ExtendedAnvilAdminMenu.REF_FIRST_DOWN)  changed = menu.adjustRefund("extendedanvil.refund.first", -0.05);
            if (raw == ExtendedAnvilAdminMenu.REF_FIRST_UP)    changed = menu.adjustRefund("extendedanvil.refund.first", +0.05);

            if (raw == ExtendedAnvilAdminMenu.REF_SECOND_DOWN) changed = menu.adjustRefund("extendedanvil.refund.second", -0.05);
            if (raw == ExtendedAnvilAdminMenu.REF_SECOND_UP)   changed = menu.adjustRefund("extendedanvil.refund.second", +0.05);

            if (raw == ExtendedAnvilAdminMenu.REF_THIRD_DOWN)  changed = menu.adjustRefund("extendedanvil.refund.thirdPlus", -0.05);
            if (raw == ExtendedAnvilAdminMenu.REF_THIRD_UP)    changed = menu.adjustRefund("extendedanvil.refund.thirdPlus", +0.05);

            if (raw == ExtendedAnvilAdminMenu.REF_RESET) {
                menu.resetRefund();
                changed = true;
            }

            if (changed) {
                menu.renderRefund(inv);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
            return;
        }

        // Caps interactions
        if (ExtendedAnvilAdminMenu.TITLE_CAPS.equals(title)) {
            // selecting enchant from list
            if (raw >= 0 && raw <= 44) {
                if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
                    String name = event.getCurrentItem().getItemMeta().getDisplayName();
                    // displayName is "§e<key>"
                    String key = stripColorPrefix(name);
                    if (key != null && key.contains(":")) {
                        menu.selectCaps(player, key);
                        menu.renderCaps(inv, vs);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    }
                }
                return;
            }

            if (raw == ExtendedAnvilAdminMenu.CAPS_INC) {
                menu.capInc(player);
                menu.renderCaps(inv, vs);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }
            if (raw == ExtendedAnvilAdminMenu.CAPS_DEC) {
                menu.capDec(player);
                menu.renderCaps(inv, vs);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }
            if (raw == ExtendedAnvilAdminMenu.CAPS_RESET) {
                menu.capReset(player);
                menu.renderCaps(inv, vs);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }

            return;
        }

        // Priority interactions
        if (ExtendedAnvilAdminMenu.TITLE_PRIO.equals(title)) {
            if (raw >= 0 && raw <= 44) {
                if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
                    String name = event.getCurrentItem().getItemMeta().getDisplayName();
                    // "§d#pos §fkey" -> split by space and take last part
                    String key = extractKeyFromPriorityName(name);
                    if (key != null && key.contains(":")) {
                        menu.selectPriority(player, key);
                        menu.renderPriority(inv, vs);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    }
                }
                return;
            }

            if (raw == ExtendedAnvilAdminMenu.PRIO_UP) {
                menu.prioMoveUp(player);
                menu.renderPriority(inv, vs);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }

            if (raw == ExtendedAnvilAdminMenu.PRIO_DOWN) {
                menu.prioMoveDown(player);
                menu.renderPriority(inv, vs);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }

            if (raw == ExtendedAnvilAdminMenu.PRIO_FIX) {
                menu.prioAppendMissing();
                menu.renderPriority(inv, vs);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        String title = event.getView().getTitle();
        if (!isEaTitle(title)) return;

        // Keep state around while menus are open; cleanup when they leave EA menus entirely:
        // (best-effort: if they closed, remove state)
        menu.forget(player);
    }

    private void reopenSame(Player player, ExtendedAnvilAdminMenu.ViewState vs) {
        if (vs.screen == ExtendedAnvilAdminMenu.Screen.REFUND) {
            menu.openRefund(player);
            return;
        }
        if (vs.screen == ExtendedAnvilAdminMenu.Screen.CAPS) {
            menu.openCaps(player);
            return;
        }
        if (vs.screen == ExtendedAnvilAdminMenu.Screen.PRIO) {
            menu.openPriority(player);
            return;
        }
        menu.openMain(player);
    }

    private boolean isEaTitle(String title) {
        return ExtendedAnvilAdminMenu.TITLE_MAIN.equals(title)
                || ExtendedAnvilAdminMenu.TITLE_REFUND.equals(title)
                || ExtendedAnvilAdminMenu.TITLE_CAPS.equals(title)
                || ExtendedAnvilAdminMenu.TITLE_PRIO.equals(title);
    }

    private String stripColorPrefix(String displayName) {
        if (displayName == null) return null;
        // expected "§e<key>"
        if (displayName.length() >= 2 && displayName.charAt(0) == '§') {
            return displayName.substring(2);
        }
        return displayName;
    }

    private String extractKeyFromPriorityName(String displayName) {
        if (displayName == null) return null;
        // "§d#12 §fminecraft:sharpness"
        // remove color codes crudely:
        String s = displayName.replace("§d", "").replace("§f", "");
        int idx = s.lastIndexOf(' ');
        if (idx < 0) return s.trim();
        return s.substring(idx + 1).trim();
    }
}
