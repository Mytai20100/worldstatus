package org.worldstatus;

import org.worldstatus.commands.worldbar;
import org.worldstatus.commands.worldstatus;
import org.worldstatus.config.Config;
import org.worldstatus.discord.Bot;
import org.worldstatus.influxdb.Influx;
import org.worldstatus.lang.Lang;
import org.worldstatus.prometheus.Server;
import org.worldstatus.util.Folia;
import org.worldstatus.util.System;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class WorldStatus extends JavaPlugin {

    private static WorldStatus instance;

    private Config    configManager;
    private Lang      langManager;
    private System    systemStats;
    private Bot       discordBot;
    private Server    prometheusServer;
    private Influx    influxWriter;
    private worldbar  worldBarCommand;

    @Override
    public void onEnable() {
        instance = this;

        java.lang.System.setProperty("java.awt.headless", "true");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();

        File playerlistJson = new File(getDataFolder(), "playerlist.json");
        if (!playerlistJson.exists()) saveResource("playerlist.json", false);

        File worldstatusJson = new File(getDataFolder(), "worldstatus.json");
        if (!worldstatusJson.exists()) saveResource("worldstatus.json", false);

        configManager = new Config(this);
        langManager   = new Lang(this);
        systemStats   = new System(this);

        systemStats.startTracking();

        worldstatus statusCmd = new worldstatus(this);
        getCommand("world-status").setExecutor(statusCmd);
        getCommand("world-status").setTabCompleter(statusCmd);

        worldBarCommand = new worldbar(this);
        getCommand("worldbar").setExecutor(worldBarCommand);
        getCommand("worldbar").setTabCompleter(worldBarCommand);

        if (configManager.isDiscordEnabled()) {
            String token = configManager.getDiscordToken();
            if (token == null || token.isBlank() || token.equalsIgnoreCase("YOUR_BOT_TOKEN_HERE")) {
                getLogger().warning("[WorldStatus] Discord enabled but token is not set.");
            } else {
                discordBot = new Bot(this, token);
                discordBot.start();
            }
        }

        prometheusServer = new Server(this);
        prometheusServer.start();

        influxWriter = new Influx(this);
        influxWriter.start();

        String platform = Folia.isFolia() ? "Folia" : "Paper/Purpur";
        getLogger().info("[WorldStatus] Enabled on " + platform
                + " (Java " + java.lang.System.getProperty("java.version") + ")");
    }

    @Override
    public void onDisable() {
        if (worldBarCommand != null) worldBarCommand.cleanup();
        if (systemStats     != null) systemStats.stopTracking();
        if (discordBot      != null) discordBot.shutdown();
        if (prometheusServer != null) prometheusServer.stop();
        if (influxWriter    != null) influxWriter.stop();

        instance = null;
        getLogger().info("[WorldStatus] Disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager = new Config(this);
        langManager.reload();
        getLogger().info("[WorldStatus] Configuration reloaded.");
    }

    public static WorldStatus getInstance()       { return instance; }
    public Config   getConfigManager()            { return configManager; }
    public Lang     getLangManager()              { return langManager; }
    public System   getSystemStats()              { return systemStats; }
    public Bot      getDiscordBot()               { return discordBot; }
    public worldbar getWorldBarCommand()          { return worldBarCommand; }
}
