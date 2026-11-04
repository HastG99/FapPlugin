package ru.hastg9.fapplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class PluginConfig {

    public static YamlConfiguration load(File file, InputStream resource)
            throws IOException, InvalidConfigurationException {
        if (resource == null) throw new IOException("Default config.yml is missing");
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            defaults.load(reader);
        }
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        config.setDefaults(defaults);
        validate(config, defaults);
        return config;
    }

    private static void validate(YamlConfiguration config, YamlConfiguration defaults) {
        for (String path : defaults.getKeys(true)) {
            Object value = defaults.get(path);
            if (value instanceof ConfigurationSection) continue;
            if (value instanceof String && !config.isString(path)
                    || value instanceof Integer && !config.isInt(path)) {
                throw new IllegalArgumentException("Wrong value type: " + path);
            }
        }
        for (String path : new String[]{"settings.cooldown", "messages.pip.title.fadein",
                "messages.pip.title.stay", "messages.pip.title.fadeout"}) {
            if (config.getInt(path) < 0) throw new IllegalArgumentException("Negative value: " + path);
        }
        int previous = -1;
        for (String size : new String[]{"smallest", "small", "medium", "big", "biggest"}) {
            int value = config.getInt("messages.pip.size." + size);
            if (value <= previous || value == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid size limit: " + size);
            }
            previous = value;
        }
        for (String path : new String[]{"messages.success", "messages.hit-success", "messages.personal-record"}) {
            String.format(config.getString(path), "1.00");
        }
        String.format(config.getString("messages.leaderboard.entry"), 1, "player", "1.00");
        String.format(config.getString("messages.pip.title.subtitle"), 1);
    }
}
