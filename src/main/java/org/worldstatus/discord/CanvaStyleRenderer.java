package org.worldstatus.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.worldstatus.WorldStatusPlugin;
import org.worldstatus.util.FormatUtil;
import org.worldstatus.util.SystemStats;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CanvaStyleRenderer {

    private final WorldStatusPlugin plugin;
    private final Gson gson = new Gson();

    public CanvaStyleRenderer(WorldStatusPlugin plugin) {
        this.plugin = plugin;
    }

    public BufferedImage renderPlayerList(List<Player> players) {
        try {
            File configFile = new File(plugin.getDataFolder(), "playerlist.json");
            if (!configFile.exists()) {
                configFile = new File(plugin.getDataFolder().getParentFile(), "WorldStatus/playerlist.json");
            }
            if (!configFile.exists()) {
                return null;
            }

            JsonObject config = gson.fromJson(new FileReader(configFile), JsonObject.class);
            if (!config.get("enabled").getAsBoolean()) {
                return null;
            }

            JsonObject canvas = config.getAsJsonObject("canvas");
            int width = canvas.get("width").getAsInt();
            boolean dynamicHeight = canvas.get("dynamicHeight").getAsBoolean();
            int minHeight = canvas.get("minHeight").getAsInt();
            int padding = canvas.get("padding").getAsInt();

            JsonObject playerListConfig = config.getAsJsonObject("playerList");
            int rowHeight = playerListConfig.get("rowHeight").getAsInt();
            int headerHeight = 40;

            int calculatedHeight = minHeight;
            if (dynamicHeight) {
                int contentHeight = 200 + headerHeight + (players.size() * rowHeight) + 100;
                calculatedHeight = Math.max(minHeight, contentHeight);
            }

            BufferedImage img = new BufferedImage(width, calculatedHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            drawBackground(g, config, width, calculatedHeight);
            drawLogo(g, config);
            drawTitle(g, config);
            drawSubtitle(g, config, players.size(), Bukkit.getMaxPlayers());
            drawPlayerListTable(g, config, players);
            drawFooter(g, config, calculatedHeight);

            g.dispose();
            return img;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to render Canva-style player list: " + e.getMessage());
            return null;
        }
    }

    public BufferedImage renderWorldStatus(StatusImageBuilder.BukkitSnapshot snapshot) {
        try {
            File configFile = new File(plugin.getDataFolder(), "worldstatus.json");
            if (!configFile.exists()) {
                configFile = new File(plugin.getDataFolder().getParentFile(), "WorldStatus/worldstatus.json");
            }
            if (!configFile.exists()) {
                return null;
            }

            JsonObject config = gson.fromJson(new FileReader(configFile), JsonObject.class);
            if (!config.get("enabled").getAsBoolean()) {
                return null;
            }

            JsonObject canvas = config.getAsJsonObject("canvas");
            int width = canvas.get("width").getAsInt();
            boolean dynamicHeight = canvas.get("dynamicHeight").getAsBoolean();
            int minHeight = canvas.get("minHeight").getAsInt();

            int calculatedHeight = minHeight;
            if (dynamicHeight) {
                int worldCount = snapshot.worlds().size();
                int contentHeight = 200 + (8 * 50) + (worldCount * 30) + 100;
                calculatedHeight = Math.max(minHeight, contentHeight);
            }

            BufferedImage img = new BufferedImage(width, calculatedHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

            drawBackground(g, config, width, calculatedHeight);
            drawLogo(g, config);
            drawStatusTitle(g, config);
            drawStatusSubtitle(g, config);
            drawStats(g, config, snapshot);
            drawWorlds(g, config, snapshot);
            drawFooter(g, config, calculatedHeight);

            g.dispose();
            return img;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to render Canva-style world status: " + e.getMessage());
            return null;
        }
    }

    private void drawBackground(Graphics2D g, JsonObject config, int width, int height) {
        JsonObject bg = config.getAsJsonObject("background");
        String type = bg.get("type").getAsString();

        if ("image".equals(type)) {
            BufferedImage bgImg = null;
            
            if (bg.has("url")) {
                String url = bg.get("url").getAsString();
                bgImg = loadImageFromURL(url);
            }
            
            if (bgImg == null && bg.has("path")) {
                String path = bg.get("path").getAsString();
                File imgFile = new File(plugin.getDataFolder(), path);
                if (imgFile.exists()) {
                    try {
                        bgImg = ImageIO.read(imgFile);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load background image: " + path);
                    }
                }
            }
            
            if (bgImg != null) {
                g.drawImage(bgImg, 0, 0, width, height, null);
                return;
            }
        }

        String colorHex = bg.get("color").getAsString();
        g.setColor(Color.decode(colorHex));
        g.fillRect(0, 0, width, height);
    }

    private void drawLogo(Graphics2D g, JsonObject config) {
        if (!config.has("logo")) return;
        JsonObject logo = config.getAsJsonObject("logo");
        if (!logo.get("enabled").getAsBoolean()) return;

        BufferedImage logoImg = null;
        
        if (logo.has("url")) {
            String url = logo.get("url").getAsString();
            logoImg = loadImageFromURL(url);
        }
        
        if (logoImg == null && logo.has("path")) {
            String path = logo.get("path").getAsString();
            File logoFile = new File(plugin.getDataFolder(), path);
            if (logoFile.exists()) {
                try {
                    logoImg = ImageIO.read(logoFile);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load logo: " + path);
                }
            }
        }
        
        if (logoImg != null) {
            int x = logo.get("x").getAsInt();
            int y = logo.get("y").getAsInt();
            int w = logo.get("width").getAsInt();
            int h = logo.get("height").getAsInt();
            g.drawImage(logoImg, x, y, w, h, null);
        }
    }

    private BufferedImage loadImageFromURL(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "WorldStatus-Plugin");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            try (InputStream in = conn.getInputStream()) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load image from URL: " + urlString);
            return null;
        }
    }

    private void drawTitle(Graphics2D g, JsonObject config) {
        JsonObject title = config.getAsJsonObject("title");
        String text = title.get("text").getAsString();
        String fontName = title.get("font").getAsString();
        int size = title.get("size").getAsInt();
        String colorHex = title.get("color").getAsString();
        boolean bold = title.get("bold").getAsBoolean();
        int x = title.get("x").getAsInt();
        int y = title.get("y").getAsInt();
        int blur = title.get("blur").getAsInt();

        Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, size);
        g.setFont(font);
        g.setColor(Color.decode(colorHex));

        if (blur > 0) {
            applyBlur(g, blur);
        }

        g.drawString(text, x, y);
    }

    private void drawSubtitle(Graphics2D g, JsonObject config, int online, int max) {
        JsonObject subtitle = config.getAsJsonObject("subtitle");
        String text = subtitle.get("text").getAsString()
                .replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max));
        String fontName = subtitle.get("font").getAsString();
        int size = subtitle.get("size").getAsInt();
        String colorHex = subtitle.get("color").getAsString();
        boolean bold = subtitle.get("bold").getAsBoolean();
        int x = subtitle.get("x").getAsInt();
        int y = subtitle.get("y").getAsInt();

        Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, size);
        g.setFont(font);
        g.setColor(Color.decode(colorHex));
        g.drawString(text, x, y);
    }

    private void drawPlayerListTable(Graphics2D g, JsonObject config, List<Player> players) {
        JsonObject playerListConfig = config.getAsJsonObject("playerList");
        int startX = playerListConfig.get("startX").getAsInt();
        int startY = playerListConfig.get("startY").getAsInt();
        int rowHeight = playerListConfig.get("rowHeight").getAsInt();
        String headerColor = playerListConfig.get("headerColor").getAsString();
        String rowColor = playerListConfig.get("rowColor").getAsString();
        String altRowColor = playerListConfig.get("alternateRowColor").getAsString();
        
        String layout = playerListConfig.has("layout") ? playerListConfig.get("layout").getAsString() : "vertical";
        int columnWidth = playerListConfig.has("columnWidth") ? playerListConfig.get("columnWidth").getAsInt() : 200;

        JsonArray columns = playerListConfig.getAsJsonArray("columns");

        if ("horizontal".equals(layout)) {
            drawPlayerListHorizontal(g, players, startX, startY, columnWidth, rowHeight, columns, rowColor, altRowColor);
        } else {
            drawPlayerListVertical(g, players, startX, startY, rowHeight, headerColor, rowColor, altRowColor, columns);
        }
    }

    private void drawPlayerListVertical(Graphics2D g, List<Player> players, int startX, int startY, int rowHeight,
                                         String headerColor, String rowColor, String altRowColor, JsonArray columns) {
        g.setColor(Color.decode(headerColor));
        g.setFont(new Font("Arial", Font.BOLD, 16));
        int headerY = startY;
        for (int i = 0; i < columns.size(); i++) {
            JsonObject col = columns.get(i).getAsJsonObject();
            if (col.has("header") && !col.get("header").getAsString().isEmpty()) {
                String header = col.get("header").getAsString();
                int colX = startX + col.get("x").getAsInt();
                g.drawString(header, colX, headerY);
            }
        }

        int currentY = startY + 30;
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);

            g.setColor(Color.decode(i % 2 == 0 ? rowColor : altRowColor));
            g.fillRect(startX - 10, currentY - 20, 600, rowHeight);

            for (int j = 0; j < columns.size(); j++) {
                JsonObject col = columns.get(j).getAsJsonObject();
                String type = col.has("type") ? col.get("type").getAsString() : "text";
                int colX = startX + col.get("x").getAsInt();

                if ("avatar".equals(type)) {
                    String avatarUrl = "https://crafatar.com/avatars/" + p.getUniqueId() + "?overlay=true&size=64";
                    BufferedImage avatar = loadImageFromURL(avatarUrl);
                    if (avatar != null) {
                        int w = col.get("width").getAsInt();
                        int h = col.get("height").getAsInt();
                        g.drawImage(avatar, colX, currentY - h + 5, w, h, null);
                    }
                } else if ("ping".equals(type)) {
                    String mode = col.has("mode") ? col.get("mode").getAsString() : "text";
                    int ping = p.getPing();
                    
                    if ("icon".equals(mode)) {
                        drawPingIcon(g, colX, currentY - 10, ping);
                    } else {
                        String value = ping + "ms";
                        String fontName = col.get("font").getAsString();
                        int fontSize = col.get("size").getAsInt();
                        String colorHex = col.get("color").getAsString();
                        
                        Font font = new Font(fontName, Font.PLAIN, fontSize);
                        g.setFont(font);
                        g.setColor(Color.decode(colorHex));
                        g.drawString(value, colX, currentY);
                    }
                } else {
                    String serverIp = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
                    String playerIp = "";
                    try {
                        InetSocketAddress addr = p.getAddress();
                        if (addr != null) playerIp = addr.getAddress().getHostAddress();
                    } catch (Exception ignored) {}
                    
                    String value = col.get("value").getAsString()
                            .replace("{player_name}", p.getName())
                            .replace("{uuid}", p.getUniqueId().toString())
                            .replace("{ping}", String.valueOf(p.getPing()))
                            .replace("{player_ip}", playerIp)
                            .replace("{world}", p.getWorld().getName())
                            .replace("{server_ip}", serverIp);

                    String fontName = col.get("font").getAsString();
                    int fontSize = col.get("size").getAsInt();
                    boolean bold = col.get("bold").getAsBoolean();
                    String colorHex = col.get("color").getAsString();

                    Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, fontSize);
                    g.setFont(font);
                    g.setColor(Color.decode(colorHex));
                    g.drawString(value, colX, currentY);
                }
            }

            currentY += rowHeight;
        }
    }

    private void drawPlayerListHorizontal(Graphics2D g, List<Player> players, int startX, int startY,
                                          int columnWidth, int rowHeight, JsonArray columns,
                                          String rowColor, String altRowColor) {
        int currentX = startX;
        int currentY = startY;
        int playersPerRow = 0;

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);

            g.setColor(Color.decode(i % 2 == 0 ? rowColor : altRowColor));
            g.fillRect(currentX - 5, currentY - 5, columnWidth - 10, rowHeight - 10);

            for (int j = 0; j < columns.size(); j++) {
                JsonObject col = columns.get(j).getAsJsonObject();
                String type = col.has("type") ? col.get("type").getAsString() : "text";
                int offsetX = col.has("x") ? col.get("x").getAsInt() : 0;
                int offsetY = col.has("y") ? col.get("y").getAsInt() : 0;

                if ("avatar".equals(type)) {
                    String avatarUrl = "https://crafatar.com/avatars/" + p.getUniqueId() + "?overlay=true&size=64";
                    BufferedImage avatar = loadImageFromURL(avatarUrl);
                    if (avatar != null) {
                        int w = col.get("width").getAsInt();
                        int h = col.get("height").getAsInt();
                        g.drawImage(avatar, currentX + offsetX, currentY + offsetY, w, h, null);
                    }
                } else {
                    String serverIp = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
                    String value = col.get("value").getAsString()
                            .replace("{player_name}", p.getName())
                            .replace("{uuid}", p.getUniqueId().toString())
                            .replace("{ping}", String.valueOf(p.getPing()))
                            .replace("{world}", p.getWorld().getName())
                            .replace("{server_ip}", serverIp);

                    String fontName = col.has("font") ? col.get("font").getAsString() : "Arial";
                    int fontSize = col.has("size") ? col.get("size").getAsInt() : 14;
                    boolean bold = col.has("bold") && col.get("bold").getAsBoolean();
                    String colorHex = col.has("color") ? col.get("color").getAsString() : "#FFFFFF";

                    Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, fontSize);
                    g.setFont(font);
                    g.setColor(Color.decode(colorHex));
                    g.drawString(value, currentX + offsetX, currentY + offsetY);
                }
            }

            currentX += columnWidth;
            playersPerRow++;

            if (currentX + columnWidth > startX + (columnWidth * 4)) {
                currentX = startX;
                currentY += rowHeight;
                playersPerRow = 0;
            }
        }
    }

    private void drawPingIcon(Graphics2D g, int x, int y, int ping) {
        Color barColor;
        int bars;
        
        if (ping < 50) {
            barColor = new Color(0x4C, 0xAF, 0x50);
            bars = 4;
        } else if (ping < 100) {
            barColor = new Color(0x8B, 0xC3, 0x4A);
            bars = 3;
        } else if (ping < 150) {
            barColor = new Color(0xFF, 0xC1, 0x07);
            bars = 2;
        } else {
            barColor = new Color(0xF4, 0x43, 0x36);
            bars = 1;
        }

        g.setColor(barColor);
        for (int i = 0; i < bars; i++) {
            int barHeight = 4 + (i * 3);
            int barX = x + (i * 5);
            int barY = y + (12 - barHeight);
            g.fillRect(barX, barY, 3, barHeight);
        }

        g.setColor(new Color(0x44, 0x44, 0x55));
        for (int i = bars; i < 4; i++) {
            int barHeight = 4 + (i * 3);
            int barX = x + (i * 5);
            int barY = y + (12 - barHeight);
            g.fillRect(barX, barY, 3, barHeight);
        }
    }

    private void drawStatusTitle(Graphics2D g, JsonObject config) {
        JsonObject title = config.getAsJsonObject("title");
        drawTextElement(g, title, title.get("text").getAsString());
    }

    private void drawStatusSubtitle(Graphics2D g, JsonObject config) {
        JsonObject subtitle = config.getAsJsonObject("subtitle");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String text = subtitle.get("text").getAsString().replace("{timestamp}", timestamp);
        drawTextElement(g, subtitle, text);
    }

    private void drawStats(Graphics2D g, JsonObject config, StatusImageBuilder.BukkitSnapshot snapshot) {
        JsonArray stats = config.getAsJsonArray("stats");
        SystemStats sys = plugin.getSystemStats();

        for (int i = 0; i < stats.size(); i++) {
            JsonObject stat = stats.get(i).getAsJsonObject();
            String label = stat.get("label").getAsString();
            String value = stat.get("value").getAsString();

            double[] tps = sys.getTPS();
            double mspt = sys.getMSPT();
            double mspt5m = sys.getMSPT5m();
            double mspt15m = sys.getMSPT15m();
            double mspt30m = sys.getMSPT30m();
            long ramUsed = sys.getUsedMemoryBytes();
            long ramMax = sys.getMaxMemoryBytes();
            long ramFree = ramMax - ramUsed;
            double ramPercent = sys.getRAMPercent();
            double cpu = sys.getCPUPercent();
            long diskUsed = sys.getDiskUsedBytes();
            long diskTotal = sys.getDiskTotalBytes();
            long diskFree = diskTotal - diskUsed;
            double diskPercent = sys.getDiskPercent();
            String serverIp = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            int chunksLoaded = snapshot.chunksLoaded();
            int entitiesTotal = snapshot.entitiesTotal();
            long mobsTotal = snapshot.mobsTotal();
            
            int cpuCores = Runtime.getRuntime().availableProcessors();
            long heapUsed = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long heapMax = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
            
            long gcTime = 0;
            long gcCount = 0;
            for (java.lang.management.GarbageCollectorMXBean gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                gcTime += gc.getCollectionTime();
                gcCount += gc.getCollectionCount();
            }
            
            int classesLoaded = java.lang.management.ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
            int pluginsLoaded = Bukkit.getPluginManager().getPlugins().length;

            value = value
                    .replace("{tps}", FormatUtil.formatTPS(tps[0]))
                    .replace("{tps_5m}", FormatUtil.formatTPS(tps[1]))
                    .replace("{tps_15m}", FormatUtil.formatTPS(tps[2]))
                    .replace("{tps_30m}", FormatUtil.formatTPS(tps[3]))
                    .replace("{mspt}", FormatUtil.formatMSPT(mspt))
                    .replace("{mspt_5m}", FormatUtil.formatMSPT(mspt5m))
                    .replace("{mspt_15m}", FormatUtil.formatMSPT(mspt15m))
                    .replace("{mspt_30m}", FormatUtil.formatMSPT(mspt30m))
                    .replace("{ram_used}", FormatUtil.formatBytes(ramUsed))
                    .replace("{ram_max}", FormatUtil.formatBytes(ramMax))
                    .replace("{ram_free}", FormatUtil.formatBytes(ramFree))
                    .replace("{ram_percent}", FormatUtil.formatPercent(ramPercent))
                    .replace("{cpu}", FormatUtil.formatPercent(cpu))
                    .replace("{cpu_cores}", String.valueOf(cpuCores))
                    .replace("{disk_used}", FormatUtil.formatBytes(diskUsed))
                    .replace("{disk_total}", FormatUtil.formatBytes(diskTotal))
                    .replace("{disk_free}", FormatUtil.formatBytes(diskFree))
                    .replace("{disk_percent}", FormatUtil.formatPercent(diskPercent))
                    .replace("{online}", String.valueOf(snapshot.onlinePlayers()))
                    .replace("{max}", String.valueOf(snapshot.maxPlayers()))
                    .replace("{server_ip}", serverIp)
                    .replace("{timestamp}", timestamp)
                    .replace("{chunks_loaded}", String.valueOf(chunksLoaded))
                    .replace("{chunks_total}", String.valueOf(chunksLoaded))
                    .replace("{entities_total}", String.valueOf(entitiesTotal))
                    .replace("{mobs_total}", String.valueOf(mobsTotal))
                    .replace("{players_online}", String.valueOf(snapshot.onlinePlayers()))
                    .replace("{players_max}", String.valueOf(snapshot.maxPlayers()))
                    .replace("{heap_used}", String.valueOf(heapUsed))
                    .replace("{heap_max}", String.valueOf(heapMax))
                    .replace("{gc_time}", String.valueOf(gcTime))
                    .replace("{gc_count}", String.valueOf(gcCount))
                    .replace("{classes_loaded}", String.valueOf(classesLoaded))
                    .replace("{plugins_loaded}", String.valueOf(pluginsLoaded));

            int x = stat.get("x").getAsInt();
            int y = stat.get("y").getAsInt();

            String labelColor = stat.get("labelColor").getAsString();
            String fontName = stat.get("font").getAsString();
            int fontSize = stat.get("size").getAsInt();
            boolean bold = stat.get("bold").getAsBoolean();

            Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, fontSize);
            g.setFont(font);
            g.setColor(Color.decode(labelColor));
            g.drawString(label + ":", x, y);

            String valueColor = stat.get("color").getAsString();
            g.setColor(Color.decode(valueColor));
            g.drawString(value, x + 100, y);

            if (stat.get("showProgressBar").getAsBoolean()) {
                int barWidth = stat.get("progressBarWidth").getAsInt();
                int barHeight = stat.get("progressBarHeight").getAsInt();
                double percent = 0;

                if (label.equals("TPS")) percent = tps[0] / 20.0 * 100.0;
                else if (label.equals("MSPT")) percent = sys.getMSPTPercent();
                else if (label.equals("RAM")) percent = sys.getRAMPercent();
                else if (label.equals("CPU")) percent = cpu;
                else if (label.equals("Disk")) percent = sys.getDiskPercent();

                drawProgressBar(g, x + 250, y - 10, barWidth, barHeight, percent);
            }
        }
    }

    private void drawWorlds(Graphics2D g, JsonObject config, StatusImageBuilder.BukkitSnapshot snapshot) {
        if (!config.has("worlds")) return;
        JsonObject worldsConfig = config.getAsJsonObject("worlds");
        if (!worldsConfig.get("enabled").getAsBoolean()) return;

        int startY = worldsConfig.get("startY").getAsInt();
        int rowHeight = worldsConfig.get("rowHeight").getAsInt();
        String fontName = worldsConfig.get("font").getAsString();
        int fontSize = worldsConfig.get("size").getAsInt();
        boolean bold = worldsConfig.get("bold").getAsBoolean();
        String colorHex = worldsConfig.get("color").getAsString();
        String labelColor = worldsConfig.get("labelColor").getAsString();
        String header = worldsConfig.get("header").getAsString();

        Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, fontSize);
        g.setFont(font);
        g.setColor(Color.decode(labelColor));
        g.drawString(header, 50, startY);

        int currentY = startY + 30;
        g.setColor(Color.decode(colorHex));
        for (StatusImageBuilder.BukkitSnapshot.WorldEntry world : snapshot.worlds()) {
            String text = world.name() + " - " + FormatUtil.formatBytes(world.sizeBytes());
            g.drawString(text, 70, currentY);
            currentY += rowHeight;
        }
    }

    private void drawFooter(Graphics2D g, JsonObject config, int totalHeight) {
        JsonObject footer = config.getAsJsonObject("footer");
        String text = footer.get("text").getAsString();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        text = text.replace("{timestamp}", timestamp);

        String fontName = footer.get("font").getAsString();
        int size = footer.get("size").getAsInt();
        String colorHex = footer.get("color").getAsString();
        boolean bold = footer.get("bold").getAsBoolean();
        int x = footer.get("x").getAsInt();
        int yOffset = footer.get("y").getAsInt();

        Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, size);
        g.setFont(font);
        g.setColor(Color.decode(colorHex));
        g.drawString(text, x, totalHeight + yOffset);
    }

    private void drawTextElement(Graphics2D g, JsonObject element, String text) {
        String fontName = element.get("font").getAsString();
        int size = element.get("size").getAsInt();
        String colorHex = element.get("color").getAsString();
        boolean bold = element.get("bold").getAsBoolean();
        int x = element.get("x").getAsInt();
        int y = element.get("y").getAsInt();

        Font font = new Font(fontName, bold ? Font.BOLD : Font.PLAIN, size);
        g.setFont(font);
        g.setColor(Color.decode(colorHex));
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
        for (int i = 0; i < data.length; i++) {
            data[i] = weight;
        }
        Kernel kernel = new Kernel(radius, radius, data);
        ConvolveOp op = new ConvolveOp(kernel);
    }
}
