package org.worldstatus.config;

import org.worldstatus.WorldStatus;
import org.bukkit.configuration.file.FileConfiguration;

public class Config {

    private final WorldStatus plugin;

    public Config(WorldStatus plugin) {
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

    public String  getBarCharFilled()   { return cfg().getString("worldbar.char-filled", "█"); }
    public String  getBarCharEmpty()    { return cfg().getString("worldbar.char-empty",  "░"); }
    public int     getBarLength()       { return cfg().getInt("worldbar.length", 20); }
    public boolean isBarColorEnabled()  { return cfg().getBoolean("worldbar.color", true); }
    public int     getBarDisplaySeconds() { return cfg().getInt("worldbar.display-seconds", 0); }

    // ---- InfluxDB ----------------------------------------------------------

    public boolean isInfluxEnabled()    { return cfg().getBoolean("influxdb.enabled", false); }
    public String  getInfluxUrl()       { return cfg().getString("influxdb.url", "http://localhost:8086"); }
    public String  getInfluxToken()     { return cfg().getString("influxdb.token", ""); }
    public String  getInfluxOrg()       { return cfg().getString("influxdb.org", ""); }
    public String  getInfluxBucket()    { return cfg().getString("influxdb.bucket", "worldstatus"); }
    public int     getInfluxInterval()  { return cfg().getInt("influxdb.interval-seconds", 30); }
    public String  getInfluxMeasurement() { return cfg().getString("influxdb.measurement", "minecraft"); }
}
