package ru.hastg9.fapplugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ru.hastg9.fapplugin.FapPlugin;
import ru.hastg9.fapplugin.managers.FapManager;
import ru.hastg9.fapplugin.managers.FapSession;
import ru.hastg9.fapplugin.utils.StringUtils;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private static final int MAX_CLICKS = 35;
    private final Set<UUID> shooters = new HashSet<>();
    private final Map<UUID, UUID> projectiles = new HashMap<>();

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
            try {
                startFinishSequence(player);
            } catch (RuntimeException ex) {
                cleanup(player, false);
                FapPlugin.LOGGER.log(Level.WARNING, "Could not start finish task", ex);
            }
        }
    }

    private void startFinishSequence(Player player) {
        FapSession session = FapManager.getSession(player);
        if (session == null) return;
        FapManager.setFapper(player, -1);

        shooters.add(player.getUniqueId());

        session.setTask(new BukkitRunnable() {
            int shots = ThreadLocalRandom.current().nextInt(3,6);
            int counter = 0;

            @Override
            public void run() {
                if (FapManager.getSession(player) != session) {
                    cancel();
                    return;
                }
                if (!player.isOnline() || player.isDead()) {
                    cleanup(player, false);
                    return;
                }
                try {
                    if (counter < shots) {
                        player.getWorld().playSound(player.getLocation(), getSoundSafe("shoot"), 5f, 2f);
                        player.playHurtAnimation(0);
                        Location spawnLoc = player.getLocation().toVector()
                                .add(player.getLocation().getDirection().multiply(0.8D))
                                .toLocation(player.getWorld())
                                .add(0.0D, 1.0D, 0.0D);
                        spawnShootProjectile(player, spawnLoc);
                        counter++;
                    } else {
                        cleanup(player, true);
                    }
                } catch (RuntimeException ex) {
                    cleanup(player, false);
                    FapPlugin.LOGGER.log(Level.WARNING, "Could not finish session", ex);
                }
            }
        }.runTaskTimer(FapPlugin.getInstance(), 0L, 9L));
    }

    private void cleanup(Player player, boolean successMessageAndStats) {
        if (!successMessageAndStats) removeProjectiles(player.getUniqueId());
        FapSession session = FapManager.getSession(player);
        if (session == null) return;
        long time = session.getTime();
        FapManager.removeFapper(player);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        if (successMessageAndStats && player.isOnline() && time > 0) {
            FapPlugin.getLeaderBoard().update(player, time);
            FapManager.updateCooldown(player.getUniqueId());
            player.sendMessage(String.format(FapPlugin.getMessage("messages.success"), StringUtils.formatDouble(time / 1000.0)));
        }
    }

    public void cancelSession(Player player) {
        cleanup(player, false);
    }

    private Sound getSoundSafe(String name) {
        String key = FapPlugin.getConf().getString("settings.sounds." + name);
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
        projectiles.put(spit.getUniqueId(), player.getUniqueId());
        spit.setShooter(player);
        spit.setVelocity(player.getLocation().getDirection().multiply(2));
        spit.setGlowing(true);

        TextDisplay text = (TextDisplay) player.getWorld()
                .spawnEntity(location, EntityType.TEXT_DISPLAY);

        text.setMetadata("cm_shoot", new FixedMetadataValue(FapPlugin.getInstance(), player.getName()));
        text.setText("§lСперма");
        text.setBillboard(Display.Billboard.CENTER);
        text.setSeeThrough(true);
        text.setShadowed(true);
        text.setInterpolationDuration(1);

        spit.addPassenger(text);
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof LlamaSpit spit && isOwnEntity(spit)) {
            removeShootText(spit);
            UUID owner = projectiles.remove(spit.getUniqueId());
            if (owner != null && !projectiles.containsValue(owner)) shooters.remove(owner);
        }
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
            if (isOwnEntity(spit)) event.setDamage(0.0);
        }

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player player)) return;
        if (!FapManager.hasFapper(player)) return;
        cleanup(player, false);
        player.sendMessage(FapPlugin.getMessage("messages.disturbed"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanup(event.getEntity(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.isCancelled() && !isLookAround(event.getFrom(), event.getTo())) {
            cleanup(event.getPlayer(), false);
        }
    }

    public void clear() {
        for (UUID uuid : FapManager.getFappers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) cleanup(player, false);
        }
        for (UUID owner : new HashSet<>(projectiles.values())) removeProjectiles(owner);
        shooters.clear();
        FapManager.clear();
    }

    private void removeProjectiles(UUID owner) {
        for (UUID uuid : new HashSet<>(projectiles.keySet())) {
            if (!owner.equals(projectiles.get(uuid))) continue;
            projectiles.remove(uuid);
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LlamaSpit spit && isOwnEntity(spit)) {
                removeShootText(spit);
                spit.remove();
            }
        }
        shooters.remove(owner);
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

        if (!(projectile instanceof LlamaSpit spit) || !isOwnEntity(spit)) return;
        removeShootText(spit);

        if (!(entity instanceof Player target)) return;
        if (!(projectile.getShooter() instanceof Player damager)) return;
        if (!shooters.contains(damager.getUniqueId())) return;

        damager.sendMessage(String.format(FapPlugin.getMessage("messages.hit-success"), target.getName()));
        target.sendMessage(FapPlugin.getMessage("messages.were-hit"));

        shooters.remove(damager.getUniqueId());
    }

    private void removeShootText(LlamaSpit spit) {
        for (Entity passenger : spit.getPassengers()) {
            if (passenger instanceof TextDisplay && isOwnEntity(passenger)) {
                passenger.remove();
            }
        }
    }

    private boolean isOwnEntity(Entity entity) {
        for (MetadataValue value : entity.getMetadata("cm_shoot")) {
            if (value.getOwningPlugin() == FapPlugin.getInstance()) return true;
        }
        return false;
    }

    private boolean isLookAround(Location from, Location to) {
        if (to == null) return true;
        return Objects.equals(from.getWorld(), to.getWorld())
                && from.getX() == to.getX() && from.getZ() == to.getZ() && from.getY() == to.getY();
    }
}
