package com.entitycore.modules.questbuilder.script;

import com.entitycore.modules.questbuilder.trigger.QuestTriggerType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class QuestScriptRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration cfg;

    public QuestScriptRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "questbuilder-triggers.yml");
        loadOrCreate();
    }

    public void loadOrCreate() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            cfg = new YamlConfiguration();
            writeDefaultFile();
            save();
        } else {
            cfg = YamlConfiguration.loadConfiguration(file);
        }
    }

    private void writeDefaultFile() {
        // Area triggers (enter/exit)
        cfg.set("areas.default.ENTER_AREA.actions", List.of(
                "player:say §aEntered default area!",
                "console:say {player} entered area default"
        ));
        cfg.set("areas.default.EXIT_AREA.actions", List.of(
                "player:say §cLeft default area!"
        ));

        // Interaction triggers (global)
        cfg.set("interactions.example_button.type", "RIGHT_CLICK_BLOCK");
        cfg.set("interactions.example_button.block", "STONE_BUTTON");
        cfg.set("interactions.example_button.requireArea", "default");
        cfg.set("interactions.example_button.actions", List.of(
                "player:say §bPressed a button inside default area!",
                "bq:some_betonquest_event_id"
        ));

        cfg.set("interactions.example_plate.type", "PHYSICAL_TRIGGER");
        cfg.set("interactions.example_plate.block", "STONE_PRESSURE_PLATE");
        cfg.set("interactions.example_plate.actions", List.of(
                "player:say §eStepped on a plate!"
        ));
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[QuestBuilder] Failed to save questbuilder-triggers.yml");
            e.printStackTrace();
        }
    }

    public void reload() {
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public List<String> getActionsForAreaTrigger(String areaId, QuestTriggerType type) {
        if (areaId == null || type == null) return List.of();
        String path = "areas." + areaId + "." + type.name() + ".actions";
        List<String> list = cfg.getStringList(path);
        return list == null ? List.of() : list;
    }

    // NEW: used by popup editor
    public void setActionsForAreaTrigger(String areaId, QuestTriggerType type, List<String> actions) {
        if (areaId == null || areaId.isBlank() || type == null) return;

        String path = "areas." + areaId + "." + type.name() + ".actions";

        List<String> cleaned = new ArrayList<>();
        if (actions != null) {
            for (String s : actions) {
                if (s == null) continue;
                String t = s.trim();
                if (!t.isEmpty()) cleaned.add(t);
            }
        }

        cfg.set(path, cleaned);
        save();
    }

    public List<InteractionTrigger> getInteractionTriggers() {
        ConfigurationSection sec = cfg.getConfigurationSection("interactions");
        if (sec == null) return List.of();

        List<InteractionTrigger> out = new ArrayList<>();
        for (String key : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(key);
            if (t == null) continue;

            QuestTriggerType type = QuestTriggerType.from(t.getString("type"));
            if (type == null) continue;

            String blockName = t.getString("block");
            if (blockName == null) continue;

            Material mat;
            try {
                mat = Material.valueOf(blockName.trim().toUpperCase());
            } catch (Exception ignored) {
                continue;
            }

            String requireArea = t.getString("requireArea", null);
            List<String> actions = t.getStringList("actions");
            if (actions == null) actions = List.of();

            out.add(new InteractionTrigger(key, type, mat, requireArea, actions));
        }
        return out;
    }

    public static final class InteractionTrigger {
        public final String id;
        public final QuestTriggerType type;
        public final Material block;
        public final String requireArea;
        public final List<String> actions;

        public InteractionTrigger(String id, QuestTriggerType type, Material block, String requireArea, List<String> actions) {
            this.id = id;
            this.type = type;
            this.block = block;
            this.requireArea = requireArea;
            this.actions = actions;
        }
    }
}
