package ru.hastg9.fapplugin.listeners;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.leaderboard.LeaderBoard;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.managers.FapSession;

import java.util.UUID;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CancelSessionTest {

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
    private CancelListener controls;
    private Player player;
    private World world;
    private BossBar bossBar;
    private BukkitTask task;
    private LeaderBoard board;
    private Runnable finish;

    @Before
    public void setUp() {
        FapPlugin plugin = mock(FapPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.cooldown", 90);
        when(plugin.getConfig()).thenReturn(config);
        board = mock(LeaderBoard.class);
        pluginMock = mockStatic(FapPlugin.class);
        pluginMock.when(FapPlugin::getInstance).thenReturn(plugin);
        pluginMock.when(FapPlugin::getConf).thenReturn(config);
        pluginMock.when(FapPlugin::getLeaderBoard).thenReturn(board);
        pluginMock.when(() -> FapPlugin.getMessage(anyString())).thenReturn("message");
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(9L)))
                .thenAnswer(call -> { finish = call.getArgument(1); return task; });
        bukkitMock = mockStatic(Bukkit.class);
        bukkitMock.when(Bukkit::getScheduler).thenReturn(scheduler);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("player");
        when(player.isOnline()).thenReturn(true);
        bukkitMock.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getCurrentInput()).thenReturn(mock(Input.class));
        bossBar = mock(BossBar.class);
        FapManager.addFapper(player, bossBar);
        listener = new PlayerListener();
        controls = new CancelListener(listener, () -> 0L);
        controls.start(player);
    }

    @After
    public void tearDown() {
        controls.clear();
        listener.clear();
        bukkitMock.close();
        pluginMock.close();
    }

    private void cancelWithJump(Player target) {
        Input press = mock(Input.class);
        when(press.isJump()).thenReturn(true);
        controls.onInput(new PlayerInputEvent(target, press));
        controls.onInput(new PlayerInputEvent(target, mock(Input.class)));
        controls.onInput(new PlayerInputEvent(target, press));
    }

    private void startFinish() {
        FapManager.setFapper(player, 34);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.LEFT_CLICK_AIR);
        listener.onClick(event);
        assertNotNull(finish);
    }

    @Test
    public void doubleJumpReleasesThePlayerWithoutSavingARecord() {
        cancelWithJump(player);
        assertFalse(FapManager.hasFapper(player));
        assertNull(FapManager.getBossbar(player));
        verify(player).removePotionEffect(PotionEffectType.SLOWNESS);
        verify(bossBar).removeAll();
        assertFalse(FapManager.hasCooldown(player.getUniqueId()));
        verifyNoInteractions(board);
    }

    @Test
    public void cancelledFinishCannotSaveARecordLater() {
        startFinish();
        cancelWithJump(player);
        finish.run();
        assertFalse(FapManager.hasFapper(player));
        verify(task).cancel();
        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
        verifyNoInteractions(board);
    }

    @Test
    public void oldFinishCannotTouchANewSession() {
        startFinish();
        cancelWithJump(player);
        BossBar next = mock(BossBar.class);
        FapManager.addFapper(player, next);
        FapSession session = FapManager.getSession(player);
        finish.run();
        assertSame(session, FapManager.getSession(player));
        assertEquals(0, FapManager.getCount(player));
        verify(next, never()).removeAll();
        verifyNoInteractions(board);
    }

    @Test
    public void cancelRemovesFlyingProjectilesAndTheirText() {
        FapPlugin plugin = FapPlugin.getInstance();
        LlamaSpit spit = mock(LlamaSpit.class);
        TextDisplay text = mock(TextDisplay.class);
        UUID uuid = UUID.randomUUID();
        when(spit.getUniqueId()).thenReturn(uuid);
        when(spit.getMetadata("cm_shoot")).thenReturn(List.of(new FixedMetadataValue(plugin, true)));
        when(text.getMetadata("cm_shoot")).thenReturn(List.of(new FixedMetadataValue(plugin, true)));
        when(spit.getPassengers()).thenReturn(List.of(text));
        when(world.spawnEntity(any(Location.class), eq(EntityType.LLAMA_SPIT))).thenReturn(spit);
        when(world.spawnEntity(any(Location.class), eq(EntityType.TEXT_DISPLAY))).thenReturn(text);
        bukkitMock.when(() -> Bukkit.getEntity(uuid)).thenReturn(spit);
        startFinish();
        finish.run();
        cancelWithJump(player);
        verify(spit).remove();
        verify(text).remove();
        verify(task).cancel();
        assertNull(FapManager.getSession(player));
        assertFalse(FapManager.hasCooldown(player.getUniqueId()));
        verifyNoInteractions(board);
    }

    @Test
    public void cancelUsesThePlayerUuid() {
        Player current = mock(Player.class);
        UUID uuid = player.getUniqueId();
        when(current.getUniqueId()).thenReturn(uuid);
        cancelWithJump(current);
        assertNull(FapManager.getSession(player));
        verify(bossBar).removeAll();
        verify(current).removePotionEffect(PotionEffectType.SLOWNESS);
        verifyNoInteractions(board);
    }
}
