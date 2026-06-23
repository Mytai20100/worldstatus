package org.worldstatus.commands;

import org.worldstatus.WorldStatusPlugin;
import org.worldstatus.config.ConfigManager;
import org.worldstatus.lang.LangManager;
import org.worldstatus.util.FoliaUtil;
import org.worldstatus.util.FormatUtil;
import org.worldstatus.util.SystemStats;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorldBarCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TYPES = List.of("ram", "cpu", "disk", "mspt", "tps");

    private final WorldStatusPlugin plugin;

    // ---- Per-player, per-type tracking (thread-safe) -----------------------
    // UUID -> (type -> BarEntry)
    private final Map<UUID, Map<String, BarEntry>> activeEntries = new ConcurrentHashMap<>();

    /**
     * Holds everything needed to manage one live BossBar for one player+type.
     * The {@code cancelled} flag is the single source of truth — both the refresh task
     * and the auto-remove timer check it so they can never race each other.
     */
    private static final class BarEntry {
        final BossBar       bar;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        // BukkitTask (Bukkit path) or ScheduledTask (Folia path) — set right after construction
        volatile Object task;

        BarEntry(BossBar bar) { this.bar = bar; }
    }

    public WorldBarCommand(WorldStatusPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Command entry point -----------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("worldstatus.bar")) {
            sender.sendMessage(plugin.getLangManager().get("messages.no-permission"));
            return true;
        }

        boolean isPlayer = sender instanceof Player;

        if (args.length == 0) {
            // No-args: toggle ALL as a group.
            // If ANY bar is currently active for this player -> remove all.
            // If none are active -> show all.
            if (isPlayer) {
                UUID uuid = ((Player) sender).getUniqueId();
                Map<String, BarEntry> playerBars = activeEntries.get(uuid);
                boolean anyActive = playerBars != null && !playerBars.isEmpty();
                if (anyActive) {
                    removeAllBarsFor(uuid);
                } else {
                    for (String type : TYPES) {
                        showBossBar((Player) sender, type);
                    }
                }
            } else {
                for (String type : TYPES) {
                    printConsoleLine(sender, type);
                }
            }
            return true;
        }

        String type = args[0].toLowerCase();
        if (!TYPES.contains(type)) {
            sender.sendMessage(plugin.getLangManager().get("worldbar.usage"));
            return true;
        }

        if (!isPlayer) {
            printConsoleLine(sender, type);
            return true;
        }

        Player player = (Player) sender;
        UUID   uuid   = player.getUniqueId();

        // Toggle: if bar for this type is already active -> remove it (turn off)
        Map<String, BarEntry> playerBars = activeEntries.get(uuid);
        if (playerBars != null) {
            BarEntry existing = playerBars.get(type);
            if (existing != null) {
                removeEntry(uuid, type, existing);
                return true; // toggled off
            }
        }

        // Not active -> show it
        showBossBar(player, type);
        return true;
    }

    // ---- Console output (snapshot, no live update) -------------------------

    private void printConsoleLine(CommandSender sender, String type) {
        ConfigManager cfg    = plugin.getConfigManager();
        SystemStats   sys    = plugin.getSystemStats();
        LangManager   lang   = plugin.getLangManager();
        String        filled = cfg.getBarCharFilled();
        String        empty  = cfg.getBarCharEmpty();
        int           len    = cfg.getBarLength();
        boolean       colors = cfg.isBarColorEnabled();

        String labelStr = lang.get("worldbar.label." + type);
        String bar;
        String extra;

        switch (type) {
            case "ram" -> {
                double pct = sys.getRAMPercent();
                bar   = FormatUtil.buildBar(pct, len, filled, empty, colors);
                extra = FormatUtil.formatPercent(pct) + " \u00a77("
                        + FormatUtil.formatBytes(sys.getUsedMemoryBytes())
                        + " / " + FormatUtil.formatBytes(sys.getMaxMemoryBytes()) + ")";
            }
            case "cpu" -> {
                double pct = sys.getCPUPercent();
                bar   = FormatUtil.buildBar(pct, len, filled, empty, colors);
                extra = FormatUtil.formatPercent(pct);
            }
            case "disk" -> {
                double pct = sys.getDiskPercent();
                bar   = FormatUtil.buildBar(pct, len, filled, empty, colors);
                extra = FormatUtil.formatPercent(pct) + " \u00a77("
                        + FormatUtil.formatBytes(sys.getDiskUsedBytes())
                        + " / " + FormatUtil.formatBytes(sys.getDiskTotalBytes()) + ")";
            }
            case "mspt" -> {
                double mspt = sys.getMSPT();
                double pct  = sys.getMSPTPercent();
                bar   = FormatUtil.buildBar(pct, len, filled, empty, colors);
                extra = FormatUtil.msptColor(mspt) + FormatUtil.formatMSPT(mspt) + "\u00a7r \u00a77/ 50ms";
            }
            case "tps" -> {
                double[] tps = sys.getTPS();
                bar   = FormatUtil.buildTPSBar(tps[0], len, filled, empty, colors);
                extra = FormatUtil.tpsColor(tps[0]) + FormatUtil.formatTPS(tps[0])
                        + " TPS\u00a7r \u00a77(5m: " + FormatUtil.formatTPS(tps[1])
                        + " | 15m: " + FormatUtil.formatTPS(tps[2])
                        + " | Players: " + Bukkit.getOnlinePlayers().size() + ")";
            }
            default -> { return; }
        }

        sender.sendMessage(lang.get("worldbar.line", Map.of(
                "label", labelStr,
                "bar",   bar,
                "extra", extra
        )));
    }

    // ---- BossBar creation --------------------------------------------------

    private void showBossBar(Player player, String type) {
        String labelStr = plugin.getLangManager().get("worldbar.label." + type);

        // Build initial snapshot so the bar shows real data immediately (no blank frame)
        BarSnapshot snap     = buildSnapshot(type);
        double      progress = Math.max(0.001, Math.min(1.0, snap.percent / 100.0));

        BossBar bossBar = Bukkit.createBossBar(
                labelStr + " " + snap.extra,
                barColor(type, snap.percent),
                BarStyle.SOLID
        );
        bossBar.setProgress(progress);
        bossBar.addPlayer(player);

        BarEntry entry = new BarEntry(bossBar);

        // Register BEFORE scheduling so the task can always see a valid entry
        activeEntries
                .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(type, entry);

        int durationSec = plugin.getConfigManager().getBarDisplaySeconds();

        if (FoliaUtil.isFolia()) {
            scheduleFolia(player.getUniqueId(), entry, type, labelStr, durationSec);
        } else {
            scheduleBukkit(player.getUniqueId(), entry, type, labelStr, durationSec);
        }
    }

    // ---- Bukkit scheduler path ---------------------------------------------

    private void scheduleBukkit(UUID uuid, BarEntry entry, String type,
                                String labelStr, int durationSec) {
        // Refresh every 2 ticks (~100 ms) async — never blocks the main thread
        BukkitTask refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Fast-exit if already cancelled — prevents race with auto-remove timer
            if (entry.cancelled.get()) return;

            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                removeEntry(uuid, type, entry);
                return;
            }
            refreshBar(entry.bar, type, labelStr);
        }, 1L, 2L); // 1-tick initial delay, 2-tick period

        entry.task = refreshTask;

        // Auto-remove after display duration (sync so it doesn't race with the async refresh)
        // durationSec <= 0 means persistent — never auto-remove
        if (durationSec > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Guard: only remove if this entry is still the active one for this player+type
                // Prevents accidentally removing a bar the player re-opened after this was queued
                Map<String, BarEntry> playerBars = activeEntries.get(uuid);
                if (playerBars != null && playerBars.get(type) == entry) {
                    removeEntry(uuid, type, entry);
                }
            }, durationSec * 20L);
        }
    }

    // ---- Folia scheduler path ----------------------------------------------

    private void scheduleFolia(UUID uuid, BarEntry entry, String type,
                               String labelStr, int durationSec) {
        final long periodMs = 100L; // 2 ticks * 50 ms/tick

        io.papermc.paper.threadedregions.scheduler.ScheduledTask refreshTask =
                plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, t -> {
                    if (entry.cancelled.get()) {
                        t.cancel();
                        return;
                    }
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) {
                        removeEntry(uuid, type, entry);
                        return;
                    }
                    refreshBar(entry.bar, type, labelStr);
                }, 50L, periodMs, TimeUnit.MILLISECONDS); // 50 ms initial delay

        entry.task = refreshTask;

        // durationSec <= 0 means persistent — never auto-remove
        if (durationSec > 0) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, t -> {
                Map<String, BarEntry> playerBars = activeEntries.get(uuid);
                if (playerBars != null && playerBars.get(type) == entry) {
                    removeEntry(uuid, type, entry);
                }
            }, durationSec * 1000L, TimeUnit.MILLISECONDS);
        }
    }

    // ---- Thread-safe entry removal -----------------------------------------

    /**
     * The single, authoritative path for removing a bar.
     * Uses {@code AtomicBoolean.compareAndSet} as a guard so that even if the
     * refresh task and the auto-remove timer fire simultaneously, only one of
     * them executes the body — no double-cancel, no NPE, no bar flickering back.
     */
    private void removeEntry(UUID uuid, String type, BarEntry entry) {
        if (!entry.cancelled.compareAndSet(false, true)) {
            return; // already being removed by another thread — do nothing
        }

        // 1. Cancel the refresh task FIRST, so no more setTitle/setProgress calls arrive
        Object task = entry.task;
        if (task instanceof BukkitTask bt) {
            try { bt.cancel(); } catch (Exception ignored) {}
        } else if (task instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask st) {
            try { st.cancel(); } catch (Exception ignored) {}
        }

        // 2. Remove the BossBar from all players (server GC can now reclaim it)
        entry.bar.removeAll();

        // 3. Clean up the tracking map (remove only if this exact entry is still registered)
        Map<String, BarEntry> playerBars = activeEntries.get(uuid);
        if (playerBars != null) {
            playerBars.remove(type, entry);
            if (playerBars.isEmpty()) {
                activeEntries.remove(uuid, playerBars);
            }
        }
    }

    /** Remove all active bars for one player (used by no-args group toggle). */
    private void removeAllBarsFor(UUID uuid) {
        Map<String, BarEntry> playerBars = activeEntries.get(uuid);
        if (playerBars == null) return;
        // Snapshot to avoid ConcurrentModificationException
        new HashMap<>(playerBars).forEach((type, entry) -> removeEntry(uuid, type, entry));
    }

    // ---- Snapshot builder --------------------------------------------------

    private record BarSnapshot(double percent, String extra) {}

    private BarSnapshot buildSnapshot(String type) {
        SystemStats sys = plugin.getSystemStats();
        return switch (type) {
            case "ram" -> {
                double pct = sys.getRAMPercent();
                yield new BarSnapshot(pct,
                        FormatUtil.formatPercent(pct) + " \u00a77("
                                + FormatUtil.formatBytes(sys.getUsedMemoryBytes())
                                + " / " + FormatUtil.formatBytes(sys.getMaxMemoryBytes()) + ")");
            }
            case "cpu" -> {
                double pct = sys.getCPUPercent();
                yield new BarSnapshot(pct, FormatUtil.formatPercent(pct));
            }
            case "disk" -> {
                double pct = sys.getDiskPercent();
                yield new BarSnapshot(pct,
                        FormatUtil.formatPercent(pct) + " \u00a77("
                                + FormatUtil.formatBytes(sys.getDiskUsedBytes())
                                + " / " + FormatUtil.formatBytes(sys.getDiskTotalBytes()) + ")");
            }
            case "mspt" -> {
                double mspt = sys.getMSPT();
                double pct  = sys.getMSPTPercent();
                yield new BarSnapshot(pct,
                        FormatUtil.msptColor(mspt) + FormatUtil.formatMSPT(mspt) + "\u00a7r \u00a77/ 50ms");
            }
            case "tps" -> {
                double[] tps = sys.getTPS();
                double   pct = Math.min(tps[0] / 20.0 * 100.0, 100.0);
                yield new BarSnapshot(pct,
                        FormatUtil.tpsColor(tps[0]) + FormatUtil.formatTPS(tps[0])
                                + " TPS\u00a7r \u00a77(5m: " + FormatUtil.formatTPS(tps[1])
                                + " | 15m: " + FormatUtil.formatTPS(tps[2])
                                + " | Players: " + Bukkit.getOnlinePlayers().size() + ")");
            }
            default -> new BarSnapshot(0, "");
        };
    }

    // ---- Refresh (called from async task) ----------------------------------

    /**
     * Pushes fresh metric data to an already-visible BossBar.
     * BossBar setTitle/setProgress/setColor are thread-safe on modern Paper/Folia.
     */
    private void refreshBar(BossBar bar, String type, String labelStr) {
        BarSnapshot snap     = buildSnapshot(type);
        double      progress = Math.max(0.001, Math.min(1.0, snap.percent / 100.0));
        bar.setTitle(labelStr + " " + snap.extra);
        bar.setProgress(progress);
        bar.setColor(barColor(type, snap.percent));
    }

    // ---- Cleanup (called by plugin on disable/reload) ----------------------

    /**
     * Cancels all active refresh tasks and removes all active BossBars from all players.
     * MUST be called from {@code WorldStatusPlugin#onDisable()} to avoid memory/task leaks
     * on server reload or plugin disable.
     */
    public void cleanup() {
        new HashMap<>(activeEntries).forEach((uuid, playerBars) ->
                new HashMap<>(playerBars).forEach((type, entry) ->
                        removeEntry(uuid, type, entry)));
        activeEntries.clear();
    }

    // ---- Color helper ------------------------------------------------------

    private static BarColor barColor(String type, double percent) {
        if ("tps".equals(type)) {
            if (percent >= 90) return BarColor.GREEN;
            if (percent >= 60) return BarColor.YELLOW;
            return BarColor.RED;
        }
        // RAM / CPU / Disk / MSPT: high usage = bad
        if (percent >= 80) return BarColor.RED;
        if (percent >= 50) return BarColor.YELLOW;
        return BarColor.GREEN;
    }

    // ---- Tab completion ----------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return TYPES.stream().filter(t -> t.startsWith(partial)).toList();
        }
        return List.of();
    }
}