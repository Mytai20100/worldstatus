package org.worldstatus.util;

public final class Format {

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    private Format() {}

    public static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        double value = bytes;
        int idx = 0;
        while (value >= 1024.0 && idx < SIZE_UNITS.length - 1) {
            value /= 1024.0;
            idx++;
        }
        return idx == 0 ? bytes + " B" : java.lang.String.format("%.2f %s", value, SIZE_UNITS[idx]);
    }

    public static String percentColor(double percent) {
        if (percent < 50.0) return "§a";
        if (percent < 75.0) return "§e";
        return "§c";
    }

    public static String tpsColor(double tps) {
        if (tps >= 18.0) return "§a";
        if (tps >= 15.0) return "§e";
        return "§c";
    }

    public static String msptColor(double mspt) {
        if (mspt <= 35.0) return "§a";
        if (mspt <= 49.0) return "§e";
        return "§c";
    }

    public static String buildBar(double percent, int length, String filled, String empty, boolean useColor) {
        percent = Math.max(0, Math.min(100, percent));
        int filledCount = (int) Math.round(percent / 100.0 * length);
        int emptyCount  = length - filledCount;

        StringBuilder bar = new StringBuilder();
        if (useColor) bar.append(percentColor(percent));
        bar.append("[");
        bar.append(filled.repeat(Math.max(0, filledCount)));
        bar.append(empty.repeat(Math.max(0, emptyCount)));
        bar.append("]");
        if (useColor) bar.append("§r");
        return bar.toString();
    }

    public static String buildTPSBar(double tps, int length, String filled, String empty, boolean useColor) {
        double percent   = Math.min(tps / 20.0 * 100.0, 100.0);
        int    filledCount = (int) Math.round(percent / 100.0 * length);
        int    emptyCount  = length - filledCount;

        StringBuilder bar = new StringBuilder();
        if (useColor) bar.append(tpsColor(tps));
        bar.append("[");
        bar.append(filled.repeat(Math.max(0, filledCount)));
        bar.append(empty.repeat(Math.max(0, emptyCount)));
        bar.append("]");
        if (useColor) bar.append("§r");
        return bar.toString();
    }

    public static String formatPercent(double d)  { return java.lang.String.format("%.1f", d) + "%"; }
    public static String formatTPS(double tps)     { return java.lang.String.format("%.2f", tps); }
    public static String formatMSPT(double mspt)   { return java.lang.String.format("%.2f", mspt) + "ms"; }
}
