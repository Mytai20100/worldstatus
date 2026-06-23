package org.worldstatus.prometheus;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.worldstatus.WorldStatusPlugin;
import org.worldstatus.util.SystemStats;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.HashMap;
import java.util.Map;

public class PrometheusMetrics {

    private final WorldStatusPlugin plugin;
    private final SystemStats stats;

    public PrometheusMetrics(WorldStatusPlugin plugin) {
        this.plugin = plugin;
        this.stats = plugin.getSystemStats();
    }

    public String generateMetrics() {
        StringBuilder sb = new StringBuilder();

        if (isEnabled("tps")) {
            double[] tps = stats.getTPS();
            metric(sb, "minecraft_tps_current", tps[0]);
            metric(sb, "minecraft_tps_5m", tps.length > 1 ? tps[1] : tps[0]);
            metric(sb, "minecraft_tps_15m", tps.length > 2 ? tps[2] : tps[0]);
            metric(sb, "minecraft_tps_30m", tps.length > 2 ? tps[2] : tps[0]);
        }

        if (isEnabled("mspt")) {
            double mspt = stats.getMSPT();
            metric(sb, "minecraft_mspt_current", mspt);
            metric(sb, "minecraft_mspt_5m", mspt);
            metric(sb, "minecraft_mspt_15m", mspt);
            metric(sb, "minecraft_mspt_30m", mspt);
        }

        if (isEnabled("players")) {
            metric(sb, "minecraft_players_max", Bukkit.getMaxPlayers());
            metric(sb, "minecraft_players_online", Bukkit.getOnlinePlayers().size());
        }

        if (isEnabled("ram")) {
            long used = stats.getUsedMemoryBytes();
            long max = stats.getMaxMemoryBytes();
            long free = max - used;
            metric(sb, "minecraft_ram_total_bytes", max);
            metric(sb, "minecraft_ram_used_bytes", used);
            metric(sb, "minecraft_ram_free_bytes", free);
        }

        if (isEnabled("cpu")) {
            metric(sb, "minecraft_cpu_cores", Runtime.getRuntime().availableProcessors());
            metric(sb, "minecraft_cpu_usage_percent", stats.getCPUPercent());
        }

        if (isEnabled("disk")) {
            long total = stats.getDiskTotalBytes();
            long used = stats.getDiskUsedBytes();
            metric(sb, "minecraft_disk_total_bytes", total);
            metric(sb, "minecraft_disk_used_bytes", used);
        }

        if (isEnabled("network")) {
            metric(sb, "minecraft_network_in_bytes", 0);
            metric(sb, "minecraft_network_out_bytes", 0);
        }

        if (isEnabled("packets")) {
            metric(sb, "minecraft_packets_in", 0);
            metric(sb, "minecraft_packets_out", 0);
        }

        if (isEnabled("ping")) {
            double avgPing = Bukkit.getOnlinePlayers().stream()
                    .mapToInt(Player::getPing)
                    .average()
                    .orElse(0);
            metric(sb, "minecraft_ping_average_ms", avgPing);
            metric(sb, "minecraft_ping_current_ms", avgPing);
        }

        if (isEnabled("chunks")) {
            int loadedChunks = Bukkit.getWorlds().stream()
                    .mapToInt(w -> w.getLoadedChunks().length)
                    .sum();
            metric(sb, "minecraft_chunks_loaded", loadedChunks);
            metric(sb, "minecraft_chunks_total", loadedChunks);
        }

        if (isEnabled("entities")) {
            int totalEntities = Bukkit.getWorlds().stream()
                    .mapToInt(w -> w.getEntities().size())
                    .sum();
            metric(sb, "minecraft_entities_total", totalEntities);

            for (World world : Bukkit.getWorlds()) {
                int chunks = world.getLoadedChunks().length;
                int entities = world.getEntities().size();
                if (chunks > 0) {
                    metric(sb, "minecraft_entities_per_chunk", entities / (double) chunks, 
                           Map.of("world", world.getName(), "dimension", world.getEnvironment().name()));
                }
            }
        }

        if (isEnabled("mobs")) {
            long mobCount = Bukkit.getWorlds().stream()
                    .flatMap(w -> w.getEntities().stream())
                    .filter(e -> e.getType() != EntityType.PLAYER && e.getType() != EntityType.ARMOR_STAND)
                    .count();
            metric(sb, "minecraft_mobs_total", mobCount);
        }

        if (isEnabled("player_events")) {
            metric(sb, "minecraft_player_join_rate", 0);
            metric(sb, "minecraft_player_leave_rate", 0);
        }

        if (isEnabled("player_session")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                long sessionTime = (System.currentTimeMillis() - p.getFirstPlayed()) / 1000;
                metric(sb, "minecraft_player_session_time_seconds", sessionTime, 
                       Map.of("player", p.getName()));
            }
        }

