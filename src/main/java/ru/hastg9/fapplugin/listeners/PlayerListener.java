package ru.hastg9.fapplugin.listeners;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.utils.StringUtils;

import java.util.Random;

public class PlayerListener implements Listener {

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if(event.getAction() != Action.LEFT_CLICK_AIR &&
                event.getAction() != Action.LEFT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();

        if(!FapManager.hasFapper(player))
            return;

        int count = FapManager.getCount(player);

        if(count == -1)
            return;

        event.setCancelled(true);

        BossBar bossBar = FapManager.getBossbar(player);

        player.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 1, 2);

        if(count <= 35)
            bossBar.setProgress(count / 35.0);

        if(count > 35) {
            bossBar.setColor(BarColor.YELLOW);

            Random random = new Random();
            if(random.nextInt(4) == 3) {
                FapManager.setFapper(player, -1);

                Bukkit.getScheduler().runTaskAsynchronously(FapPlugin.getInstance(), () -> {
                    for (int i = 0; i < 3; i++) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SWIM, 1, 2);

                        player.spawnParticle(Particle.SPIT, player.getEyeLocation(), 10, 0, 1, 0);

                        Location location = player.getLocation().toVector().add(player.getLocation().getDirection().multiply(0.8D)).
                                toLocation(player.getWorld()).add(0.0D, 1.0D, 0.0D);

                        Bukkit.getScheduler().runTask(FapPlugin.getInstance(), () -> {
                            Entity spitmonster = player.getWorld().spawnEntity(location, EntityType.LLAMA_SPIT);
                            spitmonster.setVelocity(player.getLocation().getDirection().multiply(2));
                        });

                        try {
                            Thread.sleep(450);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    Bukkit.getScheduler().runTask(FapPlugin.getInstance(), () -> player.removePotionEffect(PotionEffectType.SLOW));

                    FapManager.removeFapper(player);
                    FapManager.removeBossbar(player);
                });

                long time = System.currentTimeMillis() - FapManager.getTime(player);

                player.sendMessage(String.format(FapPlugin.getMessage("messages.success"), StringUtils.formatDouble(time / 1000.0)));

                FapManager.updateCooldown(player.getName());
            }

            return;
        }

        FapManager.setFapper(player, ++count);
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            if(FapManager.hasFapper(player)) {
                event.setCancelled(true);
                return;
            }
        }

        if(event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if(!FapManager.hasFapper(player))
                return;

            player.removePotionEffect(PotionEffectType.SLOW);
            FapManager.removeFapper(player);
            FapManager.removeBossbar(player);

            player.sendMessage(FapPlugin.getMessage("messages.disturbed"));
        }
    }


    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (isLookAround(event.getFrom(), event.getTo()))
            return;

        if(FapManager.hasFapper(event.getPlayer()))
            event.setCancelled(true);
    }

    private boolean isLookAround(Location from, Location to) {
        return from.getX() == to.getX() &&
                from.getZ() == to.getZ() &&
                from.getY() == to.getY();
    }
}
