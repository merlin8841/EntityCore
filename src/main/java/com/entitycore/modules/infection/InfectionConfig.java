package com.entitycore.modules.infection;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class InfectionConfig {

    private final JavaPlugin plugin;

    // Spread
    private boolean enabled;
    private int infectionsPerCycle;
    private int cycleTicks;
    private long unloadDelayMs;

    // Filters
    private boolean infectAir; // user wants false, but keep configurable
    private boolean skipContainers;

    // Output material (user wants dirt)
    private Material infectionMaterial;

    // Poisonous dirt effect
    private boolean damageEnabled;
    private String damageEffect; // "POISON" or "WITHER" etc
    private int damageAmplifier;
    private int damageDurationTicks;
    private int damageIntervalTicks;

    // Seed item
    private String seedName;
    private boolean seedGlow;

    public InfectionConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        enabled = c.getBoolean("infection.enabled", false);

        infectionsPerCycle = clamp(c.getInt("infection.infectionsPerCycle", 2000), 1, 50000);
        cycleTicks = clamp(c.getInt("infection.cycleTicks", 10), 1, 200);
        unloadDelayMs = Math.max(0L, c.getLong("infection.unloadDelayMs", 5000L));

        infectAir = c.getBoolean("infection.infectAir", false);
        skipContainers = c.getBoolean("infection.skipContainers", true);

        String matName = c.getString("infection.material", "DIRT");
        Material m = Material.matchMaterial(matName == null ? "DIRT" : matName);
        infectionMaterial = (m != null && m.isBlock()) ? m : Material.DIRT;

        damageEnabled = c.getBoolean("infection.damage.enabled", true);
        damageEffect = c.getString("infection.damage.effect", "POISON");
        damageAmplifier = clamp(c.getInt("infection.damage.amplifier", 0), 0, 10);
        damageDurationTicks = clamp(c.getInt("infection.damage.durationTicks", 60), 1, 20 * 60);
        damageIntervalTicks = clamp(c.getInt("infection.damage.intervalTicks", 10), 1, 200);

        seedName = c.getString("infection.seed.name", "§a§lInfection Seed");
        seedGlow = c.getBoolean("infection.seed.glow", true);

        // force your requested defaults if missing
        // (we still allow config overrides)
    }

    public void save() {
        FileConfiguration c = plugin.getConfig();

        c.set("infection.enabled", enabled);

        c.set("infection.infectionsPerCycle", infectionsPerCycle);
        c.set("infection.cycleTicks", cycleTicks);
        c.set("infection.unloadDelayMs", unloadDelayMs);

        c.set("infection.infectAir", infectAir);
        c.set("infection.skipContainers", skipContainers);

        c.set("infection.material", infectionMaterial.name());

        c.set("infection.damage.enabled", damageEnabled);
        c.set("infection.damage.effect", damageEffect);
        c.set("infection.damage.amplifier", damageAmplifier);
        c.set("infection.damage.durationTicks", damageDurationTicks);
        c.set("infection.damage.intervalTicks", damageIntervalTicks);

        c.set("infection.seed.name", seedName);
        c.set("infection.seed.glow", seedGlow);

        plugin.saveConfig();
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // Getters/setters (used by GUI)
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getInfectionsPerCycle() { return infectionsPerCycle; }
    public void setInfectionsPerCycle(int v) { this.infectionsPerCycle = clamp(v, 1, 50000); }

    public int getCycleTicks() { return cycleTicks; }
    public void setCycleTicks(int v) { this.cycleTicks = clamp(v, 1, 200); }

    public long getUnloadDelayMs() { return unloadDelayMs; }
    public void setUnloadDelayMs(long unloadDelayMs) { this.unloadDelayMs = Math.max(0L, unloadDelayMs); }

    public boolean isInfectAir() { return infectAir; }
    public void setInfectAir(boolean infectAir) { this.infectAir = infectAir; }

    public boolean isSkipContainers() { return skipContainers; }
    public void setSkipContainers(boolean skipContainers) { this.skipContainers = skipContainers; }

    public Material getInfectionMaterial() { return infectionMaterial; }
    public void setInfectionMaterial(Material m) { this.infectionMaterial = (m != null && m.isBlock()) ? m : Material.DIRT; }

    public boolean isDamageEnabled() { return damageEnabled; }
    public void setDamageEnabled(boolean damageEnabled) { this.damageEnabled = damageEnabled; }

    public String getDamageEffect() { return damageEffect; }
    public void setDamageEffect(String damageEffect) { this.damageEffect = (damageEffect == null ? "POISON" : damageEffect); }

    public int getDamageAmplifier() { return damageAmplifier; }
    public void setDamageAmplifier(int v) { this.damageAmplifier = clamp(v, 0, 10); }

    public int getDamageDurationTicks() { return damageDurationTicks; }
    public void setDamageDurationTicks(int v) { this.damageDurationTicks = clamp(v, 1, 20 * 60); }

    public int getDamageIntervalTicks() { return damageIntervalTicks; }
    public void setDamageIntervalTicks(int v) { this.damageIntervalTicks = clamp(v, 1, 200); }

    public String getSeedName() { return seedName; }
    public boolean isSeedGlow() { return seedGlow; }

    public JavaPlugin plugin() { return plugin; }
}
