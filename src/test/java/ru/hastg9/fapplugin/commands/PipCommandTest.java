package ru.hastg9.fapplugin.commands;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import ru.hastg9.fapplugin.FapPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.mockito.Mockito.*;

public class PipCommandTest {

    @Test
    public void configuredMaximumCanBeRolled() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        Player player = mock(Player.class);
        try (MockedStatic<FapPlugin> plugin = mockStatic(FapPlugin.class);
             MockedConstruction<Random> random = mockConstruction(Random.class,
                     (mock, context) -> when(mock.nextInt(29)).thenReturn(28))) {
            plugin.when(FapPlugin::getConf).thenReturn(config);
            plugin.when(() -> FapPlugin.getMessage(anyString()))
                    .thenAnswer(call -> config.getString(call.getArgument(0)));
            new PipCommand().onCommand(player, null, "pip", new String[0]);
            verify(random.constructed().getFirst()).nextInt(29);
            verify(player).sendTitle(config.getString("messages.pip.title.biggest"),
                    String.format(config.getString("messages.pip.title.subtitle"), 28), 1, 100, 10);
        }
    }
}
