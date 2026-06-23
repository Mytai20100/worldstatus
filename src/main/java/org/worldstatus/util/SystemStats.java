package org.worldstatus.util;

import com.sun.management.OperatingSystemMXBean;
import org.worldstatus.WorldStatusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

public class SystemStats {

    private static final int TICK_SAMPLE_CURRENT = 100;
    private static final int TICK_SAMPLE_5M = 6000;
    private static final int TICK_SAMPLE_15M = 18000;
    private static final int TICK_SAMPLE_30M = 36000;

    private final WorldStatusPlugin plugin;
    private final OperatingSystemMXBean osBean;

    private final Deque<Long> tickTimesCurrent = new ArrayDeque<>(TICK_SAMPLE_CURRENT + 1);
    private final Deque<Long> tickTimes5m = new ArrayDeque<>(TICK_SAMPLE_5M + 1);
    private final Deque<Long> tickTimes15m = new ArrayDeque<>(TICK_SAMPLE_15M + 1);
    private final Deque<Long> tickTimes30m = new ArrayDeque<>(TICK_SAMPLE_30M + 1);
    private long lastTickNano = -1;

    private volatile Object trackingTask;
    private final AtomicBoolean trackingActive = new AtomicBoolean(false);

    public SystemStats(WorldStatusPlugin plugin) {
        this.plugin = plugin;
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    /**
     * Starts a 1-tick repeating timer that records inter-tick durations for MSPT calculation.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public void startTracking() {
        if (!trackingActive.compareAndSet(false, true)) {
            return; // already running
        }

        if (FoliaUtil.isFolia()) {
            // Folia: GlobalRegionScheduler for server-wide tick timing
            io.papermc.paper.threadedregions.scheduler.ScheduledTask task =
                    plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
                        if (!trackingActive.get()) {
                            t.cancel();
                            return;
                        }
                        recordTick();
                    }, 1L, 1L);
            trackingTask = task;
        } else {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!trackingActive.get()) return; // will be cancelled by stopTracking()
                recordTick();
            }, 1L, 1L);
            trackingTask = task;
        }
    }

    /**
     * Stops the tick-tracking timer and releases resources.
     * Called from {@code WorldStatusPlugin#onDisable()}.
     */
    public void stopTracking() {
        if (!trackingActive.compareAndSet(true, false)) {
            return;
        }
        Object task = trackingTask;
        trackingTask = null;
        if (task instanceof BukkitTask bt) {
            try { bt.cancel(); } catch (Exception ignored) {}
        } else if (task instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask st) {
            try { st.cancel(); } catch (Exception ignored) {}
        }
        synchronized (tickTimesCurrent) {
            tickTimesCurrent.clear();
        }
        synchronized (tickTimes5m) {
            tickTimes5m.clear();
        }
        synchronized (tickTimes15m) {
            tickTimes15m.clear();
        }
        synchronized (tickTimes30m) {
            tickTimes30m.clear();
        }
    }

    private void recordTick() {
        long now = System.nanoTime();
        if (lastTickNano > 0) {
            long duration = now - lastTickNano;
            
            synchronized (tickTimesCurrent) {
                if (tickTimesCurrent.size() >= TICK_SAMPLE_CURRENT) tickTimesCurrent.pollFirst();
                tickTimesCurrent.addLast(duration);
            }
            
            synchronized (tickTimes5m) {
                if (tickTimes5m.size() >= TICK_SAMPLE_5M) tickTimes5m.pollFirst();
                tickTimes5m.addLast(duration);
            }
            
            synchronized (tickTimes15m) {
                if (tickTimes15m.size() >= TICK_SAMPLE_15M) tickTimes15m.pollFirst();
                tickTimes15m.addLast(duration);
            }
            
            synchronized (tickTimes30m) {
                if (tickTimes30m.size() >= TICK_SAMPLE_30M) tickTimes30m.pollFirst();
                tickTimes30m.addLast(duration);
            }
        }
        lastTickNano = now;
    }

    // ---- Memory stats ------------------------------------------------------

    public long getUsedMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    public long getMaxMemoryBytes() {
        return Runtime.getRuntime().maxMemory();
    }

    public double getRAMPercent() {
        long max = getMaxMemoryBytes();
        return max == 0 ? 0 : (double) getUsedMemoryBytes() / max * 100.0;
    }

    // ---- CPU stats ---------------------------------------------------------

    public double getCPUPercent() {
        double load = osBean.getProcessCpuLoad();
        return load < 0 ? 0 : load * 100.0;
    }

    public double getSystemCPUPercent() {
        double load = osBean.getCpuLoad();
        return load < 0 ? 0 : load * 100.0;
    }

    // ---- Disk stats --------------------------------------------------------

    public double getDiskPercent() {
        try {
            FileStore store = Files.getFileStore(plugin.getDataFolder().toPath());
            long total = store.getTotalSpace();
            long used  = total - store.getUsableSpace();
            return total == 0 ? 0 : (double) used / total * 100.0;
        } catch (Exception e) {
            return 0;
        }
    }

    public long getDiskTotalBytes() {
        try {
            return Files.getFileStore(plugin.getDataFolder().toPath()).getTotalSpace();
        } catch (Exception e) { return 0; }
    }

    public long getDiskUsedBytes() {
        try {
            FileStore store = Files.getFileStore(plugin.getDataFolder().toPath());
            return store.getTotalSpace() - store.getUsableSpace();
        } catch (Exception e) { return 0; }
    }

    // ---- World size --------------------------------------------------------

    public long getWorldSize(World world) {
        return getFolderSize(world.getWorldFolder());
    }

    public long getAllWorldsSize() {
        long total = 0;
        for (World w : Bukkit.getWorlds()) {
            total += getWorldSize(w);
        }
        return total;
    }

    /**
     * Recursively sums file sizes under {@code dir}.
     * NOTE: This is a blocking I/O operation. Only call it from an async context
     * or it may stall the main thread on large world folders.
     */
    private long getFolderSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            size += f.isFile() ? f.length() : getFolderSize(f);
        }
        return size;
    }

    // ---- TPS ---------------------------------------------------------------

    /**
     * On Folia, Bukkit.getTPS() always returns 20.0 because TPS is per-region.
     * Instead we derive TPS from our own MSPT ring buffer:
     *   TPS = min(1000 / mspt, 20)  where mspt = average ms per tick.
     * On Bukkit/Paper we still use the native API since it provides 5m/15m averages.
     */
    public double[] getTPS() {
        if (FoliaUtil.isFolia()) {
            double msptCurrent = getMSPT();
            double mspt5m = getMSPT5m();
            double mspt15m = getMSPT15m();
            double mspt30m = getMSPT30m();
            
            double tpsCurrent = msptCurrent <= 0 ? 20.0 : Math.min(1000.0 / msptCurrent, 20.0);
            double tps5m = mspt5m <= 0 ? 20.0 : Math.min(1000.0 / mspt5m, 20.0);
            double tps15m = mspt15m <= 0 ? 20.0 : Math.min(1000.0 / mspt15m, 20.0);
            double tps30m = mspt30m <= 0 ? 20.0 : Math.min(1000.0 / mspt30m, 20.0);
            
            return new double[]{tpsCurrent, tps5m, tps15m, tps30m};
        }
        try {
            double[] tps = Bukkit.getTPS();
            double[] result = new double[4];
            result[0] = Math.min(tps[0], 20.0);
            result[1] = tps.length > 1 ? Math.min(tps[1], 20.0) : result[0];
            result[2] = tps.length > 2 ? Math.min(tps[2], 20.0) : result[0];
            result[3] = result[2];
            return result;
        } catch (Exception e) {
            return new double[]{20.0, 20.0, 20.0, 20.0};
        }
    }

    public double getTPS1m() { return getTPS()[0]; }
    public double getTPS5m() { return getTPS()[1]; }
    public double getTPS15m() { return getTPS()[2]; }
    public double getTPS30m() { return getTPS()[3]; }

    // ---- MSPT --------------------------------------------------------------

    public double getMSPT() {
        synchronized (tickTimesCurrent) {
            if (tickTimesCurrent.isEmpty()) return 0;
            long sum = 0;
            for (long t : tickTimesCurrent) sum += t;
            return (sum / (double) tickTimesCurrent.size()) / 1_000_000.0;
        }
    }

    public double getMSPT5m() {
        synchronized (tickTimes5m) {
            if (tickTimes5m.isEmpty()) return 0;
            long sum = 0;
            for (long t : tickTimes5m) sum += t;
            return (sum / (double) tickTimes5m.size()) / 1_000_000.0;
        }
    }

    public double getMSPT15m() {
        synchronized (tickTimes15m) {
            if (tickTimes15m.isEmpty()) return 0;
            long sum = 0;
            for (long t : tickTimes15m) sum += t;
            return (sum / (double) tickTimes15m.size()) / 1_000_000.0;
        }
    }

    public double getMSPT30m() {
        synchronized (tickTimes30m) {
            if (tickTimes30m.isEmpty()) return 0;
            long sum = 0;
            for (long t : tickTimes30m) sum += t;
            return (sum / (double) tickTimes30m.size()) / 1_000_000.0;
        }
    }

    public double getMSPTPercent() {
        return Math.min(getMSPT() / 50.0 * 100.0, 100.0);
    }
}