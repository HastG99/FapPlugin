package ru.hastg9.fapplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
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
import java.util.UUID;
import java.io.File;
import java.io.IOException;

public final class FapPlugin extends JavaPlugin implements Listener {

    private static FapPlugin instance;
    private static LeaderBoard leaderBoard;
    private PlayerListener playerListener;
    private FileConfiguration settings;

    public static final Logger LOGGER = Logger.getLogger(FapPlugin.class.getSimpleName());

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (!reloadSettings()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();
        registerListeners();

        leaderBoard = new LeaderBoard("data");
        leaderBoard.load();

        scheduleTasks();
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if (playerListener != null) playerListener.clear();
        else FapManager.clear();
        if (leaderBoard != null) leaderBoard.save();
    }

    private void registerCommands() {
        if (getCommand("fap") != null) getCommand("fap").setExecutor(new FapCommand());
        if (getCommand("pip") != null) getCommand("pip").setExecutor(new PipCommand());
    }

    private void registerListeners() {
        playerListener = new PlayerListener();
        Bukkit.getPluginManager().registerEvents(playerListener, this);
    }

    private void scheduleTasks() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            FapManager.clearExpiredCooldowns();
            for (UUID uuid : FapManager.getFappers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    FapManager.removeFapper(uuid);
                    continue;
                }
                if (FapManager.getCount(player) == -1) continue;
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 4));
            }
        }, 100L, 100L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> leaderBoard.save(), 100L, 800L);
    }

    public static String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', instance.getConfig().getString(path));
    }

    @Override
    public FileConfiguration getConfig() {
        return settings == null ? super.getConfig() : settings;
    }

    public boolean reloadSettings() {
        try {
            settings = PluginConfig.load(new File(getDataFolder(), "config.yml"), getResource("config.yml"));
            return true;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException ex) {
            getLogger().warning("Could not load config.yml: " + ex.getMessage());
            return false;
        }
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
