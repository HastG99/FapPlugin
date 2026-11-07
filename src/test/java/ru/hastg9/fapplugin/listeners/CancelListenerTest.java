package ru.hastg9.fapplugin.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.managers.FapSession;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CancelListenerTest {

    private MockedStatic<FapPlugin> plugin;
    private MockedStatic<FapManager> manager;
    private MockedStatic<Bukkit> bukkit;
    private Player player;
    private PlayerListener sessions;
    private CancelListener listener;
    private AtomicLong clock;

    @Before
    public void setUp() {
        plugin = mockStatic(FapPlugin.class);
        plugin.when(() -> FapPlugin.getMessage("messages.cancel-hint")).thenReturn("Press {key} twice");
        plugin.when(() -> FapPlugin.getMessage("messages.cancelled")).thenReturn("Stopped");
        manager = mockStatic(FapManager.class);
        bukkit = mockStatic(Bukkit.class);
        player = newPlayer();
        sessions = mock(PlayerListener.class);
        clock = new AtomicLong();
        listener = new CancelListener(sessions, clock::get);
        listener.start(player);
    }

    private Player newPlayer() {
        Player result = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(result.getUniqueId()).thenReturn(uuid);
        when(result.isOnline()).thenReturn(true);
        Input current = input(false);
        when(result.getCurrentInput()).thenReturn(current);
        FapSession session = new FapSession(mock(BossBar.class));
        manager.when(() -> FapManager.getSession(result)).thenReturn(session);
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(result);
        return result;
    }

    private Input input(boolean jumping) {
        Input input = mock(Input.class);
        when(input.isJump()).thenReturn(jumping);
        return input;
    }

    private void jump(Player target, boolean pressed) {
        listener.onInput(new PlayerInputEvent(target, input(pressed)));
    }

    @After
    public void tearDown() {
        if (listener != null) listener.clear();
        bukkit.close();
        manager.close();
        plugin.close();
    }

    @Test
    public void startShowsTheConfiguredHintWithThePlayersJumpKey() {
        ArgumentCaptor<Component> hint = ArgumentCaptor.forClass(Component.class);
        verify(player).sendActionBar(hint.capture());
        String json = GsonComponentSerializer.gson().serialize(hint.getValue());
        assertTrue(json.contains("Press "));
        assertTrue(json.contains("\"keybind\":\"key.jump\""));
        assertFalse(json.contains("{key}"));
    }

    @Test
    public void onePressDoesNotCancel() {
        jump(player, true);
        verify(sessions, never()).cancelSession(any());
    }

    @Test
    public void twoPressesCancelOnlyOnce() {
        jump(player, true);
        jump(player, false);
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(200));
        jump(player, true);
        jump(player, false);
        jump(player, true);
        verify(sessions).cancelSession(player);
        verify(player).sendMessage("Stopped");
        verify(player).sendActionBar(Component.empty());
    }

    @Test
    public void holdingJumpDoesNotCountAsTwoPresses() {
        jump(player, true);
        jump(player, true);
        jump(player, true);
        verify(sessions, never()).cancelSession(any());
    }

    @Test
    public void jumpHeldBeforeStartingRequiresTwoNewPresses() {
        Input current = input(true);
        when(player.getCurrentInput()).thenReturn(current);
        listener.start(player);
        jump(player, true);
        jump(player, false);
        jump(player, true);
        verify(sessions, never()).cancelSession(any());
        jump(player, false);
        jump(player, true);
        verify(sessions).cancelSession(player);
    }

    @Test
    public void lateSecondPressStartsANewPair() {
        jump(player, true);
        jump(player, false);
        clock.set(TimeUnit.SECONDS.toNanos(1) + 1);
        jump(player, true);
        verify(sessions, never()).cancelSession(any());
        jump(player, false);
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));
        jump(player, true);
        verify(sessions).cancelSession(player);
    }

    @Test
    public void oneSecondIsStillWithinTheWindow() {
        jump(player, true);
        jump(player, false);
        clock.set(TimeUnit.SECONDS.toNanos(1));
        jump(player, true);
        verify(sessions).cancelSession(player);
    }

    @Test
    public void newSessionDoesNotInheritTheFirstPress() {
        jump(player, true);
        FapSession next = new FapSession(mock(BossBar.class));
        manager.when(() -> FapManager.getSession(player)).thenReturn(next);
        listener.start(player);
        jump(player, true);
        verify(sessions, never()).cancelSession(any());
        jump(player, false);
        jump(player, true);
        verify(sessions).cancelSession(player);
    }

    @Test
    public void replacedSessionWithoutStartCannotBeCancelled() {
        jump(player, true);
        jump(player, false);
        FapSession next = new FapSession(FapManager.getSession(player).getBossBar());
        manager.when(() -> FapManager.getSession(player)).thenReturn(next);
        jump(player, true);
        verify(sessions, never()).cancelSession(any());
    }

    @Test
    public void finishingAnimationCanBeCancelled() {
        FapManager.getSession(player).setCount(-1);
        jump(player, true);
        jump(player, false);
        jump(player, true);
        verify(sessions).cancelSession(player);
    }

    @Test
    public void playersDoNotSharePresses() {
        Player other = newPlayer();
        listener.start(other);
        jump(player, true);
        jump(other, true);
        verify(sessions, never()).cancelSession(any());
        jump(player, false);
        jump(player, true);
        verify(sessions).cancelSession(player);
        verify(sessions, never()).cancelSession(other);
    }

    @Test
    public void completedSessionStopsTheHint() {
        manager.when(() -> FapManager.getSession(player)).thenReturn(null);
        listener.refreshHints();
        jump(player, true);
        jump(player, false);
        jump(player, true);
        verify(player).sendActionBar(Component.empty());
        verify(sessions, never()).cancelSession(any());
    }

    @Test
    public void hintIsRefreshedWhileTheSessionIsActive() {
        clearInvocations(player);
        listener.refreshHints();
        listener.refreshHints();
        verify(player, times(2)).sendActionBar(any(Component.class));
    }

    @Test
    public void missingSessionDoesNotShowAHint() {
        listener.clear();
        clearInvocations(player);
        manager.when(() -> FapManager.getSession(player)).thenReturn(null);
        listener.start(player);
        listener.refreshHints();
        verify(player, never()).sendActionBar(any(Component.class));
    }

    @Test
    public void quitDropsPendingPresses() {
        jump(player, true);
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onQuit(event);
        listener.start(player);
        jump(player, true);
        verify(sessions, never()).cancelSession(any());
    }

    @Test
    public void clearRemovesTheHintAndPendingPresses() {
        jump(player, true);
        listener.clear();
        jump(player, false);
        jump(player, true);
        verify(player).sendActionBar(Component.empty());
        verify(sessions, never()).cancelSession(any());
    }
}
