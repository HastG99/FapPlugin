package ru.hastg9.fapplugin.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import ru.hastg9.fapplugin.FapPlugin;

import java.util.List;

import static org.mockito.Mockito.*;

public class PlayerListenerTest {

    private FapPlugin plugin;
    private Plugin otherPlugin;
    private PlayerListener listener;
    private MockedStatic<FapPlugin> pluginMock;

    @Before
    public void setUp() {
        plugin = mock(FapPlugin.class);
        otherPlugin = mock(Plugin.class);
        pluginMock = mockStatic(FapPlugin.class);
        pluginMock.when(FapPlugin::getInstance).thenReturn(plugin);
        listener = new PlayerListener();
    }

    @After
    public void tearDown() {
        pluginMock.close();
    }

    @Test
    public void hitKeepsPlayerPassengers() {
        LlamaSpit spit = mock(LlamaSpit.class);
        mark(spit, plugin);
        Player target = mock(Player.class);
        ArmorStand cosmetic = mock(ArmorStand.class);
        when(target.getPassengers()).thenReturn(List.of(cosmetic));

        listener.onProjectileHit(new ProjectileHitEvent(spit, target, null, null));

        verify(cosmetic, never()).remove();
    }

    @Test
    public void blockHitRemovesOnlyOwnText() {
        LlamaSpit spit = mock(LlamaSpit.class);
        mark(spit, plugin);
        TextDisplay text = mock(TextDisplay.class);
        mark(text, plugin);
        TextDisplay otherText = mock(TextDisplay.class);
        mark(otherText, otherPlugin);
        TextDisplay unmarkedText = mock(TextDisplay.class);
        ArmorStand passenger = mock(ArmorStand.class);
        mark(passenger, plugin);
        when(spit.getPassengers()).thenReturn(List.of(text, otherText, unmarkedText, passenger));

        listener.onProjectileHit(new ProjectileHitEvent(spit, null, mock(Block.class), null));

        verify(text).remove();
        verify(otherText, never()).remove();
        verify(unmarkedText, never()).remove();
        verify(passenger, never()).remove();
    }

    @Test
    public void hitIgnoresOtherSpit() {
        LlamaSpit spit = mock(LlamaSpit.class);
        Player target = mock(Player.class);
        ArmorStand cosmetic = mock(ArmorStand.class);
        when(target.getPassengers()).thenReturn(List.of(cosmetic));

        listener.onProjectileHit(new ProjectileHitEvent(spit, target, null, null));
        mark(spit, otherPlugin);
        listener.onProjectileHit(new ProjectileHitEvent(spit, target, null, null));

        verify(cosmetic, never()).remove();
        verify(spit, never()).getPassengers();
    }

    @Test
    public void removingSpitKeepsForeignPassengers() {
        LlamaSpit spit = mock(LlamaSpit.class);
        mark(spit, plugin);
        TextDisplay text = mock(TextDisplay.class);
        mark(text, plugin);
        TextDisplay otherText = mock(TextDisplay.class);
        mark(otherText, otherPlugin);
        ArmorStand passenger = mock(ArmorStand.class);
        when(spit.getPassengers()).thenReturn(List.of(text, otherText, passenger));

        EntityRemoveEvent event = mock(EntityRemoveEvent.class);
        when(event.getEntity()).thenReturn(spit);
        listener.onEntityRemove(event);

        verify(text).remove();
        verify(otherText, never()).remove();
        verify(passenger, never()).remove();
    }

    @Test
    public void removingOtherSpitKeepsItsPassengers() {
        LlamaSpit spit = mock(LlamaSpit.class);
        TextDisplay text = mock(TextDisplay.class);
        when(spit.getPassengers()).thenReturn(List.of(text));
        EntityRemoveEvent event = mock(EntityRemoveEvent.class);
        when(event.getEntity()).thenReturn(spit);

        listener.onEntityRemove(event);
        mark(spit, otherPlugin);
        listener.onEntityRemove(event);

        verify(text, never()).remove();
        verify(spit, never()).getPassengers();
    }

    @Test
    public void otherSpitKeepsItsDamage() {
        LlamaSpit spit = mock(LlamaSpit.class);
        mark(spit, otherPlugin);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(spit);
        when(event.getEntity()).thenReturn(mock(Player.class));

        listener.onAttack(event);

        verify(event, never()).setDamage(anyDouble());
    }

    @Test
    public void ownSpitStillDoesNoDamageWithSharedMetadataKey() {
        LlamaSpit spit = mock(LlamaSpit.class);
        when(spit.hasMetadata("cm_shoot")).thenReturn(true);
        when(spit.getMetadata("cm_shoot")).thenReturn(List.of(
                new FixedMetadataValue(otherPlugin, true),
                new FixedMetadataValue(plugin, true)
        ));
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(spit);
        when(event.getEntity()).thenReturn(mock(Player.class));

        listener.onAttack(event);

        verify(event).setDamage(0.0);
    }

    private void mark(Entity entity, Plugin owner) {
        when(entity.hasMetadata("cm_shoot")).thenReturn(true);
        when(entity.getMetadata("cm_shoot")).thenReturn(List.of(new FixedMetadataValue(owner, true)));
    }
}
