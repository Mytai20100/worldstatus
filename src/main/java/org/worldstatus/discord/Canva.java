package org.worldstatus.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.worldstatus.WorldStatus;
import org.worldstatus.util.Format;
import org.worldstatus.util.System;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Canva {

    private final WorldStatus plugin;
    private final Gson gson = new Gson();

    public Canva(WorldStatus plugin) {
        this.plugin = plugin;
    }

    public BufferedImage renderPlayerList(List<Player> players) {
        try {
            File configFile = new File(plugin.getDataFolder(), "playerlist.json");
            if (!configFile.exists()) {
                configFile = new File(plugin.getDataFolder().getParentFile(), "WorldStatus/playerlist.json");
            }
            if (!configFile.exists()) return null;

            JsonObject config = gson.fromJson(new FileReader(configFile), JsonObject.class);
            if (!config.get("enabled").getAsBoolean()) return null;

            JsonObject canvas      = config.getAsJsonObject("canvas");
            int width         = canvas.get("width").getAsInt();
            boolean dynHeight = canvas.get("dynamicHeight").getAsBoolean();
            int minHeight     = canvas.get("minHeight").getAsInt();
            int padding       = canvas.get("padding").getAsInt();

            JsonObject playerListConfig = config.getAsJsonObject("playerList");
            int rowHeight   = playerListConfig.get("rowHeight").getAsInt();
            int headerHeight = 40;

            int calcHeight = minHeight;
            if (dynHeight) {
                int contentHeight = 200 + headerHeight + (players.size() * rowHeight) + 100;
                calcHeight = Math.max(minHeight, contentHeight);
            }

            BufferedImage img = new BufferedImage(width, calcHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D    g   = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            drawBackground(g, config, width, calcHeight);
            drawLogo(g, config);
            drawTitle(g, config);
            drawSubtitle(g, config, players.size(), Bukkit.getMaxPlayers());
            drawPlayerListTable(g, config, players);
            drawFooter(g, config, calcHeight);

            g.dispose();
            return img;
        } catch (Exception e) {
            plugin.getLogger().warning("[WorldStatus] Failed to render Canva player list: " + e.getMessage());
            return null;
        }
    }

    public BufferedImage renderWorldStatus(Image.BukkitSnapshot snapshot) {
        try {
            File configFile = new File(plugin.getDataFolder(), "worldstatus.json");
            if (!configFile.exists()) {
                configFile = new File(plugin.getDataFolder().getParentFile(), "WorldStatus/worldstatus.json");
            }
            if (!configFile.exists()) return null;

            JsonObject config = gson.fromJson(new FileReader(configFile), JsonObject.class);
            if (!config.get("enabled").getAsBoolean()) return null;

            JsonObject canvas  = config.getAsJsonObject("canvas");
            int width          = canvas.get("width").getAsInt();
            boolean dynHeight  = canvas.get("dynamicHeight").getAsBoolean();
            int minHeight      = canvas.get("minHeight").getAsInt();

            int calcHeight = minHeight;
            if (dynHeight) {
                int worldCount    = snapshot.worlds().size();
                int contentHeight = 200 + (8 * 50) + (worldCount * 30) + 100;
                calcHeight = Math.max(minHeight, contentHeight);
            }

            BufferedImage img = new BufferedImage(width, calcHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D    g   = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            drawBackground(g, config, width, calcHeight);
            drawLogo(g, config);
            drawStatusTitle(g, config);
            drawStatusSubtitle(g, config);
            drawStats(g, config, snapshot);
            drawWorlds(g, config, snapshot);
            drawFooter(g, config, calcHeight);

            g.dispose();
            return img;
        } catch (Exception e) {
            plugin.getLogger().warning("[WorldStatus] Failed to render Canva world status: " + e.getMessage());
            return null;
        }
    }

    // ---- Background -------------------------------------------------------

    private void drawBackground(Graphics2D g, JsonObject config, int width, int height) {
        JsonObject bg   = config.getAsJsonObject("background");
        String     type = bg.get("type").getAsString();

        if ("image".equals(type)) {
            BufferedImage bgImg = null;
            if (bg.has("url"))  bgImg = loadImageFromURL(bg.get("url").getAsString());
            if (bgImg == null && bg.has("path")) {
                File imgFile = new File(plugin.getDataFolder(), bg.get("path").getAsString());
                if (imgFile.exists()) {
                    try { bgImg = ImageIO.read(imgFile); } catch (Exception ignored) {}
                }
            }
            if (bgImg != null) { g.drawImage(bgImg, 0, 0, width, height, null); return; }
        }

        g.setColor(Color.decode(bg.get("color").getAsString()));
        g.fillRect(0, 0, width, height);
    }

    // ---- Logo -------------------------------------------------------------

    private void drawLogo(Graphics2D g, JsonObject config) {
        if (!config.has("logo")) return;
        JsonObject logo = config.getAsJsonObject("logo");
        if (!logo.get("enabled").getAsBoolean()) return;

        BufferedImage logoImg = null;
        if (logo.has("url"))  logoImg = loadImageFromURL(logo.get("url").getAsString());
        if (logoImg == null && logo.has("path")) {
            File f = new File(plugin.getDataFolder(), logo.get("path").getAsString());
            if (f.exists()) {
                try { logoImg = ImageIO.read(f); } catch (Exception ignored) {}
            }
        }
        if (logoImg != null) {
            g.drawImage(logoImg, logo.get("x").getAsInt(), logo.get("y").getAsInt(),
                    logo.get("width").getAsInt(), logo.get("height").getAsInt(), null);
        }
    }

    private BufferedImage loadImageFromURL(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "WorldStatus-Plugin");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            try (InputStream in = conn.getInputStream()) { return ImageIO.read(in); }
        } catch (Exception e) {
            plugin.getLogger().warning("[WorldStatus] Failed to load image: " + urlString);
            return null;
        }
    }

    // ---- Title / Subtitle -------------------------------------------------

    private void drawTitle(Graphics2D g, JsonObject config) {
        JsonObject title = config.getAsJsonObject("title");
        drawTextElement(g, title, title.get("text").getAsString());
        if (title.get("blur").getAsInt() > 0) applyBlur(g, title.get("blur").getAsInt());
    }

    private void drawSubtitle(Graphics2D g, JsonObject config, int online, int max) {
        JsonObject sub = config.getAsJsonObject("subtitle");
        String text = sub.get("text").getAsString()
                .replace("{online}", java.lang.String.valueOf(online))
                .replace("{max}", java.lang.String.valueOf(max));
        drawTextElement(g, sub, text);
    }

    private void drawStatusTitle(Graphics2D g, JsonObject config) {
        JsonObject title = config.getAsJsonObject("title");
        drawTextElement(g, title, title.get("text").getAsString());
    }

    private void drawStatusSubtitle(Graphics2D g, JsonObject config) {
        JsonObject sub = config.getAsJsonObject("subtitle");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        drawTextElement(g, sub, sub.get("text").getAsString().replace("{timestamp}", timestamp));
    }

    // ---- Player list table ------------------------------------------------

    private void drawPlayerListTable(Graphics2D g, JsonObject config, List<Player> players) {
        JsonObject plCfg = config.getAsJsonObject("playerList");
        int startX = plCfg.get("startX").getAsInt();
        int startY = plCfg.get("startY").getAsInt();
        int rowH   = plCfg.get("rowHeight").getAsInt();
        String headerColor  = plCfg.get("headerColor").getAsString();
        String rowColor     = plCfg.get("rowColor").getAsString();
        String altRowColor  = plCfg.get("alternateRowColor").getAsString();
        String layout       = plCfg.has("layout") ? plCfg.get("layout").getAsString() : "vertical";
        int columnWidth     = plCfg.has("columnWidth") ? plCfg.get("columnWidth").getAsInt() : 200;
        JsonArray columns   = plCfg.getAsJsonArray("columns");

        if ("horizontal".equals(layout)) {
            drawPlayerListHorizontal(g, players, startX, startY, columnWidth, rowH, columns, rowColor, altRowColor);
        } else {
            drawPlayerListVertical(g, players, startX, startY, rowH, headerColor, rowColor, altRowColor, columns);
        }
    }

    private void drawPlayerListVertical(Graphics2D g, List<Player> players, int startX, int startY,
            int rowH, String headerColor, String rowColor, String altRowColor, JsonArray columns) {

        g.setColor(Color.decode(headerColor));
        g.setFont(new Font("Arial", Font.BOLD, 16));
        for (int i = 0; i < columns.size(); i++) {
            JsonObject col = columns.get(i).getAsJsonObject();
            if (col.has("header") && !col.get("header").getAsString().isEmpty()) {
                g.drawString(col.get("header").getAsString(), startX + col.get("x").getAsInt(), startY);
            }
        }

        int curY = startY + 30;
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            g.setColor(Color.decode(i % 2 == 0 ? rowColor : altRowColor));
            g.fillRect(startX - 10, curY - 20, 600, rowH);

            for (int j = 0; j < columns.size(); j++) {
                JsonObject col   = columns.get(j).getAsJsonObject();
                String     type  = col.has("type") ? col.get("type").getAsString() : "text";
                int        colX  = startX + col.get("x").getAsInt();

                if ("avatar".equals(type)) {
                    BufferedImage av = loadImageFromURL(
                            "https://crafatar.com/avatars/" + p.getUniqueId() + "?overlay=true&size=64");
                    if (av != null)
                        g.drawImage(av, colX, curY - col.get("height").getAsInt() + 5,
                                col.get("width").getAsInt(), col.get("height").getAsInt(), null);
                } else if ("ping".equals(type)) {
                    if ("icon".equals(col.has("mode") ? col.get("mode").getAsString() : "text")) {
                        drawPingIcon(g, colX, curY - 10, p.getPing());
                    } else {
                        drawTextElement(g, col, p.getPing() + "ms", colX, curY);
                    }
                } else {
                    String serverIp  = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
                    String playerIp  = "";
                    try {
                        InetSocketAddress addr = p.getAddress();
                        if (addr != null) playerIp = addr.getAddress().getHostAddress();
                    } catch (Exception ignored) {}
                    String value = col.get("value").getAsString()
                            .replace("{player_name}", p.getName())
                            .replace("{uuid}",        p.getUniqueId().toString())
                            .replace("{ping}",        java.lang.String.valueOf(p.getPing()))
                            .replace("{player_ip}",   playerIp)
                            .replace("{world}",       p.getWorld().getName())
                            .replace("{server_ip}",   serverIp);
                    drawTextElement(g, col, value, colX, curY);
                }
            }
            curY += rowH;
        }
    }

    private void drawPlayerListHorizontal(Graphics2D g, List<Player> players, int startX, int startY,
            int columnWidth, int rowH, JsonArray columns, String rowColor, String altRowColor) {
        int curX = startX, curY = startY;
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            g.setColor(Color.decode(i % 2 == 0 ? rowColor : altRowColor));
            g.fillRect(curX - 5, curY - 5, columnWidth - 10, rowH - 10);

            for (int j = 0; j < columns.size(); j++) {
                JsonObject col   = columns.get(j).getAsJsonObject();
                String     type  = col.has("type") ? col.get("type").getAsString() : "text";
                int        offX  = col.has("x") ? col.get("x").getAsInt() : 0;
                int        offY  = col.has("y") ? col.get("y").getAsInt() : 0;

                if ("avatar".equals(type)) {
                    BufferedImage av = loadImageFromURL(
                            "https://crafatar.com/avatars/" + p.getUniqueId() + "?overlay=true&size=64");
                    if (av != null)
                        g.drawImage(av, curX + offX, curY + offY,
                                col.get("width").getAsInt(), col.get("height").getAsInt(), null);
                } else {
                    String serverIp = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
                    String value = col.get("value").getAsString()
                            .replace("{player_name}", p.getName())
                            .replace("{uuid}",        p.getUniqueId().toString())
                            .replace("{ping}",        java.lang.String.valueOf(p.getPing()))
                            .replace("{world}",       p.getWorld().getName())
                            .replace("{server_ip}",   serverIp);
                    drawTextElement(g, col, value, curX + offX, curY + offY);
                }
            }

            curX += columnWidth;
            if (curX + columnWidth > startX + (columnWidth * 4)) {
                curX = startX;
                curY += rowH;
            }
        }
    }

    // ---- Ping icon --------------------------------------------------------

    private void drawPingIcon(Graphics2D g, int x, int y, int ping) {
        Color barColor;
        int   bars;
        if (ping < 50)        { barColor = new Color(0x4C, 0xAF, 0x50); bars = 4; }
        else if (ping < 100)  { barColor = new Color(0x8B, 0xC3, 0x4A); bars = 3; }
        else if (ping < 150)  { barColor = new Color(0xFF, 0xC1, 0x07); bars = 2; }
        else                  { barColor = new Color(0xF4, 0x43, 0x36); bars = 1; }

        g.setColor(barColor);
        for (int i = 0; i < bars; i++) {
            int bh = 4 + (i * 3);
            g.fillRect(x + (i * 5), y + (12 - bh), 3, bh);
        }
        g.setColor(new Color(0x44, 0x44, 0x55));
        for (int i = bars; i < 4; i++) {
            int bh = 4 + (i * 3);
            g.fillRect(x + (i * 5), y + (12 - bh), 3, bh);
        }
    }

    // ---- Stats / Worlds ---------------------------------------------------

    private void drawStats(Graphics2D g, JsonObject config, Image.BukkitSnapshot snapshot) {
        JsonArray stats = config.getAsJsonArray("stats");
        System sys = plugin.getSystemStats();

        for (int i = 0; i < stats.size(); i++) {
            JsonObject stat  = stats.get(i).getAsJsonObject();
            String label     = stat.get("label").getAsString();
            String value     = stat.get("value").getAsString();

            double[] tps    = sys.getTPS();
            double mspt     = sys.getMSPT();
            double mspt5m   = sys.getMSPT5m();
            double mspt15m  = sys.getMSPT15m();
            double mspt30m  = sys.getMSPT30m();
            long ramUsed    = sys.getUsedMemoryBytes();
            long ramMax     = sys.getMaxMemoryBytes();
            double ramPct   = sys.getRAMPercent();
            double cpu      = sys.getCPUPercent();
            long diskUsed   = sys.getDiskUsedBytes();
            long diskTotal  = sys.getDiskTotalBytes();
            double diskPct  = sys.getDiskPercent();
            String serverIp = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            long heapUsed = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long heapMax  = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
            long gcTime = 0, gcCount = 0;
            for (java.lang.management.GarbageCollectorMXBean gc
                    : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                gcTime  += gc.getCollectionTime();
                gcCount += gc.getCollectionCount();
            }
            int classesLoaded  = java.lang.management.ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
            int pluginsLoaded  = Bukkit.getPluginManager().getPlugins().length;

            value = value
                    .replace("{tps}",          Format.formatTPS(tps[0]))
                    .replace("{tps_5m}",        Format.formatTPS(tps[1]))
                    .replace("{tps_15m}",       Format.formatTPS(tps[2]))
                    .replace("{tps_30m}",       Format.formatTPS(tps[3]))
                    .replace("{mspt}",          Format.formatMSPT(mspt))
                    .replace("{mspt_5m}",       Format.formatMSPT(mspt5m))
                    .replace("{mspt_15m}",      Format.formatMSPT(mspt15m))
                    .replace("{mspt_30m}",      Format.formatMSPT(mspt30m))
                    .replace("{ram_used}",      Format.formatBytes(ramUsed))
                    .replace("{ram_max}",       Format.formatBytes(ramMax))
                    .replace("{ram_free}",      Format.formatBytes(ramMax - ramUsed))
                    .replace("{ram_percent}",   Format.formatPercent(ramPct))
                    .replace("{cpu}",           Format.formatPercent(cpu))
                    .replace("{cpu_cores}",     java.lang.String.valueOf(Runtime.getRuntime().availableProcessors()))
                    .replace("{disk_used}",     Format.formatBytes(diskUsed))
                    .replace("{disk_total}",    Format.formatBytes(diskTotal))
                    .replace("{disk_free}",     Format.formatBytes(diskTotal - diskUsed))
                    .replace("{disk_percent}",  Format.formatPercent(diskPct))
                    .replace("{online}",        java.lang.String.valueOf(snapshot.onlinePlayers()))
                    .replace("{max}",           java.lang.String.valueOf(snapshot.maxPlayers()))
                    .replace("{server_ip}",     serverIp)
                    .replace("{timestamp}",     timestamp)
                    .replace("{chunks_loaded}", java.lang.String.valueOf(snapshot.chunksLoaded()))
                    .replace("{chunks_total}",  java.lang.String.valueOf(snapshot.chunksLoaded()))
                    .replace("{entities_total}",java.lang.String.valueOf(snapshot.entitiesTotal()))
                    .replace("{mobs_total}",    java.lang.String.valueOf(snapshot.mobsTotal()))
                    .replace("{players_online}",java.lang.String.valueOf(snapshot.onlinePlayers()))
                    .replace("{players_max}",   java.lang.String.valueOf(snapshot.maxPlayers()))
                    .replace("{heap_used}",     java.lang.String.valueOf(heapUsed))
                    .replace("{heap_max}",      java.lang.String.valueOf(heapMax))
                    .replace("{gc_time}",       java.lang.String.valueOf(gcTime))
                    .replace("{gc_count}",      java.lang.String.valueOf(gcCount))
                    .replace("{classes_loaded}",java.lang.String.valueOf(classesLoaded))
                    .replace("{plugins_loaded}",java.lang.String.valueOf(pluginsLoaded));

            int x = stat.get("x").getAsInt();
            int y = stat.get("y").getAsInt();
            drawTextElement(g, stat, label + ":", "labelColor", x, y);
            drawTextElement(g, stat, value, "color", x + 100, y);

            if (stat.get("showProgressBar").getAsBoolean()) {
                double percent = 0;
                if (label.equals("TPS"))  percent = tps[0] / 20.0 * 100.0;
                else if (label.equals("MSPT")) percent = sys.getMSPTPercent();
                else if (label.equals("RAM"))  percent = sys.getRAMPercent();
                else if (label.equals("CPU"))  percent = cpu;
                else if (label.equals("Disk")) percent = sys.getDiskPercent();
                drawProgressBar(g, x + 250, y - 10,
                        stat.get("progressBarWidth").getAsInt(),
                        stat.get("progressBarHeight").getAsInt(), percent);
            }
        }
    }

    private void drawWorlds(Graphics2D g, JsonObject config, Image.BukkitSnapshot snapshot) {
        if (!config.has("worlds")) return;
        JsonObject wc = config.getAsJsonObject("worlds");
        if (!wc.get("enabled").getAsBoolean()) return;

        int startY   = wc.get("startY").getAsInt();
        int rowHeight = wc.get("rowHeight").getAsInt();
        Font font = new Font(wc.get("font").getAsString(),
                wc.get("bold").getAsBoolean() ? Font.BOLD : Font.PLAIN,
                wc.get("size").getAsInt());

        g.setFont(font);
        g.setColor(Color.decode(wc.get("labelColor").getAsString()));
        g.drawString(wc.get("header").getAsString(), 50, startY);

        int curY = startY + 30;
        g.setColor(Color.decode(wc.get("color").getAsString()));
        for (Image.BukkitSnapshot.WorldEntry world : snapshot.worlds()) {
            g.drawString(world.name() + " - " + Format.formatBytes(world.sizeBytes()), 70, curY);
            curY += rowHeight;
        }
    }

    // ---- Footer -----------------------------------------------------------

    private void drawFooter(Graphics2D g, JsonObject config, int totalHeight) {
        JsonObject footer = config.getAsJsonObject("footer");
        String timestamp  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String text       = footer.get("text").getAsString().replace("{timestamp}", timestamp);
        int x       = footer.get("x").getAsInt();
        int yOffset = footer.get("y").getAsInt();
        drawTextElement(g, footer, text, x, totalHeight + yOffset);
    }

    // ---- Text helpers -----------------------------------------------------

    private void drawTextElement(Graphics2D g, JsonObject el, String text) {
        drawTextElement(g, el, text, "color", el.get("x").getAsInt(), el.get("y").getAsInt());
    }

    private void drawTextElement(Graphics2D g, JsonObject el, String text, int x, int y) {
        drawTextElement(g, el, text, "color", x, y);
    }

    private void drawTextElement(Graphics2D g, JsonObject el, String text, String colorKey, int x, int y) {
        Font font = new Font(
                el.get("font").getAsString(),
                el.get("bold").getAsBoolean() ? Font.BOLD : Font.PLAIN,
                el.get("size").getAsInt());
        g.setFont(font);
        g.setColor(Color.decode(el.get(colorKey).getAsString()));
        g.drawString(text, x, y);
    }

    private void drawProgressBar(Graphics2D g, int x, int y, int w, int h, double percent) {
        percent = Math.max(0, Math.min(100, percent));
        int filled = (int) (percent / 100.0 * w);
        g.setColor(new Color(0x44, 0x44, 0x55));
        g.fillRoundRect(x, y, w, h, h, h);
        Color barColor;
        if (percent >= 75) barColor = new Color(0xF4, 0x43, 0x36);
        else if (percent >= 50) barColor = new Color(0xFF, 0xC1, 0x07);
        else barColor = new Color(0x4C, 0xAF, 0x50);
        g.setColor(barColor);
        if (filled > 0) g.fillRoundRect(x, y, filled, h, h, h);
    }

    private void applyBlur(Graphics2D g, int radius) {
        float weight = 1.0f / (radius * radius);
        float[] data = new float[radius * radius];
        for (int i = 0; i < data.length; i++) data[i] = weight;
        new ConvolveOp(new Kernel(radius, radius, data));
    }
}
