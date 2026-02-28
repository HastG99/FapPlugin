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
import ru.hastg9.fapplugin.leaderboard.BoardEntry;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.utils.StringUtils;

public class FapCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length > 0) {
            if(args[0].equalsIgnoreCase("reload")) {
                if(!sender.hasPermission("fap.reload")) {
                    sender.sendMessage(FapPlugin.getMessage("messages.no-perm"));
                    return true;
                }

                FapPlugin.getInstance().reloadConfig();

                sender.sendMessage(FapPlugin.getMessage("messages.reload"));
                return true;
            }

            if(args[0].equalsIgnoreCase("top")) {
                if(!sender.hasPermission("fap.top")) {
                    sender.sendMessage(FapPlugin.getMessage("messages.no-perm"));
                    return true;
                }

                FapPlugin.getLeaderBoard().printTop(sender, 10);

                BoardEntry entry = FapPlugin.getLeaderBoard().getEntry(sender.getName());

                if(entry != null) {
                    String time = StringUtils.formatDouble(entry.getTime() / 1000.0);

                    sender.sendMessage("");
                    sender.sendMessage(String.format(
                            FapPlugin.getMessage("messages.personal-record"),
                            time
                    ));
                }

                return true;
            }
        }

        if(!(sender instanceof Player)) {
            sender.sendMessage(FapPlugin.getMessage("messages.only-players"));
            return true;
        }

        Player player = Bukkit.getPlayer(sender.getName());

        if(FapManager.hasFapper(player)) {
            player.sendMessage(FapPlugin.getMessage("messages.already"));
            return true;
        }

        if(FapManager.hasCooldown(player.getName()) && !player.hasPermission("fap.bypass")) {
            player.sendMessage(FapPlugin.getMessage("messages.cooldown"));
            return true;
        }

        BossBar bossBar = Bukkit.createBossBar(FapPlugin.getMessage("messages.bossbar-title"), BarColor.WHITE, BarStyle.SOLID);
        bossBar.setProgress(0.0);
        bossBar.addPlayer(player);

        FapManager.addFapper(player, bossBar);

        player.sendMessage(FapPlugin.getMessage("messages.started"));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 4));
        return true;
    }

}
