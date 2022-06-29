package ru.hastg9.fapplugin.managers;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import ru.hastg9.fapplugin.FapPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FapManager {

    private static final Map<Player, Integer> fappers = new ConcurrentHashMap<>();
    private static final Map<Player, BossBar> bossbars = new ConcurrentHashMap<>();
    private static final Map<Player, Long> times = new ConcurrentHashMap<>();
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();


    public static void addFapper(Player player, BossBar bossBar) {
        bossbars.put(player, bossBar);
        times.put(player, System.currentTimeMillis());
        fappers.put(player, 0);
    }

    public static void setFapper(Player player, int count) {
        fappers.put(player, count);
    }

    public static boolean hasFapper(Player player) {
        return fappers.containsKey(player);
    }

    public static void removeFapper(Player player) {
        fappers.remove(player);
    }

    public static void removeBossbar(Player player) {
        BossBar bossBar = bossbars.get(player);

        bossBar.removePlayer(player);
        bossbars.remove(player);
    }

    public static BossBar getBossbar(Player player) {
        return bossbars.get(player);
    }

    public static long getTime(Player player) {
        return times.get(player);
    }

    public static int getCount(Player player) {
        return fappers.get(player);
    }

    public static Map<Player, Integer> getFappers() {
        return fappers;
    }

    public static Map<Player, BossBar> getBossbars() {
        return bossbars;
    }

    public static Map<Player, Long> getTimes() {
        return times;
    }

    public static boolean hasCooldown(String playerName) {
        int cooldownTime = FapPlugin.getConf().getInt("settings.cooldown");

        if(cooldowns.containsKey(playerName)) {
            long left = ((cooldowns.get(playerName)/1000)+cooldownTime) - (System.currentTimeMillis()/1000);

            return left >= 0;
        }

        return false;
    }

    public static void updateCooldown(String playerName) {
        cooldowns.put(playerName, System.currentTimeMillis());
    }

}
