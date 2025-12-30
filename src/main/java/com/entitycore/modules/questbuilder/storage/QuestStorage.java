package com.entitycore.modules.questbuilder.storage;

import com.entitycore.modules.questbuilder.model.QuestDraft;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class QuestStorage {

    private final File file;
    private final Map<String, QuestDraft> drafts = new HashMap<>();

    public QuestStorage(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "questbuilder.yml");
        load();
    }

    public QuestDraft getOrCreate(String id, World world) {
        return drafts.computeIfAbsent(id, k -> new QuestDraft(id, world));
    }

    public void load() {
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String id : cfg.getKeys(false)) {
            World world = Bukkit.getWorld(cfg.getString(id + ".world"));
            if (world == null) continue;

            QuestDraft d = new QuestDraft(id, world);
            d.pos1 = cfg.getVector(id + ".pos1");
            d.pos2 = cfg.getVector(id + ".pos2");

            for (Object o : cfg.getList(id + ".points", List.of())) {
                if (o instanceof Vector v) d.points.add(v);
            }

            drafts.put(id, d);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (QuestDraft d : drafts.values()) {
            cfg.set(d.id + ".world", d.world.getName());
            cfg.set(d.id + ".pos1", d.pos1);
            cfg.set(d.id + ".pos2", d.pos2);
            cfg.set(d.id + ".points", d.points);
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
