package org.worldstatus;

import org.worldstatus.commands.WorldBarCommand;
import org.worldstatus.commands.WorldStatusCommand;
import org.worldstatus.config.ConfigManager;
import org.worldstatus.discord.DiscordBot;
import org.worldstatus.lang.LangManager;
import org.worldstatus.prometheus.PrometheusServer;
import org.worldstatus.util.FoliaUtil;
import org.worldstatus.util.SystemStats;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class WorldStatusPlugin extends JavaPlugin {

    private static WorldStatusPlugin instance;

    private ConfigManager    configManager;
    private LangManager      langManager;
    private SystemStats      systemStats;
    private DiscordBot       discordBot;
    private PrometheusServer prometheusServer;
    private WorldBarCommand  worldBarCommand;

    @Override
    public void onEnable() {
        instance = this;

        System.setProperty("java.awt.headless", "true");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();
        
        File playerlistJson = new File(getDataFolder(), "playerlist.json");
        if (!playerlistJson.exists()) {
            saveResource("playerlist.json", false);
        }
        
        File worldstatusJson = new File(getDataFolder(), "worldstatus.json");
        if (!worldstatusJson.exists()) {
            saveResource("worldstatus.json", false);
        }

        configManager = new ConfigManager(this);
        langManager   = new LangManager(this);
        systemStats   = new SystemStats(this);

        systemStats.startTracking();

        WorldStatusCommand statusCmd = new WorldStatusCommand(this);
        getCommand("world-status").setExecutor(statusCmd);
        getCommand("world-status").setTabCompleter(statusCmd);

        worldBarCommand = new WorldBarCommand(this);
        getCommand("worldbar").setExecutor(worldBarCommand);
        getCommand("worldbar").setTabCompleter(worldBarCommand);

        if (configManager.isDiscordEnabled()) {
            String token = configManager.getDiscordToken();
            if (token == null || token.isBlank() || token.equalsIgnoreCase("YOUR_BOT_TOKEN_HERE")) {
                getLogger().warning("[WorldStatus] Discord enabled but token is not set.");
            } else {
                discordBot = new DiscordBot(this, token);
                discordBot.start();
            }
        }

        prometheusServer = new PrometheusServer(this);
        prometheusServer.start();

        String platform = FoliaUtil.isFolia() ? "Folia" : "Paper/Purpur";
        getLogger().info("[WorldStatus] Enabled on " + platform + " (Java " + System.getProperty("java.version") + ")");
    }

    @Override
    public void onDisable() {
        if (worldBarCommand != null) {
            worldBarCommand.cleanup();
        }

        if (systemStats != null) {
            systemStats.stopTracking();
        }

        if (discordBot != null) {
            discordBot.shutdown();
        }

        if (prometheusServer != null) {
            prometheusServer.stop();
        }

        instance = null;

        getLogger().info("[WorldStatus] Disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager = new ConfigManager(this);
        langManager.reload();
        getLogger().info("[WorldStatus] Configuration reloaded.");
    }

    public static WorldStatusPlugin getInstance() { return instance; }
    public ConfigManager   getConfigManager()     { return configManager; }
    public LangManager     getLangManager()        { return langManager; }
    public SystemStats     getSystemStats()        { return systemStats; }
    public DiscordBot      getDiscordBot()         { return discordBot; }
    public WorldBarCommand getWorldBarCommand()    { return worldBarCommand; }
}