package org.worldstatus.config;

import org.worldstatus.WorldStatusPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final WorldStatusPlugin plugin;

    public ConfigManager(WorldStatusPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public String getLanguage() {
        return cfg().getString("language", "en");
    }

    public boolean isDiscordEnabled() {
        return cfg().getBoolean("discord.enabled", false);
    }

    public String getDiscordToken() {
        return cfg().getString("discord.token", "YOUR_BOT_TOKEN_HERE");
    }

    public String getDiscordGuildId() {
        return cfg().getString("discord.guild-id", "");
    }

    public int getPlayerListRefreshSeconds() {
        return cfg().getInt("discord.playerlist-refresh-seconds", 5);
    }

    public boolean showDiskPercent()  { return cfg().getBoolean("world-status.show-disk-percent",  true); }
    public boolean showWorldSizes()   { return cfg().getBoolean("world-status.show-world-sizes",   true); }
    public boolean showTPS()          { return cfg().getBoolean("world-status.show-tps",           true); }
    public boolean showMSPT()         { return cfg().getBoolean("world-status.show-mspt",          true); }
    public boolean showRAM()          { return cfg().getBoolean("world-status.show-ram",           true); }
    public boolean showCPU()          { return cfg().getBoolean("world-status.show-cpu",           true); }
    public boolean showPlayers()      { return cfg().getBoolean("world-status.show-players",       true); }

    public String getBarCharFilled()   { return cfg().getString("worldbar.char-filled", "\u2588"); }
    public String getBarCharEmpty()    { return cfg().getString("worldbar.char-empty",  "\u2591"); }
    public int    getBarLength()       { return cfg().getInt("worldbar.length", 20); }
    public boolean isBarColorEnabled() { return cfg().getBoolean("worldbar.color", true); }
    /** Seconds before the in-game boss bar is automatically removed. */
    public int    getBarDisplaySeconds() { return cfg().getInt("worldbar.display-seconds", 0); }
}