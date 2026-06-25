package org.worldstatus.lang;

import org.worldstatus.WorldStatus;
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

public class Lang {

    private final WorldStatus plugin;
    private FileConfiguration lang;

    public Lang(WorldStatus plugin) {
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
            lang = loadFromJar("lang/en.yml");
            if (lang == null) {
                plugin.getLogger().severe("[WorldStatus] lang/en.yml not found in jar — all messages will show raw keys!");
                lang = new YamlConfiguration();
            }
        }

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
        return lang.getString(key, key).replace("&", "§");
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
