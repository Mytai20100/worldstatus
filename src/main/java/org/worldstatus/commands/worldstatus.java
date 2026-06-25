package org.worldstatus.commands;

import org.worldstatus.WorldStatus;
import org.worldstatus.config.Config;
import org.worldstatus.lang.Lang;
import org.worldstatus.util.Format;
import org.worldstatus.util.System;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class worldstatus implements CommandExecutor, TabCompleter {

    private final WorldStatus plugin;

    public worldstatus(WorldStatus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("worldstatus.reload")) {
                sender.sendMessage(plugin.getLangManager().get("messages.no-permission"));
                return true;
            }
            plugin.reload();
            sender.sendMessage(plugin.getLangManager().get("messages.reloaded"));
            return true;
        }

        if (!sender.hasPermission("worldstatus.status")) {
            sender.sendMessage(plugin.getLangManager().get("messages.no-permission"));
            return true;
        }

        Config cfg  = plugin.getConfigManager();
        Lang   lang = plugin.getLangManager();
        System sys  = plugin.getSystemStats();

        List<String> lines = new ArrayList<>();
        lines.add(lang.get("world-status.header"));

        if (cfg.showDiskPercent()) {
            double diskPct = sys.getDiskPercent();
            lines.add(lang.get("world-status.disk", Map.of(
                    "color", Format.percentColor(diskPct),
                    "pct",   Format.formatPercent(diskPct),
                    "used",  Format.formatBytes(sys.getDiskUsedBytes()),
                    "total", Format.formatBytes(sys.getDiskTotalBytes())
            )));
        }

        if (cfg.showWorldSizes()) {
            lines.add(lang.get("world-status.worlds-total", "size", Format.formatBytes(sys.getAllWorldsSize())));
            for (World w : Bukkit.getWorlds()) {
                String envIcon = switch (w.getEnvironment()) {
                    case NETHER  -> lang.get("world-status.env-nether");
                    case THE_END -> lang.get("world-status.env-end");
                    default      -> lang.get("world-status.env-overworld");
                };
                lines.add(lang.get("world-status.world-entry", Map.of(
                        "icon", envIcon,
                        "name", w.getName(),
                        "size", Format.formatBytes(sys.getWorldSize(w))
                )));
            }
        }

        if (cfg.showTPS()) {
            double[] tps = sys.getTPS();
            lines.add(lang.get("world-status.tps", Map.of(
                    "color", Format.tpsColor(tps[0]),
                    "tps1",  Format.formatTPS(tps[0]),
                    "tps5",  Format.formatTPS(tps[1]),
                    "tps15", Format.formatTPS(tps[2])
            )));
        }

        if (cfg.showMSPT()) {
            double mspt = sys.getMSPT();
            lines.add(lang.get("world-status.mspt", Map.of(
                    "color", Format.msptColor(mspt),
                    "mspt",  Format.formatMSPT(mspt)
            )));
        }

        if (cfg.showRAM()) {
            double ramPct = sys.getRAMPercent();
            lines.add(lang.get("world-status.ram", Map.of(
                    "color", Format.percentColor(ramPct),
                    "pct",   Format.formatPercent(ramPct),
                    "used",  Format.formatBytes(sys.getUsedMemoryBytes()),
                    "max",   Format.formatBytes(sys.getMaxMemoryBytes())
            )));
        }

        if (cfg.showCPU()) {
            double cpuPct = sys.getCPUPercent();
            lines.add(lang.get("world-status.cpu", Map.of(
                    "color", Format.percentColor(cpuPct),
                    "pct",   Format.formatPercent(cpuPct)
            )));
        }

        if (cfg.showPlayers()) {
            lines.add(lang.get("world-status.players", Map.of(
                    "online", java.lang.String.valueOf(Bukkit.getOnlinePlayers().size()),
                    "max",    java.lang.String.valueOf(Bukkit.getMaxPlayers())
            )));
        }

        lines.add(lang.get("world-status.footer"));
        lines.forEach(sender::sendMessage);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("worldstatus.reload")) {
            return List.of("reload");
        }
        return List.of();
    }
}
