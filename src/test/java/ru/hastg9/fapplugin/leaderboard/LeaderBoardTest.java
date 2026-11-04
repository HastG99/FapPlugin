package ru.hastg9.fapplugin.leaderboard;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import ru.hastg9.fapplugin.FapPlugin;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LeaderBoardTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private MockedStatic<FapPlugin> pluginMock;
    private LeaderBoard board;

    @Before
    public void setUp() {
        FapPlugin plugin = mock(FapPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.getRoot());
        pluginMock = mockStatic(FapPlugin.class);
        pluginMock.when(FapPlugin::getInstance).thenReturn(plugin);
        board = new LeaderBoard("data");
        board.load();
    }

    @After
    public void tearDown() {
        pluginMock.close();
    }

    @Test
    public void fastestRecordComesFirst() {
        board.update("slow", UUID.randomUUID(), 3000);
        board.update("fast", UUID.randomUUID(), 1000);
        assertEquals("fast", board.getTop(1).iterator().next().getName());
    }

    @Test
    public void equalTimesKeepBothPlayers() {
        board.update("one", UUID.randomUUID(), 1000);
        board.update("two", UUID.randomUUID(), 1000);
        assertEquals(2, board.size());
        assertEquals(2, board.getTop(10).size());
    }

    @Test
    public void improvedRecordMovesToFirstPlace() {
        UUID player = UUID.randomUUID();
        board.update("one", player, 3000);
        board.update("two", UUID.randomUUID(), 1000);
        board.update("one", player, 500);
        assertEquals(player, board.getTop(1).iterator().next().getUUID());
        assertEquals(2, board.size());
    }

    @Test
    public void topReturnsTheRequestedNumber() {
        for (int i = 0; i < 12; i++) board.update("player" + i, UUID.randomUUID(), 1000 + i);
        assertEquals(10, board.getTop(10).size());
        assertTrue(board.getTop(0).isEmpty());
        assertTrue(board.getTop(-1).isEmpty());
    }

    @Test
    public void renamedPlayerKeepsTheBestRecord() {
        UUID player = UUID.randomUUID();
        board.update("old", player, 500);
        board.update("new", player, 1000);
        assertEquals(1, board.size());
        assertEquals("new", board.getEntry(player).getName());
        assertEquals(500, board.getEntry(player).getTime());
        assertNull(board.getEntry("old"));
    }

    @Test
    public void comparisonDoesNotOverflow() {
        board.update("long", UUID.randomUUID(), Long.MAX_VALUE);
        board.update("short", UUID.randomUUID(), 1);
        assertEquals("short", new ArrayList<>(board.getTop(10)).getFirst().getName());
    }

    @Test
    public void invalidTimesAreIgnored() {
        board.update("zero", UUID.randomUUID(), 0);
        board.update("negative", UUID.randomUUID(), -1);
        assertEquals(0, board.size());
    }

    @Test
    public void savedRecordsKeepTiesAndLongTimes() {
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        UUID three = UUID.randomUUID();
        board.update("one", one, 1000);
        board.update("two", two, 1000);
        board.update("three", three, 5_000_000_000L);
        board.save();
        LeaderBoard loaded = new LeaderBoard("data");
        loaded.load();
        assertEquals(3, loaded.size());
        assertEquals(1000, loaded.getEntry(one).getTime());
        assertEquals(5_000_000_000L, loaded.getEntry(three).getTime());
    }

    @Test
    public void brokenYamlIsNotOverwritten() throws Exception {
        Path path = folder.getRoot().toPath().resolve("data.yml");
        String broken = "records: [unfinished";
        Files.writeString(path, broken);
        LeaderBoard loaded = new LeaderBoard("data");
        loaded.load();
        loaded.update("player", UUID.randomUUID(), 1000);
        loaded.save();
        assertEquals(broken, Files.readString(path));
        assertEquals(1, folder.getRoot().listFiles((dir, name) -> name.endsWith(".bak")).length);
    }

    @Test
    public void invalidEntriesAreBackedUpBeforeSavingValidOnes() throws Exception {
        UUID uuid = UUID.randomUUID();
        Path path = folder.getRoot().toPath().resolve("data.yml");
        String data = uuid + ":\n  username: player\n  time: 1000\nnot-a-uuid: broken\n";
        Files.writeString(path, data);
        board.load();
        assertEquals(1, board.size());
        board.save();
        assertEquals(1, folder.getRoot().listFiles((dir, name) -> name.endsWith(".bak")).length);
        Path backup = folder.getRoot().listFiles((dir, name) -> name.endsWith(".bak"))[0].toPath();
        assertEquals(data, Files.readString(backup));
        LeaderBoard loaded = new LeaderBoard("data");
        loaded.load();
        assertEquals(1000, loaded.getEntry(uuid).getTime());
    }

    @Test
    public void updatesAndSavesCanRunTogether() throws Exception {
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 20; i++) players.add(UUID.randomUUID());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var updates = executor.submit(() -> {
                for (int time = 100; time > 0; time--) {
                    for (UUID uuid : players) board.update(uuid.toString(), uuid, time);
                }
            });
            var saves = executor.submit(() -> {
                for (int i = 0; i < 20; i++) board.save();
            });
            updates.get(10, TimeUnit.SECONDS);
            saves.get(10, TimeUnit.SECONDS);
        }
        board.save();
        LeaderBoard loaded = new LeaderBoard("data");
        loaded.load();
        assertEquals(players.size(), loaded.size());
        for (UUID uuid : players) assertEquals(1, loaded.getEntry(uuid).getTime());
        assertEquals(0, folder.getRoot().listFiles((dir, name) -> name.endsWith(".tmp")).length);
    }
}
