package org.worldstatus.util;

import org.worldstatus.WorldStatus;
import org.bukkit.Bukkit;

import java.util.concurrent.TimeUnit;

public final class Folia {

    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {}
        IS_FOLIA = folia;
    }

    private Folia() {}

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public static void runAsync(WorldStatus plugin, Runnable task) {
        if (IS_FOLIA) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runAsyncTimer(WorldStatus plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            long delayMs  = delayTicks  * 50L;
            long periodMs = periodTicks * 50L;
            plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin, t -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    public static void runSync(WorldStatus plugin, Runnable task) {
        if (IS_FOLIA) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runSyncTimer(WorldStatus plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin, t -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }
}