        if (isEnabled("player_ping")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                metric(sb, "minecraft_player_ping_ms", p.getPing(), 
                       Map.of("player", p.getName()));
            }
        }

        if (isEnabled("deaths")) {
            metric(sb, "minecraft_deaths_total", 0);
        }

        if (isEnabled("classes")) {
            ClassLoadingMXBean classBean = ManagementFactory.getClassLoadingMXBean();
            metric(sb, "minecraft_classes_loaded", classBean.getLoadedClassCount());
        }

        if (isEnabled("heap")) {
            MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
            long heapUsed = memBean.getHeapMemoryUsage().getUsed();
            long heapMax = memBean.getHeapMemoryUsage().getMax();
            metric(sb, "minecraft_heap_used_bytes", heapUsed);
            metric(sb, "minecraft_heap_max_bytes", heapMax);
        }

        if (isEnabled("gc")) {
            long gcTime = 0;
            long gcCount = 0;
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcTime += gc.getCollectionTime();
                gcCount += gc.getCollectionCount();
            }
            metric(sb, "minecraft_gc_time_ms", gcTime);
            metric(sb, "minecraft_gc_count", gcCount);
        }

        if (isEnabled("io")) {
            metric(sb, "minecraft_io_read_bytes", 0);
            metric(sb, "minecraft_io_write_bytes", 0);
        }

        if (isEnabled("chunk_operations")) {
            metric(sb, "minecraft_chunk_load_time_ms", 0);
            metric(sb, "minecraft_chunk_gen_time_ms", 0);
            metric(sb, "minecraft_chunk_save_time_ms", 0);
            metric(sb, "minecraft_chunk_ticket_count", 0);
        }

        if (isEnabled("tick")) {
            metric(sb, "minecraft_tick_rate", stats.getTPS1m());
            metric(sb, "minecraft_tick_time_ms", stats.getMSPT());
        }

        if (isEnabled("plugins")) {
            metric(sb, "minecraft_plugins_loaded", Bukkit.getPluginManager().getPlugins().length);
        }

        if (isEnabled("world_size")) {
            for (World world : Bukkit.getWorlds()) {
                long size = stats.getWorldSize(world);
                metric(sb, "minecraft_world_size_bytes", size, 
                       Map.of("world", world.getName()));
            }
        }

        return sb.toString();
    }

    private boolean isEnabled(String metric) {
        return plugin.getConfig().getBoolean("prometheus.metrics." + metric, true);
    }

    private void metric(StringBuilder sb, String name, double value) {
        metric(sb, name, value, null);
    }

    private void metric(StringBuilder sb, String name, double value, Map<String, String> labels) {
        sb.append(name);
        if (labels != null && !labels.isEmpty()) {
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                if (!first) sb.append(",");
                sb.append(entry.getKey()).append("=\"").append(escape(entry.getValue())).append("\"");
                first = false;
            }
            sb.append("}");
        }
        sb.append(" ").append(value).append("\n");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
