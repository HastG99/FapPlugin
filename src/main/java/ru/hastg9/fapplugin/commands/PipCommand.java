package ru.hastg9.fapplugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.managers.FapManager;

import java.util.Random;

public class PipCommand implements CommandExecutor {


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player)) {
            sender.sendMessage(FapPlugin.getMessage("messages.only-players"));
            return true;
        }

        Player player = Bukkit.getPlayer(sender.getName());

        Random random = new Random();
        int randNum = random.nextInt(25);
        String title = "";

        if(randNum <= FapPlugin.getConf().getInt("messages.pip.size.smallest"))
            title = FapPlugin.getMessage("messages.pip.title.smallest");
        else if(randNum <= FapPlugin.getConf().getInt("messages.pip.size.small"))
            title = FapPlugin.getMessage("messages.pip.title.small");
        else if(randNum <= FapPlugin.getConf().getInt("messages.pip.size.medium"))
            title = FapPlugin.getMessage("messages.pip.title.medium");
        else if(randNum <= FapPlugin.getConf().getInt("messages.pip.size.big"))
            title = FapPlugin.getMessage("messages.pip.title.big");
        else if(randNum <= FapPlugin.getConf().getInt("messages.pip.size.biggest"))
            title = FapPlugin.getMessage("messages.pip.title.biggest");

        String subtitle = String.format(FapPlugin.getMessage("messages.pip.title.subtitle"), randNum);
        int fadeIn = FapPlugin.getConf().getInt("messages.pip.title.fadein");
        int stay = FapPlugin.getConf().getInt("messages.pip.title.stay");
        int fadeout = FapPlugin.getConf().getInt("messages.pip.title.fadeout");

        player.sendTitle(title, subtitle, fadeIn, stay, fadeout);
        return true;
    }
}
