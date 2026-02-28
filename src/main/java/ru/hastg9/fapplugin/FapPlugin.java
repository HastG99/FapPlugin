package ru.hastg9.fapplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.hastg9.fapplugin.commands.FapCommand;
import ru.hastg9.fapplugin.commands.PipCommand;
import ru.hastg9.fapplugin.leaderboard.LeaderBoard;
import ru.hastg9.fapplugin.listeners.PlayerListener;
import ru.hastg9.fapplugin.managers.FapManager;

import java.util.logging.Logger;

public final class FapPlugin extends JavaPlugin implements Listener {

    private static FapPlugin instance;
    private static LeaderBoard leaderBoard;

    public static final Logger LOGGER = Logger.getLogger(FapPlugin.class.getSimpleName());

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        registerCommands();
        registerListeners();

        leaderBoard = new LeaderBoard("data");
        leaderBoard.load();

        scheduleTasks();
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (FapManager.hasFapper(p)) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                FapManager.removeBossbar(p);
                FapManager.removeFapper(p);
            }
        }
        leaderBoard.save();
    }

    private void registerCommands() {
        if (getCommand("fap") != null) getCommand("fap").setExecutor(new FapCommand());
        if (getCommand("pip") != null) getCommand("pip").setExecutor(new PipCommand());
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
    }

    private void scheduleTasks() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : FapManager.getFappers().keySet()) {
                if (player == null || !player.isOnline()) continue;
                if (FapManager.getCount(player) == -1) continue; // уже "финиширует" — не обязательно, но чище
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 4));
            }
        }, 100L, 100L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> leaderBoard.save(), 100L, 800L);
    }

    public static String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', instance.getConfig().getString(path));
    }

    public static FapPlugin getInstance() {
        return instance;
    }

    public static FileConfiguration getConf() {
        return instance.getConfig();
    }

    public static LeaderBoard getLeaderBoard() {
        return leaderBoard;
    }
}