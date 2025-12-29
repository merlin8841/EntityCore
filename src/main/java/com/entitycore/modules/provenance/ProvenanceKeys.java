package com.entitycore.modules.provenance;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProvenanceKeys {

    private final JavaPlugin plugin;

    public ProvenanceKeys(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public NamespacedKey provState() { return new NamespacedKey(plugin, "prov_state"); }
    public NamespacedKey provSource() { return new NamespacedKey(plugin, "prov_source"); }

    // REQUIRED: unique stamp for this tagged stack
    public NamespacedKey provStamp() { return new NamespacedKey(plugin, "prov_stamp"); }

    // OPTIONAL: parent stamp that tainted this output
    public NamespacedKey provParentStamp() { return new NamespacedKey(plugin, "prov_parent_stamp"); }

    // Optional (handy for “placed block tracking” later)
    public NamespacedKey provPlacedBy() { return new NamespacedKey(plugin, "prov_placed_by"); }
}
