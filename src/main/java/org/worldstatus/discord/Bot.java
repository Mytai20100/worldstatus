package org.worldstatus.discord;

import org.worldstatus.WorldStatus;
import org.worldstatus.discord.listeners.Discord;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;

public class Bot {

    private final WorldStatus plugin;
    private final String token;
    private volatile JDA jda;
    private Discord commandListener;

    public Bot(WorldStatus plugin, String token) {
        this.plugin = plugin;
        this.token  = token;
    }

    public void start() {
        plugin.getLogger().info("[WorldStatus] Starting Discord bot...");
        Thread botThread = new Thread(() -> {
            try {
                commandListener = new Discord(plugin);

                jda = JDABuilder.createLight(token)
                        .addEventListeners(commandListener)
                        .build();

                jda.awaitReady();
                registerCommands();

                plugin.getLogger().info("[WorldStatus] Discord bot connected: " + jda.getSelfUser().getName());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getLogger().severe("[WorldStatus] Discord bot interrupted during startup.");
            } catch (Exception e) {
                plugin.getLogger().severe("[WorldStatus] Discord bot failed to start: " + e.getMessage());
                jda = null;
            }
        }, "WorldStatus-BotStartup");
        botThread.setDaemon(true);
        botThread.start();
    }

    public void shutdown() {
        if (commandListener != null) commandListener.shutdown();
        if (jda != null) {
            plugin.getLogger().info("[WorldStatus] Shutting down Discord bot...");
            jda.shutdown();
            jda = null;
        }
    }

    private void registerCommands() {
        var commands = List.of(
                Commands.slash("world-status", "Show server status as a visual card"),
                Commands.slash("playerlist",   "Show live player list with ping")
        );

        String guildId = plugin.getConfigManager().getDiscordGuildId();

        if (guildId != null && !guildId.isBlank()) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                commands.forEach(cmd -> guild.upsertCommand(cmd).queue());
                plugin.getLogger().info("[WorldStatus] Guild slash commands registered.");
                return;
            }
            plugin.getLogger().warning("[WorldStatus] Guild ID '" + guildId + "' not found, falling back to global.");
        }

        commands.forEach(cmd -> jda.upsertCommand(cmd).queue(
                ok  -> {},
                err -> plugin.getLogger().warning("[WorldStatus] Command upsert failed: " + err.getMessage())
        ));
        plugin.getLogger().info("[WorldStatus] Global slash commands registered (up to 1h propagation).");
    }

    public JDA     getJDA()      { return jda; }
    public boolean isRunning()   { return jda != null; }
}
