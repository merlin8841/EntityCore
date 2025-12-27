package com.entitycore.modules.anvil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ExtendedAnvilAdminMenu {

    public static final String TITLE_MAIN   = "ExtendedAnvil Admin";
    public static final String TITLE_REFUND = "ExtendedAnvil Refund";
    public static final String TITLE_CAPS   = "ExtendedAnvil Caps";
    public static final String TITLE_PRIO   = "ExtendedAnvil Priority";

    public static final int SIZE_27 = 27;
    public static final int SIZE_54 = 54;

    // Main menu slots
    public static final int MAIN_REFUND = 11;
    public static final int MAIN_CAPS   = 13;
    public static final int MAIN_PRIO   = 15;
    public static final int MAIN_CLOSE  = 26;

    // Common nav
    public static final int NAV_BACK  = 45;
    public static final int NAV_PREV  = 46;
    public static final int NAV_NEXT  = 52;
    public static final int NAV_CLOSE = 53;

    // Refund page controls
    public static final int REF_FIRST_DOWN  = 10;
    public static final int REF_FIRST_SHOW  = 11;
    public static final int REF_FIRST_UP    = 12;

    public static final int REF_SECOND_DOWN = 13;
    public static final int REF_SECOND_SHOW = 14;
    public static final int REF_SECOND_UP   = 15;

    public static final int REF_THIRD_DOWN  = 16;
    public static final int REF_THIRD_SHOW  = 17;
    public static final int REF_THIRD_UP    = 18;

    public static final int REF_RESET = 22;

    // Caps page selection + adjust buttons (bottom row)
    public static final int CAPS_SELECTED = 49;
    public static final int CAPS_DEC = 48;
    public static final int CAPS_INC = 50;
    public static final int CAPS_RESET = 51;

    // Priority page selection + move buttons (bottom row)
    public static final int PRIO_SELECTED = 49;
    public static final int PRIO_UP = 48;
    public static final int PRIO_DOWN = 50;
    public static final int PRIO_FIX = 51;

    private final JavaPlugin plugin;

    // runtime state per player
    private final Map<UUID, ViewState> state = new HashMap<UUID, ViewState>();

    public ExtendedAnvilAdminMenu(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE_27, TITLE_MAIN);
        inv.setItem(MAIN_REFUND, button(Material.EXPERIENCE_BOTTLE, "§bRefund Settings", Arrays.asList("§7Edit 75% / 25% / 0%")));
        inv.setItem(MAIN_CAPS, button(Material.ANVIL, "§eEnchantment Caps", Arrays.asList("§7Set max levels per enchant")));
        inv.setItem(MAIN_PRIO, button(Material.ENCHANTED_BOOK, "§dDisenchant Priority", Arrays.asList("§7Set removal order (2+ books)")));
        inv.setItem(MAIN_CLOSE, button(Material.BARRIER, "§cClose", Arrays.asList()));
        player.openInventory(inv);

        ViewState vs = new ViewState();
        vs.screen = Screen.MAIN;
        state.put(player.getUniqueId(), vs);
    }

    public void openRefund(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE_27, TITLE_REFUND);
        renderRefund(inv);
        player.openInventory(inv);

        ViewState vs = getState(player);
        vs.screen = Screen.REFUND;
    }

    public void openCaps(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE_54, TITLE_CAPS);
        ViewState vs = getState(player);
        vs.screen = Screen.CAPS;
        if (vs.page < 0) vs.page = 0;
        renderCaps(inv, vs);
        player.openInventory(inv);
    }

    public void openPriority(Player player) {
        Inventory inv = Bukkit.createInventory(player, SIZE_54, TITLE_PRIO);
        ViewState vs = getState(player);
        vs.screen = Screen.PRIO;
        if (vs.page < 0) vs.page = 0;
        renderPriority(inv, vs);
        player.openInventory(inv);
    }

    public ViewState getState(Player player) {
        UUID id = player.getUniqueId();
        ViewState vs = state.get(id);
        if (vs == null) {
            vs = new ViewState();
            state.put(id, vs);
        }
        return vs;
    }

    public void forget(Player player) {
        state.remove(player.getUniqueId());
    }

    /* =========================
       REFUND
       ========================= */

    public void renderRefund(Inventory inv) {
        inv.clear();

        inv.setItem(REF_FIRST_DOWN, button(Material.REDSTONE, "§c-5%", Arrays.asList()));
        inv.setItem(REF_FIRST_SHOW, percentItem("§bFirst Removal", "extendedanvil.refund.first", "§7Default: §f75%"));
        inv.setItem(REF_FIRST_UP, button(Material.GLOWSTONE_DUST, "§a+5%", Arrays.asList()));

        inv.setItem(REF_SECOND_DOWN, button(Material.REDSTONE, "§c-5%", Arrays.asList()));
        inv.setItem(REF_SECOND_SHOW, percentItem("§bSecond Removal", "extendedanvil.refund.second", "§7Default: §f25%"));
        inv.setItem(REF_SECOND_UP, button(Material.GLOWSTONE_DUST, "§a+5%", Arrays.asList()));

        inv.setItem(REF_THIRD_DOWN, button(Material.REDSTONE, "§c-5%", Arrays.asList()));
        inv.setItem(REF_THIRD_SHOW, percentItem("§bThird+ Removal", "extendedanvil.refund.thirdPlus", "§7Default: §f0%"));
        inv.setItem(REF_THIRD_UP, button(Material.GLOWSTONE_DUST, "§a+5%", Arrays.asList()));

        inv.setItem(REF_RESET, button(Material.PAPER, "§eReset Defaults", Arrays.asList("§775% / 25% / 0%")));

        renderNav(inv, true, false, false);
    }

    public boolean adjustRefund(String path, double delta) {
        double v = plugin.getConfig().getDouble(path, 0.0);
        v = clamp01(v + delta);
        plugin.getConfig().set(path, v);
        plugin.saveConfig();
        return true;
    }

    public void resetRefund() {
        plugin.getConfig().set("extendedanvil.refund.first", 0.75);
        plugin.getConfig().set("extendedanvil.refund.second", 0.25);
        plugin.getConfig().set("extendedanvil.refund.thirdPlus", 0.0);
        plugin.saveConfig();
    }

    /* =========================
       CAPS
       ========================= */

    public void renderCaps(Inventory inv, ViewState vs) {
        inv.clear();

        List<Enchantment> all = allEnchantsSorted();
        int perPage = 45; // slots 0..44
        int maxPage = (all.size() + perPage - 1) / perPage;
        if (maxPage <= 0) maxPage = 1;
        if (vs.page >= maxPage) vs.page = maxPage - 1;

        int start = vs.page * perPage;
        int end = Math.min(all.size(), start + perPage);

        for (int i = start; i < end; i++) {
            int slot = i - start;
            Enchantment ench = all.get(i);
            String key = ench.getKey().toString();

            Integer override = getCapOverride(key);
            int vanilla = ench.getMaxLevel();
            String capText = (override == null) ? ("Default (max " + vanilla + ")") : String.valueOf(override);

            Material icon = Material.ENCHANTED_BOOK;
            ItemStack it = button(icon,
                    "§e" + key,
                    Arrays.asList(
                            "§7Cap: §f" + capText,
                            "§8Click to select"
                    )
            );
            inv.setItem(slot, it);
        }

        // Selected display + controls
        String selKey = vs.selectedKey;
        if (selKey == null || selKey.isEmpty()) selKey = "(none)";

        inv.setItem(CAPS_DEC, button(Material.REDSTONE, "§cCap -1", Arrays.asList("§7Applies to selected")));
        inv.setItem(CAPS_SELECTED, button(Material.NAME_TAG, "§bSelected", Arrays.asList("§f" + selKey)));
        inv.setItem(CAPS_INC, button(Material.GLOWSTONE_DUST, "§aCap +1", Arrays.asList("§7Applies to selected")));
        inv.setItem(CAPS_RESET, button(Material.PAPER, "§eReset Cap", Arrays.asList("§7Remove override (use default)")));

        renderNav(inv, true, vs.page > 0, (vs.page + 1) < maxPage);
    }

    public void selectCaps(Player player, String enchantKey) {
        ViewState vs = getState(player);
        vs.selectedKey = enchantKey;
    }

    public void capInc(Player player) {
        ViewState vs = getState(player);
        if (vs.selectedKey == null) return;

        Enchantment ench = enchantByKey(vs.selectedKey);
        if (ench == null) return;

        Integer override = getCapOverride(vs.selectedKey);
        int vanilla = ench.getMaxLevel();

        int next;
        if (override == null) next = vanilla + 1;
        else next = override.intValue() + 1;

        if (next > 255) next = 255;

        setCapOverride(vs.selectedKey, next);
    }

    public void capDec(Player player) {
        ViewState vs = getState(player);
        if (vs.selectedKey == null) return;

        Enchantment ench = enchantByKey(vs.selectedKey);
        if (ench == null) return;

        Integer override = getCapOverride(vs.selectedKey);
        int vanilla = ench.getMaxLevel();

        int next;
        if (override == null) next = vanilla - 1;
        else next = override.intValue() - 1;

        if (next < 0) next = 0;

        setCapOverride(vs.selectedKey, next);
    }

    public void capReset(Player player) {
        ViewState vs = getState(player);
        if (vs.selectedKey == null) return;
        removeCapOverride(vs.selectedKey);
    }

    /* =========================
       PRIORITY
       ========================= */

    public void renderPriority(Inventory inv, ViewState vs) {
        inv.clear();

        List<String> list = getPriorityList();
        int perPage = 45;
        int maxPage = (list.size() + perPage - 1) / perPage;
        if (maxPage <= 0) maxPage = 1;
        if (vs.page >= maxPage) vs.page = maxPage - 1;

        int start = vs.page * perPage;
        int end = Math.min(list.size(), start + perPage);

        for (int i = start; i < end; i++) {
            int slot = i - start;
            String key = list.get(i);

            int pos = i + 1;
            inv.setItem(slot, button(
                    Material.BOOK,
                    "§d#" + pos + " §f" + key,
                    Arrays.asList("§8Click to select", "§7Used when removing one enchant (2+ books)")
            ));
        }

        String sel = vs.selectedKey;
        if (sel == null || sel.isEmpty()) sel = "(none)";

        inv.setItem(PRIO_UP, button(Material.ARROW, "§aMove Up", Arrays.asList("§7Move selected earlier")));
        inv.setItem(PRIO_SELECTED, button(Material.NAME_TAG, "§bSelected", Arrays.asList("§f" + sel)));
        inv.setItem(PRIO_DOWN, button(Material.ARROW, "§aMove Down", Arrays.asList("§7Move selected later")));
        inv.setItem(PRIO_FIX, button(Material.PAPER, "§eAppend Missing", Arrays.asList("§7Adds any missing enchants to end")));

        renderNav(inv, true, vs.page > 0, (vs.page + 1) < maxPage);
    }

    public void selectPriority(Player player, String key) {
        ViewState vs = getState(player);
        vs.selectedKey = key;
    }

    public void prioMoveUp(Player player) {
        ViewState vs = getState(player);
        if (vs.selectedKey == null) return;

        List<String> list = getPriorityList();
        int idx = list.indexOf(vs.selectedKey);
        if (idx <= 0) return;

        Collections.swap(list, idx, idx - 1);
        setPriorityList(list);
    }

    public void prioMoveDown(Player player) {
        ViewState vs = getState(player);
        if (vs.selectedKey == null) return;

        List<String> list = getPriorityList();
        int idx = list.indexOf(vs.selectedKey);
        if (idx < 0 || idx >= list.size() - 1) return;

        Collections.swap(list, idx, idx + 1);
        setPriorityList(list);
    }

    public void prioAppendMissing() {
        List<String> list = getPriorityList();

        Set<String> existing = new HashSet<String>();
        for (String s : list) {
            if (s != null) existing.add(s.toLowerCase(Locale.ROOT));
        }

        List<Enchantment> all = allEnchantsSorted();
        for (Enchantment e : all) {
            String k = e.getKey().toString().toLowerCase(Locale.ROOT);
            if (!existing.contains(k)) {
                list.add(k);
                existing.add(k);
            }
        }

        setPriorityList(list);
    }

    /* =========================
       Storage helpers
       ========================= */

    private Integer getCapOverride(String enchantKey) {
        String path = "extendedanvil.caps." + enchantKey;
        if (!plugin.getConfig().contains(path)) return null;
        return Integer.valueOf(plugin.getConfig().getInt(path));
    }

    private void setCapOverride(String enchantKey, int cap) {
        plugin.getConfig().set("extendedanvil.caps." + enchantKey, cap);
        plugin.saveConfig();
    }

    private void removeCapOverride(String enchantKey) {
        plugin.getConfig().set("extendedanvil.caps." + enchantKey, null);
        plugin.saveConfig();
    }

    private List<String> getPriorityList() {
        List<String> list = plugin.getConfig().getStringList("extendedanvil.disenchant.priority");
        if (list == null) list = new ArrayList<String>();
        // normalize
        List<String> out = new ArrayList<String>();
        for (String s : list) {
            if (s == null) continue;
            String k = s.trim().toLowerCase(Locale.ROOT);
            if (k.isEmpty()) continue;
            out.add(k);
        }
        return out;
    }

    private void setPriorityList(List<String> list) {
        plugin.getConfig().set("extendedanvil.disenchant.priority", list);
        plugin.saveConfig();
    }

    private List<Enchantment> allEnchantsSorted() {
        Enchantment[] arr = Enchantment.values();
        List<Enchantment> list = new ArrayList<Enchantment>();
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != null) list.add(arr[i]);
        }
        Collections.sort(list, new Comparator<Enchantment>() {
            @Override
            public int compare(Enchantment a, Enchantment b) {
                return a.getKey().toString().compareTo(b.getKey().toString());
            }
        });
        return list;
    }

    private Enchantment enchantByKey(String key) {
        if (key == null) return null;
        String k = key.toLowerCase(Locale.ROOT).trim();
        for (Enchantment e : Enchantment.values()) {
            if (e == null) continue;
            if (e.getKey().toString().equalsIgnoreCase(k)) return e;
        }
        return null;
    }

    private void renderNav(Inventory inv, boolean backToMain, boolean prev, boolean next) {
        inv.setItem(NAV_BACK, button(Material.OAK_DOOR, backToMain ? "§eBack" : "§7Back", Arrays.asList("§7Return to main")));
        inv.setItem(NAV_PREV, button(Material.ARROW, prev ? "§aPrev Page" : "§7Prev Page", Arrays.asList()));
        inv.setItem(NAV_NEXT, button(Material.ARROW, next ? "§aNext Page" : "§7Next Page", Arrays.asList()));
        inv.setItem(NAV_CLOSE, button(Material.BARRIER, "§cClose", Arrays.asList()));
    }

    private ItemStack percentItem(String name, String path, String footer) {
        double v = plugin.getConfig().getDouble(path, 0.0);
        int pct = (int) Math.round(v * 100.0);
        return button(Material.COMPARATOR, name, Arrays.asList(
                "§7Current: §f" + pct + "%",
                footer,
                "§8Applies immediately."
        ));
    }

    private ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    public static final class ViewState {
        public Screen screen = Screen.MAIN;
        public int page = 0;
        public String selectedKey = null;
    }

    public enum Screen {
        MAIN,
        REFUND,
        CAPS,
        PRIO
    }
}
