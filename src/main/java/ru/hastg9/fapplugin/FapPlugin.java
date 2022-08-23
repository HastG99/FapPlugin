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
import ru.hastg9.fapplugin.listeners.PlayerListener;
import ru.hastg9.fapplugin.managers.FapManager;

public final class FapPlugin extends JavaPlugin implements Listener {

    private static FapPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        registerCommands();
        registerListeners();

        scheduleTasks();
    }

    private void registerCommands() {
        getCommand("fap").setExecutor(new FapCommand());
        getCommand("pip").setExecutor(new PipCommand());
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
    }

    private void scheduleTasks() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : FapManager.getFappers().keySet())
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 4));

        }, 100L, 100L);
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

}
