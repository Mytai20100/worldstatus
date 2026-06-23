package org.worldstatus.commands;

import org.worldstatus.WorldStatusPlugin;
import org.worldstatus.config.ConfigManager;
import org.worldstatus.lang.LangManager;
import org.worldstatus.util.FormatUtil;
import org.worldstatus.util.SystemStats;
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

public class WorldStatusCommand implements CommandExecutor, TabCompleter {

    private final WorldStatusPlugin plugin;

    public WorldStatusCommand(WorldStatusPlugin plugin) {
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

        ConfigManager cfg  = plugin.getConfigManager();
        LangManager   lang = plugin.getLangManager();
        SystemStats   sys  = plugin.getSystemStats();

        List<String> lines = new ArrayList<>();

        lines.add(lang.get("world-status.header"));

        if (cfg.showDiskPercent()) {
            double diskPct = sys.getDiskPercent();
            lines.add(lang.get("world-status.disk", Map.of(
                    "color", FormatUtil.percentColor(diskPct),
                    "pct",   FormatUtil.formatPercent(diskPct),
                    "used",  FormatUtil.formatBytes(sys.getDiskUsedBytes()),
                    "total", FormatUtil.formatBytes(sys.getDiskTotalBytes())
            )));
        }

        if (cfg.showWorldSizes()) {
            lines.add(lang.get("world-status.worlds-total", "size", FormatUtil.formatBytes(sys.getAllWorldsSize())));
            for (World w : Bukkit.getWorlds()) {
                String envIcon = switch (w.getEnvironment()) {
                    case NETHER  -> lang.get("world-status.env-nether");
                    case THE_END -> lang.get("world-status.env-end");
                    default      -> lang.get("world-status.env-overworld");
                };
                lines.add(lang.get("world-status.world-entry", Map.of(
                        "icon", envIcon,
                        "name", w.getName(),
                        "size", FormatUtil.formatBytes(sys.getWorldSize(w))
                )));
            }
        }

        if (cfg.showTPS()) {
            double[] tps = sys.getTPS();
            lines.add(lang.get("world-status.tps", Map.of(
                    "color", FormatUtil.tpsColor(tps[0]),
                    "tps1",  FormatUtil.formatTPS(tps[0]),
                    "tps5",  FormatUtil.formatTPS(tps[1]),
                    "tps15", FormatUtil.formatTPS(tps[2])
            )));
        }

        if (cfg.showMSPT()) {
            double mspt = sys.getMSPT();
            lines.add(lang.get("world-status.mspt", Map.of(
                    "color", FormatUtil.msptColor(mspt),
                    "mspt",  FormatUtil.formatMSPT(mspt)
            )));
        }

        if (cfg.showRAM()) {
            double ramPct = sys.getRAMPercent();
            lines.add(lang.get("world-status.ram", Map.of(
                    "color", FormatUtil.percentColor(ramPct),
                    "pct",   FormatUtil.formatPercent(ramPct),
                    "used",  FormatUtil.formatBytes(sys.getUsedMemoryBytes()),
                    "max",   FormatUtil.formatBytes(sys.getMaxMemoryBytes())
            )));
        }

        if (cfg.showCPU()) {
            double cpuPct = sys.getCPUPercent();
            lines.add(lang.get("world-status.cpu", Map.of(
                    "color", FormatUtil.percentColor(cpuPct),
                    "pct",   FormatUtil.formatPercent(cpuPct)
            )));
        }

        if (cfg.showPlayers()) {
            lines.add(lang.get("world-status.players", Map.of(
                    "online", String.valueOf(Bukkit.getOnlinePlayers().size()),
                    "max",    String.valueOf(Bukkit.getMaxPlayers())
            )));
        }

        lines.add(lang.get("world-status.footer"));

        for (String line : lines) {
            sender.sendMessage(line);
        }

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
