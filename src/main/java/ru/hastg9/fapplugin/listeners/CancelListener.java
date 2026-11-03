package ru.hastg9.fapplugin.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.managers.FapSession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public class CancelListener implements Listener {

    private static final long DOUBLE_PRESS_TIME = TimeUnit.SECONDS.toNanos(1);
    private final Map<UUID, CancelState> players = new HashMap<>();
    private final PlayerListener listener;
    private final LongSupplier clock;

    public CancelListener(PlayerListener listener) {
        this(listener, System::nanoTime);
    }

    CancelListener(PlayerListener listener, LongSupplier clock) {
        this.listener = listener;
        this.clock = clock;
    }

    public void start(Player player) {
        stop(player);
        FapSession session = FapManager.getSession(player);
        if (session == null) return;
        players.put(player.getUniqueId(), new CancelState(session, player.getCurrentInput().isJump()));
        showHint(player);
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        CancelState state = players.get(player.getUniqueId());
        if (state == null) return;
        if (FapManager.getSession(player) != state.session) {
            stop(player);
            return;
        }
        boolean jump = event.getInput().isJump();
        boolean pressed = jump && !state.jumpHeld;
        state.jumpHeld = jump;
        if (!pressed) return;
        long now = clock.getAsLong();
        if (state.firstPress != null && now - state.firstPress <= DOUBLE_PRESS_TIME) {
            listener.cancelSession(player);
            stop(player);
            player.sendMessage(FapPlugin.getMessage("messages.cancelled"));
        } else {
            state.firstPress = now;
        }
    }

    public void refreshHints() {
        for (UUID uuid : new HashSet<>(players.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                players.remove(uuid);
            } else if (FapManager.getSession(player) != players.get(uuid).session) {
                stop(player);
            } else {
                showHint(player);
            }
        }
    }

    private void showHint(Player player) {
        Component hint = LegacyComponentSerializer.legacySection()
                .deserialize(FapPlugin.getMessage("messages.cancel-hint"))
                .replaceText(builder -> builder.matchLiteral("{key}").replacement(Component.keybind("key.jump")));
        player.sendActionBar(hint);
    }

    public void stop(Player player) {
        if (players.remove(player.getUniqueId()) != null) player.sendActionBar(Component.empty());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }

    public void clear() {
        for (UUID uuid : new HashSet<>(players.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) stop(player);
        }
        players.clear();
    }

    private static class CancelState {
        private final FapSession session;
        private boolean jumpHeld;
        private Long firstPress;

        private CancelState(FapSession session, boolean jumpHeld) {
            this.session = session;
            this.jumpHeld = jumpHeld;
        }
    }
}
