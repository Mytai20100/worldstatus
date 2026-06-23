package org.worldstatus.lang;

import org.worldstatus.WorldStatusPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.logging.Level;

public class LangManager {

    private final WorldStatusPlugin plugin;
    private FileConfiguration lang;

    public LangManager(WorldStatusPlugin plugin) {
        this.plugin = plugin;
        loadLang();
    }

    public void reload() {
        loadLang();
    }

    private void loadLang() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        extractDefault(langDir, "en.yml");
        extractDefault(langDir, "vi.yml");

        String locale = plugin.getConfigManager().getLanguage();
        File target   = new File(langDir, locale + ".yml");

        // If the requested locale file doesn't exist, fall back to en.yml on disk.
        // Note: only reassign target when the locale is NOT already "en" — otherwise
        // we'd be setting target to the same path we just checked, which is always missing.
        if (!target.exists()) {
            if (!locale.equalsIgnoreCase("en")) {
                plugin.getLogger().warning("[WorldStatus] lang/" + locale + ".yml not found, falling back to en.yml.");
                target = new File(langDir, "en.yml");
            } else {
                plugin.getLogger().warning("[WorldStatus] lang/en.yml not found on disk, loading from jar.");
            }
        }

        if (target.exists()) {
            lang = YamlConfiguration.loadConfiguration(target);
        } else {
            // File still doesn't exist (extraction failed or jar resource missing).
            // Load en.yml directly from the bundled jar resource as a last resort.
            lang = loadFromJar("lang/en.yml");
            if (lang == null) {
                plugin.getLogger().severe("[WorldStatus] lang/en.yml not found in jar — all messages will show raw keys!");
                lang = new YamlConfiguration();
            }
        }

        // Overlay en.yml as defaults for non-English locales so missing keys degrade gracefully.
        if (!target.getName().equals("en.yml")) {
            File enFile = new File(langDir, "en.yml");
            YamlConfiguration enDefaults = enFile.exists()
                    ? YamlConfiguration.loadConfiguration(enFile)
                    : loadFromJar("lang/en.yml");
            if (enDefaults != null) {
                lang.setDefaults(enDefaults);
            }
        }
    }

    /**
     * Load a YamlConfiguration directly from a jar resource stream.
     * Returns null if the resource does not exist in the jar.
     */
    private YamlConfiguration loadFromJar(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[WorldStatus] Failed to load " + resourcePath + " from jar", e);
            return null;
        }
    }

    private void extractDefault(File langDir, String filename) {
        File out = new File(langDir, filename);
        if (out.exists()) return;
        try (InputStream in = plugin.getResource("lang/" + filename)) {
            if (in == null) {
                plugin.getLogger().warning("[WorldStatus] lang/" + filename + " not found in jar, skipping extraction.");
                return;
            }
            Files.copy(in, out.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[WorldStatus] Could not extract lang/" + filename, e);
        }
    }

    public String get(String key) {
        if (lang == null) return key;
        return lang.getString(key, key).replace("&", "\u00a7");
    }

    public String get(String key, Map<String, String> placeholders) {
        String msg = get(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return msg;
    }

    public String get(String key, String phKey, String phVal) {
        return get(key).replace("{" + phKey + "}", phVal);
    }
}