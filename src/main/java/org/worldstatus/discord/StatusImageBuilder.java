package org.worldstatus.discord;

import org.worldstatus.WorldStatusPlugin;
import org.worldstatus.util.FormatUtil;
import org.worldstatus.util.SystemStats;
import org.bukkit.World;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class StatusImageBuilder {

    private static final Color BG        = new Color(0x1E, 0x20, 0x28);
    private static final Color BG_CARD   = new Color(0x25, 0x27, 0x35);
    private static final Color ACCENT    = new Color(0x5B, 0x8D, 0xFF);
    private static final Color TEXT_MAIN = new Color(0xE4, 0xE6, 0xF0);
    private static final Color TEXT_MUTED= new Color(0x88, 0x8A, 0x9D);
    private static final Color COL_GREEN = new Color(0x4C, 0xAF, 0x50);
    private static final Color COL_YELLOW= new Color(0xFF, 0xC1, 0x07);
    private static final Color COL_RED   = new Color(0xF4, 0x43, 0x36);

    private static final int WIDTH    = 680;
    private static final int PADDING  = 24;
    private static final int ROW_H    = 38;
    private static final int HEADER_H = 70;
    private static final int FOOTER_H = 40;
    private static final int ARC      = 16;
    private static final int BAR_W    = 200;
    private static final int BAR_H    = 14;

    private record Row(String label, String value, String extra,
                       double barPercent, boolean invertedBar, boolean hasBar) {}

    /** Thread-safe snapshot of main-thread Bukkit data for image rendering. */
    public record BukkitSnapshot(List<WorldEntry> worlds, int onlinePlayers, int maxPlayers,
                                  String serverName, int chunksLoaded, int entitiesTotal, long mobsTotal) {
        public record WorldEntry(String name, String environment, long sizeBytes) {}
    }

    private final WorldStatusPlugin plugin;

    public StatusImageBuilder(WorldStatusPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Must be called on the Bukkit main thread to capture a safe snapshot.
     */
    public static BukkitSnapshot snapshotOnMainThread(WorldStatusPlugin plugin) {
        SystemStats sys = plugin.getSystemStats();
        List<BukkitSnapshot.WorldEntry> worlds = new ArrayList<>();
        for (World w : org.bukkit.Bukkit.getWorlds()) {
            worlds.add(new BukkitSnapshot.WorldEntry(
                    w.getName(),
                    w.getEnvironment().name(),
                    sys.getWorldSize(w)
            ));
        }
        int online = org.bukkit.Bukkit.getOnlinePlayers().size();
        int max    = org.bukkit.Bukkit.getMaxPlayers();
        String serverName = org.bukkit.Bukkit.getServer().getName();
        
        int chunksLoaded = org.bukkit.Bukkit.getWorlds().stream()
                .mapToInt(w -> w.getLoadedChunks().length)
                .sum();
        int entitiesTotal = org.bukkit.Bukkit.getWorlds().stream()
                .mapToInt(w -> w.getEntities().size())
                .sum();
        long mobsTotal = org.bukkit.Bukkit.getWorlds().stream()
                .flatMap(w -> w.getEntities().stream())
                .filter(e -> e.getType() != org.bukkit.entity.EntityType.PLAYER 
                        && e.getType() != org.bukkit.entity.EntityType.ARMOR_STAND)
                .count();
        
        return new BukkitSnapshot(worlds, online, max, serverName, chunksLoaded, entitiesTotal, mobsTotal);
    }

    /** Build the status image using a pre-captured main-thread snapshot. */
    public BufferedImage build(BukkitSnapshot snapshot) {
        SystemStats sys  = plugin.getSystemStats();
        List<Row>   rows = collectRows(sys, snapshot);
        int height = HEADER_H + rows.size() * ROW_H + PADDING + FOOTER_H;

        BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D    g   = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        g.setColor(BG);
        g.fillRoundRect(0, 0, WIDTH, height, ARC, ARC);

        drawHeader(g, height);

        int y = HEADER_H + 4;
        for (int i = 0; i < rows.size(); i++) {
            drawRow(g, rows.get(i), y, i % 2 == 0);
            y += ROW_H;
        }

        drawFooter(g, height, snapshot.serverName());
        g.dispose();
        return img;
    }

    private List<Row> collectRows(SystemStats sys, BukkitSnapshot snapshot) {
        List<Row> rows = new ArrayList<>();

        double diskPct = sys.getDiskPercent();
        rows.add(new Row(
                "Disk",
                FormatUtil.formatPercent(diskPct),
                FormatUtil.formatBytes(sys.getDiskUsedBytes()) + " / " + FormatUtil.formatBytes(sys.getDiskTotalBytes()),
                diskPct, false, true));

        rows.add(new Row(
                "Worlds (total)",
                FormatUtil.formatBytes(sys.getAllWorldsSize()),
                "", 0, false, false));

        for (BukkitSnapshot.WorldEntry w : snapshot.worlds()) {
            String prefix = switch (w.environment()) {
                case "NETHER"  -> "  > [Nether] ";
                case "THE_END" -> "  > [End] ";
                default        -> "  > [Overworld] ";
            };
            rows.add(new Row(
                    prefix + w.name(),
                    FormatUtil.formatBytes(w.sizeBytes()),
                    "", 0, false, false));
        }

        double[] tps = sys.getTPS();
        rows.add(new Row(
                "TPS",
                FormatUtil.formatTPS(tps[0]),
                "5m: " + FormatUtil.formatTPS(tps[1]) + "  15m: " + FormatUtil.formatTPS(tps[2]),
                tps[0] / 20.0 * 100.0, true, true));

        double mspt = sys.getMSPT();
        rows.add(new Row(
                "MSPT",
                FormatUtil.formatMSPT(mspt),
                "/ 50ms budget",
                sys.getMSPTPercent(), false, true));

        double ramPct = sys.getRAMPercent();
        rows.add(new Row(
                "RAM",
                FormatUtil.formatPercent(ramPct),
                FormatUtil.formatBytes(sys.getUsedMemoryBytes()) + " / " + FormatUtil.formatBytes(sys.getMaxMemoryBytes()),
                ramPct, false, true));

        double cpuPct = sys.getCPUPercent();
        rows.add(new Row(
                "CPU",
                FormatUtil.formatPercent(cpuPct),
                "",
                cpuPct, false, true));

        int online = snapshot.onlinePlayers();
        int max    = snapshot.maxPlayers();
        rows.add(new Row(
                "Players",
                online + " / " + max,
                "",
                (double) online / Math.max(max, 1) * 100.0, false, true));

        return rows;
    }

    private void drawHeader(Graphics2D g, int totalHeight) {
        g.setColor(ACCENT);
        g.fillRoundRect(0, 0, WIDTH, HEADER_H, ARC, ARC);
        g.setColor(BG_CARD);
        g.fillRect(0, HEADER_H - ARC, WIDTH, ARC);

        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString("WorldStatus", PADDING, (HEADER_H + fm.getAscent() - fm.getDescent()) / 2);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(0xCC, 0xCC, 0xFF, 180));
        FontMetrics fm2 = g.getFontMetrics();
        g.drawString(ts, WIDTH - PADDING - fm2.stringWidth(ts), (HEADER_H + fm2.getAscent() - fm2.getDescent()) / 2);
    }

    private void drawRow(Graphics2D g, Row row, int y, boolean alternate) {
        if (alternate) {
            g.setColor(new Color(0xFF, 0xFF, 0xFF, 8));
            g.fillRect(PADDING / 2, y, WIDTH - PADDING, ROW_H);
        }

        int midY = y + ROW_H / 2;

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(TEXT_MUTED);
        g.drawString(row.label(), PADDING, midY + 5);

        if (row.hasBar()) {
            drawProgressBar(g, 260, midY - BAR_H / 2, BAR_W, BAR_H, row.barPercent(), row.invertedBar());
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(percentToColor(row.barPercent(), row.invertedBar(), row.hasBar()));
        int valX = row.hasBar() ? 260 + BAR_W + 12 : 380;
        g.drawString(row.value(), valX, midY + 5);

        if (!row.extra().isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(TEXT_MUTED);
            FontMetrics fm = g.getFontMetrics(new Font("SansSerif", Font.BOLD, 14));
            g.drawString(row.extra(), valX + fm.stringWidth(row.value()) + 10, midY + 5);
        }

        g.setColor(new Color(0xFF, 0xFF, 0xFF, 15));
        g.drawLine(PADDING, y + ROW_H - 1, WIDTH - PADDING, y + ROW_H - 1);
    }

    private void drawProgressBar(Graphics2D g, int x, int y, int w, int h,
                                  double percent, boolean inverted) {
        percent = Math.max(0, Math.min(100, percent));
        int filled = (int) (percent / 100.0 * w);

        g.setColor(new Color(0x44, 0x44, 0x55));
        g.fillRoundRect(x, y, w, h, h, h);

        g.setColor(percentToColor(percent, inverted, true));
        if (filled > 0) g.fillRoundRect(x, y, filled, h, h, h);
    }

    private void drawFooter(Graphics2D g, int totalHeight, String serverName) {
        int y = totalHeight - FOOTER_H;
        g.setColor(new Color(0xFF, 0xFF, 0xFF, 15));
        g.drawLine(PADDING, y, WIDTH - PADDING, y);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(TEXT_MUTED);
        g.drawString("WorldStatus  |  " + serverName, PADDING, y + (FOOTER_H + 12) / 2);
    }

    private Color percentToColor(double pct, boolean inverted, boolean hasBar) {
        if (!hasBar) return TEXT_MAIN;
        if (inverted) {
            if (pct >= 90) return COL_GREEN;
            if (pct >= 75) return COL_YELLOW;
            return COL_RED;
        }
        if (pct >= 75) return COL_RED;
        if (pct >= 50) return COL_YELLOW;
        return COL_GREEN;
    }
}
