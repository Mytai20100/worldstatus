package org.worldstatus.commands;

import org.worldstatus.WorldStatus;
import org.worldstatus.config.Config;
import org.worldstatus.lang.Lang;
import org.worldstatus.util.Folia;
import org.worldstatus.util.Format;
import org.worldstatus.util.System;
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

public class worldbar implements CommandExecutor, TabCompleter {

    private static final List<String> TYPES = List.of("ram", "cpu", "disk", "mspt", "tps");

    private final WorldStatus plugin;

    private final Map<UUID, Map<String, BarEntry>> activeEntries = new ConcurrentHashMap<>();

    private static final class BarEntry {
        final BossBar       bar;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile Object     task;

        BarEntry(BossBar bar) { this.bar = bar; }
    }

    public worldbar(WorldStatus plugin) {
        this.plugin = plugin;
    }

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
            if (isPlayer) {
                UUID uuid = ((Player) sender).getUniqueId();
                Map<String, BarEntry> playerBars = activeEntries.get(uuid);
                boolean anyActive = playerBars != null && !playerBars.isEmpty();
                if (anyActive) {
                    removeAllBarsFor(uuid);
                } else {
                    for (String type : TYPES) showBossBar((Player) sender, type);
                }
            } else {
                for (String type : TYPES) printConsoleLine(sender, type);
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

        Map<String, BarEntry> playerBars = activeEntries.get(uuid);
        if (playerBars != null) {
            BarEntry existing = playerBars.get(type);
            if (existing != null) {
                removeEntry(uuid, type, existing);
                return true;
            }
        }

        showBossBar(player, type);
        return true;
    }

    // ---- Console output ---------------------------------------------------

    private void printConsoleLine(CommandSender sender, String type) {
        Config      cfg    = plugin.getConfigManager();
        System      sys    = plugin.getSystemStats();
        Lang        lang   = plugin.getLangManager();
        String      filled = cfg.getBarCharFilled();
        String      empty  = cfg.getBarCharEmpty();
        int         len    = cfg.getBarLength();
        boolean     colors = cfg.isBarColorEnabled();

        String labelStr = lang.get("worldbar.label." + type);
        String bar, extra;

        switch (type) {
            case "ram" -> {
                double pct = sys.getRAMPercent();
                bar   = Format.buildBar(pct, len, filled, empty, colors);
                extra = Format.formatPercent(pct) + " §7("
                        + Format.formatBytes(sys.getUsedMemoryBytes())
                        + " / " + Format.formatBytes(sys.getMaxMemoryBytes()) + ")";
            }
            case "cpu" -> {
                double pct = sys.getCPUPercent();
                bar   = Format.buildBar(pct, len, filled, empty, colors);
                extra = Format.formatPercent(pct);
            }
            case "disk" -> {
                double pct = sys.getDiskPercent();
                bar   = Format.buildBar(pct, len, filled, empty, colors);
                extra = Format.formatPercent(pct) + " §7("
                        + Format.formatBytes(sys.getDiskUsedBytes())
                        + " / " + Format.formatBytes(sys.getDiskTotalBytes()) + ")";
            }
            case "mspt" -> {
                double mspt = sys.getMSPT();
                double pct  = sys.getMSPTPercent();
                bar   = Format.buildBar(pct, len, filled, empty, colors);
                extra = Format.msptColor(mspt) + Format.formatMSPT(mspt) + "§r §7/ 50ms";
            }
            case "tps" -> {
                double[] tps = sys.getTPS();
                bar   = Format.buildTPSBar(tps[0], len, filled, empty, colors);
                extra = Format.tpsColor(tps[0]) + Format.formatTPS(tps[0])
                        + " TPS§r §7(5m: " + Format.formatTPS(tps[1])
                        + " | 15m: " + Format.formatTPS(tps[2])
                        + " | Players: " + Bukkit.getOnlinePlayers().size() + ")";
            }
            default -> { return; }
        }

        sender.sendMessage(lang.get("worldbar.line", Map.of(
                "label", labelStr, "bar", bar, "extra", extra)));
    }

    // ---- BossBar creation -------------------------------------------------

    private void showBossBar(Player player, String type) {
        String labelStr = plugin.getLangManager().get("worldbar.label." + type);
        BarSnapshot snap = buildSnapshot(type);
        double progress  = Math.max(0.001, Math.min(1.0, snap.percent() / 100.0));

        BossBar bossBar = Bukkit.createBossBar(
                labelStr + " " + snap.extra(),
                barColor(type, snap.percent()),
                BarStyle.SOLID);
        bossBar.setProgress(progress);
        bossBar.addPlayer(player);

        BarEntry entry = new BarEntry(bossBar);
        activeEntries.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(type, entry);

        int durationSec = plugin.getConfigManager().getBarDisplaySeconds();

        if (Folia.isFolia()) {
            scheduleFolia(player.getUniqueId(), entry, type, labelStr, durationSec);
        } else {
            scheduleBukkit(player.getUniqueId(), entry, type, labelStr, durationSec);
        }
    }

    // ---- Bukkit scheduler -------------------------------------------------

