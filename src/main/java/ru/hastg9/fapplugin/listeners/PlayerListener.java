package ru.hastg9.fapplugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.utils.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private static final int MAX_CLICKS = 35;
    private final Set<UUID> shooters = new HashSet<>();

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!FapManager.hasFapper(player)) return;

        int count = FapManager.getCount(player);
        if (count == -1) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_BLOCK) return;

        World world = player.getWorld();
        BossBar bossBar = FapManager.getBossbar(player);
        if (bossBar == null) return;

        count++;
        FapManager.setFapper(player, count);

        double progress = Math.min(1.0, count / (double) MAX_CLICKS);
        bossBar.setProgress(progress);

        world.playSound(player.getLocation(), getSoundSafe("recharging"), 2f, 2f);

        if (count >= MAX_CLICKS) {
            bossBar.setColor(BarColor.YELLOW);
            startFinishSequence(player);
        }
    }

    private void startFinishSequence(Player player) {
        FapManager.setFapper(player, -1);

        shooters.add(player.getUniqueId());

        final World world = player.getWorld();

        new BukkitRunnable() {
            int shots = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup(player, false);
                    cancel();
                    return;
                }

                if (shots < 3) {
                    world.playSound(player.getLocation(), getSoundSafe("shoot"), 5f, 2f);

                    Location spawnLoc = player.getLocation().toVector()
                            .add(player.getLocation().getDirection().multiply(0.8D))
                            .toLocation(player.getWorld())
                            .add(0.0D, 1.0D, 0.0D);

                    spawnShootProjectile(player, spawnLoc);

                    shots++;
                    return;
                }

                cleanup(player, true);
                cancel();
            }
        }.runTaskTimer(FapPlugin.getInstance(), 0L, 9L);
        Bukkit.getScheduler().runTaskLater(FapPlugin.getInstance(), () -> {
            if (FapManager.hasFapper(player) && FapManager.getCount(player) == -1) {
                cleanup(player, true);
            }
        }, 100L);
    }

    private void cleanup(Player player, boolean successMessageAndStats) {
        if (!player.isOnline()) {
            FapManager.removeBossbar(player);
            FapManager.removeFapper(player);
            return;
        }

        player.removePotionEffect(PotionEffectType.SLOWNESS);

        FapManager.removeFapper(player);
        FapManager.removeBossbar(player);

        if (successMessageAndStats) {
            long time = System.currentTimeMillis() - FapManager.getTime(player);
            player.sendMessage(String.format(FapPlugin.getMessage("messages.success"), StringUtils.formatDouble(time / 1000.0)));
            FapPlugin.getLeaderBoard().update(player, (int) time);
            FapManager.updateCooldown(player.getName());
        }
    }

    private Sound getSoundSafe(String name) {
        String key = FapPlugin.getInstance().getConfig().getString("settings.sounds." + name);
        if (key == null) return Sound.BLOCK_SLIME_BLOCK_FALL;

        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return Sound.BLOCK_SLIME_BLOCK_FALL;
        }
    }

    private void spawnShootProjectile(Player player, Location location) {
        LlamaSpit spit = (LlamaSpit) player.getWorld().spawnEntity(location, EntityType.LLAMA_SPIT);
        spit.setMetadata("cm_shoot", new FixedMetadataValue(FapPlugin.getInstance(), player.getName()));
        spit.setShooter(player);
        spit.setVelocity(player.getLocation().getDirection().multiply(2));
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager) {
            if (FapManager.hasFapper(damager)) {
                event.setCancelled(true);
                return;
            }
        }

        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getDamager() instanceof LlamaSpit spit) {
            if (spit.hasMetadata("cm_shoot")) event.setDamage(0.0);
        }

        if (!FapManager.hasFapper(player)) return;

        player.removePotionEffect(PotionEffectType.SLOWNESS);
        FapManager.removeBossbar(player);
        FapManager.removeFapper(player);

        player.sendMessage(FapPlugin.getMessage("messages.disturbed"));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (isLookAround(event.getFrom(), event.getTo())) return;
        if (FapManager.hasFapper(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Entity entity = event.getHitEntity();
        if (!(projectile instanceof LlamaSpit) || !(entity instanceof Player target)) return;

        if (!(projectile.getShooter() instanceof Player damager)) return;
        if (!shooters.contains(damager.getUniqueId())) return;

        damager.sendMessage(String.format(FapPlugin.getMessage("messages.hit-success"), target.getName()));
        target.sendMessage(FapPlugin.getMessage("messages.were-hit"));

        shooters.remove(damager.getUniqueId());
    }

    private boolean isLookAround(Location from, Location to) {
        if (to == null) return true;
        return from.getX() == to.getX() && from.getZ() == to.getZ() && from.getY() == to.getY();
    }
}