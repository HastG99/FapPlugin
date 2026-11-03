package ru.hastg9.fapplugin;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PluginConfigTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private File file;

    @Before
    public void setUp() throws Exception {
        file = new File(folder.getRoot(), "config.yml");
        try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            Files.copy(stream, file.toPath());
        }
    }

    private YamlConfiguration load() throws Exception {
        return PluginConfig.load(file, getClass().getResourceAsStream("/config.yml"));
    }

    @Test
    public void missingValuesUseDefaults() throws Exception {
        Files.writeString(file.toPath(), "");
        YamlConfiguration config = load();
        assertEquals(90, config.getInt("settings.cooldown"));
        assertEquals(28, config.getInt("messages.pip.size.biggest"));
        assertNotNull(config.getString("messages.reload-failed"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidMessageFormatIsRejected() throws Exception {
        YamlConfiguration config = load();
        config.set("messages.success", "%d");
        config.save(file);
        load();
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongValueTypeIsRejected() throws Exception {
        YamlConfiguration config = load();
        config.set("messages.started", 100);
        config.save(file);
        load();
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeCooldownIsRejected() throws Exception {
        YamlConfiguration config = load();
        config.set("settings.cooldown", -1);
        config.save(file);
        load();
    }

    @Test(expected = IllegalArgumentException.class)
    public void reversedSizeLimitsAreRejected() throws Exception {
        YamlConfiguration config = load();
        config.set("messages.pip.size.biggest", 10);
        config.save(file);
        load();
    }

    @Test(expected = IllegalArgumentException.class)
    public void overflowingRandomBoundIsRejected() throws Exception {
        YamlConfiguration config = load();
        config.set("messages.pip.size.biggest", Integer.MAX_VALUE);
        config.save(file);
        load();
    }

    @Test(expected = InvalidConfigurationException.class)
    public void brokenYamlIsRejected() throws Exception {
        Files.writeString(file.toPath(), "messages: [unfinished");
        load();
    }

    @Test
    public void failedReloadKeepsTheLastWorkingSettings() throws Exception {
        FapPlugin plugin = mock(FapPlugin.class, CALLS_REAL_METHODS);
        doReturn(folder.getRoot()).when(plugin).getDataFolder();
        doReturn(Logger.getAnonymousLogger()).when(plugin).getLogger();
        doAnswer(call -> getClass().getResourceAsStream("/config.yml")).when(plugin).getResource("config.yml");
        assertTrue(plugin.reloadSettings());
        FileConfiguration previous = plugin.getConfig();
        Files.writeString(file.toPath(), "messages: [unfinished");
        assertFalse(plugin.reloadSettings());
        assertSame(previous, plugin.getConfig());
        assertEquals(90, plugin.getConfig().getInt("settings.cooldown"));
        YamlConfiguration corrected = new YamlConfiguration();
        corrected.set("settings.cooldown", 15);
        corrected.save(file);
        assertTrue(plugin.reloadSettings());
        assertEquals(15, plugin.getConfig().getInt("settings.cooldown"));
    }
}
