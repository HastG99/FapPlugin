package ru.hastg9.fapplugin.listeners;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.utils.StringUtils;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class PlayerListener implements Listener {

    private final Set<Player> shooters = new HashSet<>();

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if(event.getAction() != Action.LEFT_CLICK_AIR &&
                event.getAction() != Action.LEFT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();
        World world = player.getWorld();

        if(!FapManager.hasFapper(player))
            return;

        int count = FapManager.getCount(player);

        if(count == -1)
            return;

        event.setCancelled(true);

        BossBar bossBar = FapManager.getBossbar(player);

        world.playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 2, 2);

        if(count <= 35)
            bossBar.setProgress(count / 35.0);

        if(count > 35) {
            bossBar.setColor(BarColor.YELLOW);

            Random random = new Random();
            if(random.nextInt(4) == 3) {
                FapManager.setFapper(player, -1);
                shooters.add(player);

                Bukkit.getScheduler().runTaskAsynchronously(FapPlugin.getInstance(), () -> {
                    for (int i = 0; i < 3; i++) {
                        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SWIM, 5, 2);

                        Location location = player.getLocation().toVector().add(player.getLocation().getDirection().multiply(0.8D)).
                                toLocation(player.getWorld()).add(0.0D, 1.0D, 0.0D);

                        Bukkit.getScheduler().runTask(FapPlugin.getInstance(), () -> spawnShootProjectile(player, location));

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


    private void spawnShootProjectile(Player player, Location location) {
        LlamaSpit spitmonster = (LlamaSpit) player.getWorld().spawnEntity(location, EntityType.LLAMA_SPIT);
        spitmonster.setMetadata("cm_shoot", new FixedMetadataValue(FapPlugin.getInstance(), player.getName()));
        spitmonster.setShooter(player);
        spitmonster.setVelocity(player.getLocation().getDirection().multiply(2));
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

            if(event.getDamager() instanceof LlamaSpit) {
                LlamaSpit spit = (LlamaSpit) event.getDamager();

                if(spit.hasMetadata("cm_shoot"))
                    event.setDamage(0.0);
            }

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

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Entity entity = event.getHitEntity();

        if(projectile instanceof LlamaSpit && entity instanceof Player) {
            Player target = (Player) entity;

            if(!(projectile.getShooter() instanceof Player)) return;

            Player damager = (Player) projectile.getShooter();

            if(!shooters.contains(damager)) return;

            damager.sendMessage(String.format(FapPlugin.getMessage("messages.hit-success"), target.getName()));
            target.sendMessage(FapPlugin.getMessage("messages.were-hit"));

            shooters.remove(damager);
        }
    }

    private boolean isLookAround(Location from, Location to) {
        return from.getX() == to.getX() &&
                from.getZ() == to.getZ() &&
                from.getY() == to.getY();
    }
}
