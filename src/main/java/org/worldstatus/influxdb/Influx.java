package org.worldstatus.influxdb;

import org.worldstatus.WorldStatus;
import org.worldstatus.util.System;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * InfluxDB v2 Line Protocol writer.
 * Periodically pushes server metrics to an InfluxDB bucket via HTTP write API.
 * Enable/disable via influxdb.enabled in config.yml.
 */
public class Influx {

    private final WorldStatus plugin;
    private final System stats;
    private ScheduledExecutorService scheduler;

    public Influx(WorldStatus plugin) {
        this.plugin = plugin;
        this.stats  = plugin.getSystemStats();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("influxdb.enabled", false)) return;

        int interval = plugin.getConfigManager().getInfluxInterval();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WorldStatus-InfluxDB");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::writeMetrics, interval, interval, TimeUnit.SECONDS);
        plugin.getLogger().info("[WorldStatus] InfluxDB writer started (interval: " + interval + "s).");
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            plugin.getLogger().info("[WorldStatus] InfluxDB writer stopped.");
        }
    }

    private void writeMetrics() {
        try {
            StringBuilder sb = new StringBuilder();
            String measurement = plugin.getConfigManager().getInfluxMeasurement();
            String serverTag   = escape(Bukkit.getServer().getName());

            if (isEnabled("tps")) {
                double[] tps = stats.getTPS();
                appendLine(sb, measurement, serverTag,
                        "tps_current=" + tps[0]
                        + ",tps_5m=" + tps[1]
                        + ",tps_15m=" + tps[2]
                        + ",tps_30m=" + tps[3]);
            }

            if (isEnabled("mspt")) {
                appendLine(sb, measurement, serverTag,
                        "mspt_current=" + stats.getMSPT()
                        + ",mspt_5m=" + stats.getMSPT5m()
                        + ",mspt_15m=" + stats.getMSPT15m()
                        + ",mspt_30m=" + stats.getMSPT30m());
            }

            if (isEnabled("players")) {
                appendLine(sb, measurement, serverTag,
                        "players_online=" + Bukkit.getOnlinePlayers().size() + "i"
                        + ",players_max=" + Bukkit.getMaxPlayers() + "i");
            }

            if (isEnabled("ram")) {
                long used = stats.getUsedMemoryBytes();
                long max  = stats.getMaxMemoryBytes();
                appendLine(sb, measurement, serverTag,
                        "ram_used=" + used + "i"
                        + ",ram_max=" + max + "i"
                        + ",ram_percent=" + stats.getRAMPercent());
            }

            if (isEnabled("cpu")) {
                appendLine(sb, measurement, serverTag,
                        "cpu_percent=" + stats.getCPUPercent()
                        + ",cpu_system_percent=" + stats.getSystemCPUPercent());
            }

            if (isEnabled("disk")) {
                appendLine(sb, measurement, serverTag,
                        "disk_used=" + stats.getDiskUsedBytes() + "i"
                        + ",disk_total=" + stats.getDiskTotalBytes() + "i"
                        + ",disk_percent=" + stats.getDiskPercent());
            }

            if (isEnabled("chunks")) {
                int loaded = Bukkit.getWorlds().stream()
                        .mapToInt(w -> w.getLoadedChunks().length).sum();
                appendLine(sb, measurement, serverTag, "chunks_loaded=" + loaded + "i");
            }

            if (isEnabled("entities")) {
                int total = Bukkit.getWorlds().stream()
                        .mapToInt(w -> w.getEntities().size()).sum();
                appendLine(sb, measurement, serverTag, "entities_total=" + total + "i");
            }

            if (isEnabled("mobs")) {
                long mobs = Bukkit.getWorlds().stream()
                        .flatMap(w -> w.getEntities().stream())
                        .filter(e -> e.getType() != EntityType.PLAYER
                                  && e.getType() != EntityType.ARMOR_STAND)
                        .count();
                appendLine(sb, measurement, serverTag, "mobs_total=" + mobs + "i");
            }

            if (isEnabled("ping")) {
                double avg = Bukkit.getOnlinePlayers().stream()
                        .mapToInt(Player::getPing).average().orElse(0);
                appendLine(sb, measurement, serverTag, "ping_avg=" + avg);
            }

            if (isEnabled("world_size")) {
                for (World w : Bukkit.getWorlds()) {
                    String worldTag = serverTag + ",world=" + escape(w.getName());
                    appendLine(sb, measurement, worldTag,
                            "world_size_bytes=" + stats.getWorldSize(w) + "i");
                }
            }

            if (sb.length() == 0) return;
            send(sb.toString());

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[WorldStatus] InfluxDB write failed", e);
        }
    }

    private void appendLine(StringBuilder sb, String measurement, String tags, String fields) {
        sb.append(measurement).append(",server=").append(tags)
          .append(' ').append(fields).append('\n');
    }

    private void send(String body) throws Exception {
        String url    = plugin.getConfigManager().getInfluxUrl();
        String org    = plugin.getConfigManager().getInfluxOrg();
        String bucket = plugin.getConfigManager().getInfluxBucket();
        String token  = plugin.getConfigManager().getInfluxToken();

        // InfluxDB v2 write endpoint
        URL endpoint = new URL(url + "/api/v2/write"
                + "?org=" + encode(org)
                + "&bucket=" + encode(bucket)
                + "&precision=ms");

        HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);
        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
        if (!token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Token " + token);
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", java.lang.String.valueOf(bytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        if (code != 204 && code != 200) {
            plugin.getLogger().warning("[WorldStatus] InfluxDB returned HTTP " + code);
        }
        conn.disconnect();
    }

    private boolean isEnabled(String metric) {
        return plugin.getConfig().getBoolean("influxdb.metrics." + metric, true);
    }

    private static String escape(String value) {
        return value.replace(" ", "\\ ").replace(",", "\\,").replace("=", "\\=");
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
