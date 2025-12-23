package com.entitycore.module;

import org.bukkit.plugin.java.JavaPlugin;

public interface Module {

    String getName();

    void enable(JavaPlugin plugin);

    void disable();
}
