package ru.hastg9.fapplugin.managers;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import ru.hastg9.fapplugin.FapPlugin;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FapManager {

    private static final Map<UUID, FapSession> fappers = new HashMap<>();
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public static void addFapper(Player player, BossBar bossBar) {
        removeFapper(player);
        fappers.put(player.getUniqueId(), new FapSession(bossBar));
    }

    public static void setFapper(Player player, int count) {
        FapSession session = getSession(player);
        if (session != null) session.setCount(count);
    }

    public static boolean hasFapper(Player player) {
        return getSession(player) != null;
    }

    public static void removeFapper(Player player) {
        removeFapper(player.getUniqueId());
    }

    public static void removeFapper(UUID uuid) {
        FapSession session = fappers.remove(uuid);
        if (session != null) session.close();
    }

    public static BossBar getBossbar(Player player) {
        FapSession session = getSession(player);
        return session == null ? null : session.getBossBar();
    }

    public static FapSession getSession(Player player) {
        return fappers.get(player.getUniqueId());
    }

    public static int getCount(Player player) {
        FapSession session = getSession(player);
        return session == null ? -1 : session.getCount();
    }

    public static Set<UUID> getFappers() {
        return new HashSet<>(fappers.keySet());
    }

    public static boolean hasCooldown(String playerName) {
        int cooldownTime = FapPlugin.getConf().getInt("settings.cooldown");
        if (cooldowns.containsKey(playerName)) {
            long left = ((cooldowns.get(playerName) / 1000) + cooldownTime) - (System.currentTimeMillis() / 1000);
            return left >= 0;
        }
        return false;
    }

    public static void updateCooldown(String playerName) {
        cooldowns.put(playerName, System.currentTimeMillis());
    }
}
