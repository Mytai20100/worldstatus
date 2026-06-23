package org.worldstatus.discord.listeners;

import org.worldstatus.WorldStatusPlugin;
import org.worldstatus.discord.CanvaStyleRenderer;
import org.worldstatus.discord.StatusImageBuilder;
import org.worldstatus.util.FoliaUtil;
import org.worldstatus.util.SystemStats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DiscordCommandListener extends ListenerAdapter {

    private static final long AUTO_REFRESH_EXPIRE_MS = 300_000L;

    private final WorldStatusPlugin plugin;
    private final StatusImageBuilder imageBuilder;
    private final CanvaStyleRenderer canvaRenderer;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "WorldStatus-Discord");
        t.setDaemon(true);
        return t;
    });

    public DiscordCommandListener(WorldStatusPlugin plugin) {
        this.plugin       = plugin;
        this.imageBuilder = new StatusImageBuilder(plugin);
        this.canvaRenderer = new CanvaStyleRenderer(plugin);
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        plugin.getLogger().info("[WorldStatus] Discord slash command received: /" + event.getName()
                + " from " + event.getUser().getEffectiveName());
        switch (event.getName()) {
            case "world-status" -> handleWorldStatus(event);
            case "playerlist"   -> handlePlayerList(event);
        }
    }

    private void handleWorldStatus(SlashCommandInteractionEvent event) {
        String mode = plugin.getConfig().getString("discord.world-status-mode", "embed");
        
        event.deferReply().queue(
                hook -> {
                    CompletableFuture<StatusImageBuilder.BukkitSnapshot> snapshotFuture = new CompletableFuture<>();
                    FoliaUtil.runSync(plugin, () -> {
                        try {
                            snapshotFuture.complete(StatusImageBuilder.snapshotOnMainThread(plugin));
                        } catch (Exception e) {
                            snapshotFuture.completeExceptionally(e);
                        }
                    });

                    scheduler.execute(() -> {
                        try {
                            StatusImageBuilder.BukkitSnapshot snapshot = snapshotFuture.get(10, TimeUnit.SECONDS);
                            
                            BufferedImage img = canvaRenderer.renderWorldStatus(snapshot);
                            if (img == null) {
                                img = imageBuilder.build(snapshot);
                            }
                            
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(img, "png", baos);

                            if ("canva".equalsIgnoreCase(mode)) {
                                hook.sendFiles(FileUpload.fromData(baos.toByteArray(), "worldstatus.png"))
                                        .queue(
                                                ok  -> plugin.getLogger().info("[WorldStatus] /world-status sent successfully."),
                                                err -> plugin.getLogger().warning("[WorldStatus] Failed to send world-status: " + err.getMessage())
                                        );
                            } else {
                                MessageEmbed embed = buildStatusEmbed(snapshot);
                                hook.sendFiles(FileUpload.fromData(baos.toByteArray(), "worldstatus.png"))
                                        .addEmbeds(embed)
                                        .queue(
                                                ok  -> plugin.getLogger().info("[WorldStatus] /world-status sent successfully."),
                                                err -> plugin.getLogger().warning("[WorldStatus] Failed to send world-status: " + err.getMessage())
                                        );
                            }
                        } catch (Throwable ex) {
                            plugin.getLogger().severe("[WorldStatus] Exception in /world-status: " + ex);
                            ex.printStackTrace();
                            hook.sendMessage("❌ Lỗi: `" + ex + "`").setEphemeral(true).queue();
                        }
                    });
                },
                err -> plugin.getLogger().severe("[WorldStatus] deferReply failed for /world-status: " + err.getMessage())
        );
    }

    private MessageEmbed buildStatusEmbed(StatusImageBuilder.BukkitSnapshot snapshot) {
        SystemStats sys = plugin.getSystemStats();
        double[]    tps = sys.getTPS();
        int      online = snapshot.onlinePlayers();
        int         max = snapshot.maxPlayers();

        String tpsIndicator = tps[0] >= 18 ? "[OK]" : tps[0] >= 15 ? "[WARN]" : "[LOW]";

        return new EmbedBuilder()
                .setTitle("WorldStatus")
                .setColor(0x5B8DFF)
                .setImage("attachment://worldstatus.png")
                .addField("TPS",     tpsIndicator + " " + String.format("%.2f", tps[0]), true)
                .addField("MSPT",    String.format("%.2f ms", sys.getMSPT()),             true)
                .addField("Players", online + " / " + max,                                true)
                .setFooter("Requested at")
                .setTimestamp(Instant.now())
                .build();
    }

    private void handlePlayerList(SlashCommandInteractionEvent event) {
        String renderMode = plugin.getConfig().getString("discord.playerlist-render-mode", "text");
        
        if ("canva".equalsIgnoreCase(renderMode)) {
            handlePlayerListCanva(event);
        } else {
            handlePlayerListText(event);
        }
    }

    private void handlePlayerListCanva(SlashCommandInteractionEvent event) {
        event.deferReply().queue(
                hook -> {
                    CompletableFuture<List<Player>> snapshotFuture = new CompletableFuture<>();
                    FoliaUtil.runSync(plugin, () -> {
                        try {
                            snapshotFuture.complete(new ArrayList<>(Bukkit.getOnlinePlayers()));
                        } catch (Exception e) {
                            snapshotFuture.completeExceptionally(e);
                        }
                    });

                    scheduler.execute(() -> {
                        try {
                            List<Player> players = snapshotFuture.get(10, TimeUnit.SECONDS);
                            BufferedImage img = canvaRenderer.renderPlayerList(players);
                            
                            if (img == null) {
                                hook.sendMessage("Canva rendering is not enabled or configured.").setEphemeral(true).queue();
                                return;
                            }
                            
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(img, "png", baos);

                            hook.sendFiles(FileUpload.fromData(baos.toByteArray(), "playerlist.png")).queue(message -> {
                                int refreshSec = plugin.getConfigManager().getPlayerListRefreshSeconds();
                                if (refreshSec <= 0) return;

                                long startTime = System.currentTimeMillis();
                                ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];

                                holder[0] = scheduler.scheduleAtFixedRate(() -> {
                                    if (System.currentTimeMillis() - startTime > AUTO_REFRESH_EXPIRE_MS) {
                                        holder[0].cancel(false);
                                        return;
                                    }
                                    
                                    CompletableFuture<List<Player>> refreshFuture = new CompletableFuture<>();
                                    FoliaUtil.runSync(plugin, () -> {
                                        try {
                                            refreshFuture.complete(new ArrayList<>(Bukkit.getOnlinePlayers()));
                                        } catch (Exception e) {
                                            refreshFuture.completeExceptionally(e);
                                        }
                                    });
                                    
                                    try {
                                        List<Player> refreshedPlayers = refreshFuture.get(10, TimeUnit.SECONDS);
                                        BufferedImage refreshedImg = canvaRenderer.renderPlayerList(refreshedPlayers);
                                        if (refreshedImg == null) {
                                            holder[0].cancel(false);
                                            return;
                                        }
                                        
                                        ByteArrayOutputStream refreshBaos = new ByteArrayOutputStream();
                                        ImageIO.write(refreshedImg, "png", refreshBaos);
                                        
                                        message.editMessage("")
                                                .setFiles(FileUpload.fromData(refreshBaos.toByteArray(), "playerlist.png"))
                                                .queue(ok -> {}, err -> holder[0].cancel(false));
                                    } catch (Exception ignored) {
                                        holder[0].cancel(false);
                                    }
                                }, refreshSec, refreshSec, TimeUnit.SECONDS);
                            });
                        } catch (Throwable ex) {
                            plugin.getLogger().severe("[WorldStatus] Exception in /playerlist (canva): " + ex);
                            hook.sendMessage("❌ Lỗi: `" + ex + "`").setEphemeral(true).queue();
                        }
                    });
                },
                err -> plugin.getLogger().severe("[WorldStatus] deferReply failed for /playerlist: " + err.getMessage())
        );
    }

    private void handlePlayerListText(SlashCommandInteractionEvent event) {
        event.deferReply().queue(
                hook -> {
                    CompletableFuture<PlayerListSnapshot> snapshotFuture = new CompletableFuture<>();
                    FoliaUtil.runSync(plugin, () -> {
                        try {
                            snapshotFuture.complete(PlayerListSnapshot.capture());
                        } catch (Exception e) {
                            snapshotFuture.completeExceptionally(e);
                        }
                    });

                    scheduler.execute(() -> {
                        try {
                            PlayerListSnapshot snapshot = snapshotFuture.get(10, TimeUnit.SECONDS);
                            hook.sendMessageEmbeds(buildPlayerListEmbed(snapshot)).queue(message -> {
                                int refreshSec = plugin.getConfigManager().getPlayerListRefreshSeconds();
                                if (refreshSec <= 0) return;

                                long startTime = System.currentTimeMillis();
                                ScheduledFuture<?>[] holder = new ScheduledFuture<?>[1];

                                holder[0] = scheduler.scheduleAtFixedRate(() -> {
                                    if (System.currentTimeMillis() - startTime > AUTO_REFRESH_EXPIRE_MS) {
                                        holder[0].cancel(false);
                                        return;
                                    }
                                    
                                    CompletableFuture<PlayerListSnapshot> refreshFuture = new CompletableFuture<>();
                                    FoliaUtil.runSync(plugin, () -> {
                                        try {
                                            refreshFuture.complete(PlayerListSnapshot.capture());
                                        } catch (Exception e) {
                                            refreshFuture.completeExceptionally(e);
                                        }
                                    });
                                    try {
                                        PlayerListSnapshot refreshed = refreshFuture.get(10, TimeUnit.SECONDS);
                                        message.editMessageEmbeds(buildPlayerListEmbed(refreshed)).queue(
                                                ok  -> {},
                                                err -> holder[0].cancel(false));
                                    } catch (Exception ignored) {
                                        holder[0].cancel(false);
                                    }
                                }, refreshSec, refreshSec, TimeUnit.SECONDS);
                            });
                        } catch (Throwable ex) {
                            plugin.getLogger().severe("[WorldStatus] Exception in /playerlist: " + ex);
                            hook.sendMessage("❌ Lỗi: `" + ex + "`").setEphemeral(true).queue();
                        }
                    });
                },
                err -> plugin.getLogger().severe("[WorldStatus] deferReply failed for /playerlist: " + err.getMessage())
        );
    }

    /** Immutable snapshot of player data captured on the main thread. */
    private record PlayerListSnapshot(List<PlayerEntry> players, int maxPlayers) {
        private record PlayerEntry(String name, int ping, String world) {}

        static PlayerListSnapshot capture() {
            var online = Bukkit.getOnlinePlayers();
            int max = Bukkit.getMaxPlayers();
            List<PlayerEntry> entries = new ArrayList<>();
            for (var p : online) {
                entries.add(new PlayerEntry(p.getName(), p.getPing(), p.getWorld().getName()));
            }
            return new PlayerListSnapshot(entries, max);
        }
    }

    private MessageEmbed buildPlayerListEmbed(PlayerListSnapshot snapshot) {
        List<PlayerListSnapshot.PlayerEntry> players = snapshot.players();
        int online = players.size();
        int max    = snapshot.maxPlayers();

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Player List  " + online + " / " + max)
                .setColor(online == 0 ? 0x888888 : 0x4CAF50)
                .setTimestamp(Instant.now())
                .setFooter("Last updated");

        if (players.isEmpty()) {
            eb.setDescription("*No players online.*");
        } else {
            StringJoiner sj = new StringJoiner("\n");
            sj.add("```");
            sj.add(String.format("%-20s %6s  %s", "Name", "Ping", "World"));
            sj.add("-".repeat(42));

            players.stream()
                    .sorted(Comparator.comparing(PlayerListSnapshot.PlayerEntry::name, String.CASE_INSENSITIVE_ORDER))
                    .forEach(p -> {
                        String world = p.world();
                        if (world.length() > 12) world = world.substring(0, 11) + ".";
                        sj.add(String.format("%-20s %5dms  %s", p.name(), p.ping(), world));
                    });

            sj.add("```");
            eb.setDescription(sj.toString());
        }

        return eb.build();
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}