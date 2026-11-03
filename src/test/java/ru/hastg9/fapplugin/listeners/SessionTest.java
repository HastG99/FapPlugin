package ru.hastg9.fapplugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffectType;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.leaderboard.LeaderBoard;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.managers.FapSession;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SessionTest {

    private static MockedStatic<RegistryAccess> registries;

    @BeforeClass
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void setUpRegistries() {
        RegistryAccess access = mock(RegistryAccess.class);
        registries = mockStatic(RegistryAccess.class);
        registries.when(RegistryAccess::registryAccess).thenReturn(access);
        when(access.getRegistry(any(Class.class))).thenAnswer(call -> registry(call.getArgument(0)));
        when(access.getRegistry(any(RegistryKey.class))).thenAnswer(call -> {
            Object key = call.getArgument(0);
            return registry(key == RegistryKey.SOUND_EVENT ? Sound.class
                    : key == RegistryKey.MOB_EFFECT ? PotionEffectType.class : Keyed.class);
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Registry registry(Class<? extends Keyed> type) {
        Registry registry = mock(Registry.class);
        when(registry.getOrThrow(any(NamespacedKey.class))).thenAnswer(call -> {
            Keyed value = mock(type);
            when(value.getKey()).thenReturn(call.getArgument(0));
            return value;
        });
        return registry;
    }

    @AfterClass
    public static void closeRegistries() {
        registries.close();
    }

    private MockedStatic<FapPlugin> pluginMock;
    private MockedStatic<Bukkit> bukkitMock;
    private PlayerListener listener;
    private Player player;
    private World world;
    private BossBar bossBar;
    private LeaderBoard board;
    private BukkitTask task;
    private Runnable finish;

    @Before
    public void setUp() {
        FapPlugin plugin = mock(FapPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        board = mock(LeaderBoard.class);
        pluginMock = mockStatic(FapPlugin.class);
        pluginMock.when(FapPlugin::getInstance).thenReturn(plugin);
        pluginMock.when(FapPlugin::getLeaderBoard).thenReturn(board);
        pluginMock.when(() -> FapPlugin.getMessage("messages.success")).thenReturn("Done: %s");
        pluginMock.when(() -> FapPlugin.getMessage("messages.disturbed")).thenReturn("Stopped");
        task = mock(BukkitTask.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(9L)))
                .thenAnswer(call -> { finish = call.getArgument(1); return task; });
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
        world = mock(World.class);
        when(world.spawnEntity(any(Location.class), eq(EntityType.LLAMA_SPIT))).thenReturn(mock(LlamaSpit.class));
        when(world.spawnEntity(any(Location.class), eq(EntityType.TEXT_DISPLAY))).thenReturn(mock(TextDisplay.class));
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("player");
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        bossBar = mock(BossBar.class);
        FapManager.addFapper(player, bossBar);
        listener = new PlayerListener();
    }

    @After
    public void tearDown() {
        for (UUID uuid : FapManager.getFappers()) FapManager.removeFapper(uuid);
        bukkitMock.close();
        pluginMock.close();
    }

    private void startFinish() {
        FapManager.setFapper(player, 34);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_AIR);
        listener.onClick(event);
        assertNotNull(finish);
    }

    private void interrupt() {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(player);
        listener.onAttack(event);
    }

    @Test
    public void interruptedFinishCannotSaveARecord() {
        startFinish();
        interrupt();
        finish.run();
        assertFalse(FapManager.hasFapper(player));
        verify(task).cancel();
        verify(board, never()).update(any(Player.class), anyLong());
        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
    }

    @Test
    public void oldTaskCannotFinishANewSession() {
        startFinish();
        interrupt();
        BossBar nextBar = mock(BossBar.class);
        FapManager.addFapper(player, nextBar);
        FapSession next = FapManager.getSession(player);
        finish.run();
        assertSame(next, FapManager.getSession(player));
        verify(nextBar, never()).removeAll();
        verify(board, never()).update(any(Player.class), anyLong());
    }

    @Test
    public void failedAnimationReleasesThePlayer() {
        startFinish();
        when(world.spawnEntity(any(Location.class), eq(EntityType.LLAMA_SPIT)))
                .thenThrow(new IllegalStateException("spawn failed"));
        finish.run();
        assertFalse(FapManager.hasFapper(player));
        verify(task).cancel();
        verify(bossBar).removeAll();
        verify(board, never()).update(any(Player.class), anyLong());
    }

    @Test
    public void successfulFinishSavesOnlyOnce() {
        startFinish();
        for (int i = 0; i < 7; i++) finish.run();
        assertFalse(FapManager.hasFapper(player));
        verify(board).update(eq(player), anyLong());
        verify(task).cancel();
        verify(player).sendMessage(startsWith("Done:"));
    }
}