    private void scheduleBukkit(UUID uuid, BarEntry entry, String type, String labelStr, int durationSec) {
        BukkitTask refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (entry.cancelled.get()) return;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) { removeEntry(uuid, type, entry); return; }
            refreshBar(entry.bar, type, labelStr);
        }, 1L, 2L);

        entry.task = refreshTask;

        if (durationSec > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Map<String, BarEntry> playerBars = activeEntries.get(uuid);
                if (playerBars != null && playerBars.get(type) == entry) removeEntry(uuid, type, entry);
            }, durationSec * 20L);
        }
    }

    // ---- Folia scheduler --------------------------------------------------

    private void scheduleFolia(UUID uuid, BarEntry entry, String type, String labelStr, int durationSec) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask refreshTask =
                plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, t -> {
                    if (entry.cancelled.get()) { t.cancel(); return; }
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) { removeEntry(uuid, type, entry); return; }
                    refreshBar(entry.bar, type, labelStr);
                }, 50L, 100L, TimeUnit.MILLISECONDS);

        entry.task = refreshTask;

        if (durationSec > 0) {
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, t -> {
                Map<String, BarEntry> playerBars = activeEntries.get(uuid);
                if (playerBars != null && playerBars.get(type) == entry) removeEntry(uuid, type, entry);
            }, durationSec * 1000L, TimeUnit.MILLISECONDS);
        }
    }

    // ---- Entry removal ----------------------------------------------------

    private void removeEntry(UUID uuid, String type, BarEntry entry) {
        if (!entry.cancelled.compareAndSet(false, true)) return;

        Object task = entry.task;
        if (task instanceof BukkitTask bt) {
            try { bt.cancel(); } catch (Exception ignored) {}
        } else if (task instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask st) {
            try { st.cancel(); } catch (Exception ignored) {}
        }

        entry.bar.removeAll();

        Map<String, BarEntry> playerBars = activeEntries.get(uuid);
        if (playerBars != null) {
            playerBars.remove(type, entry);
            if (playerBars.isEmpty()) activeEntries.remove(uuid, playerBars);
        }
    }

    private void removeAllBarsFor(UUID uuid) {
        Map<String, BarEntry> playerBars = activeEntries.get(uuid);
        if (playerBars == null) return;
        new HashMap<>(playerBars).forEach((type, entry) -> removeEntry(uuid, type, entry));
    }

    // ---- Snapshot builder -------------------------------------------------

    private record BarSnapshot(double percent, String extra) {}

    private BarSnapshot buildSnapshot(String type) {
        System sys = plugin.getSystemStats();
        return switch (type) {
            case "ram" -> {
                double pct = sys.getRAMPercent();
                yield new BarSnapshot(pct, Format.formatPercent(pct) + " §7("
                        + Format.formatBytes(sys.getUsedMemoryBytes())
                        + " / " + Format.formatBytes(sys.getMaxMemoryBytes()) + ")");
            }
            case "cpu" -> {
                double pct = sys.getCPUPercent();
                yield new BarSnapshot(pct, Format.formatPercent(pct));
            }
            case "disk" -> {
                double pct = sys.getDiskPercent();
                yield new BarSnapshot(pct, Format.formatPercent(pct) + " §7("
                        + Format.formatBytes(sys.getDiskUsedBytes())
                        + " / " + Format.formatBytes(sys.getDiskTotalBytes()) + ")");
            }
            case "mspt" -> {
                double mspt = sys.getMSPT();
                yield new BarSnapshot(sys.getMSPTPercent(),
                        Format.msptColor(mspt) + Format.formatMSPT(mspt) + "§r §7/ 50ms");
            }
            case "tps" -> {
                double[] tps = sys.getTPS();
                double   pct = Math.min(tps[0] / 20.0 * 100.0, 100.0);
                yield new BarSnapshot(pct, Format.tpsColor(tps[0]) + Format.formatTPS(tps[0])
                        + " TPS§r §7(5m: " + Format.formatTPS(tps[1])
                        + " | 15m: " + Format.formatTPS(tps[2])
                        + " | Players: " + Bukkit.getOnlinePlayers().size() + ")");
            }
            default -> new BarSnapshot(0, "");
        };
    }

    // ---- Refresh ----------------------------------------------------------

    private void refreshBar(BossBar bar, String type, String labelStr) {
        BarSnapshot snap     = buildSnapshot(type);
        double      progress = Math.max(0.001, Math.min(1.0, snap.percent() / 100.0));
        bar.setTitle(labelStr + " " + snap.extra());
        bar.setProgress(progress);
        bar.setColor(barColor(type, snap.percent()));
    }

    // ---- Cleanup ----------------------------------------------------------

    public void cleanup() {
        new HashMap<>(activeEntries).forEach((uuid, playerBars) ->
                new HashMap<>(playerBars).forEach((type, entry) -> removeEntry(uuid, type, entry)));
        activeEntries.clear();
    }

    // ---- Color helper -----------------------------------------------------

    private static BarColor barColor(String type, double percent) {
        if ("tps".equals(type)) {
            if (percent >= 90) return BarColor.GREEN;
            if (percent >= 60) return BarColor.YELLOW;
            return BarColor.RED;
        }
        if (percent >= 80) return BarColor.RED;
        if (percent >= 50) return BarColor.YELLOW;
        return BarColor.GREEN;
    }

    // ---- Tab completion ---------------------------------------------------

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
