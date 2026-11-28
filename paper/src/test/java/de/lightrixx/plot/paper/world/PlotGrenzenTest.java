package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.*;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotGrenzenTest {
    @Test void anzeigeIstPrivatWirdBeiRechteverlustBeendetUndHatNurEinenTaktgeber() {
        var plugin = mock(Plugin.class); var server = mock(Server.class); var scheduler = mock(BukkitScheduler.class); var task = mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
        var tick = ArgumentCaptor.forClass(Runnable.class);
        var grenzen = new PlotGrenzen(plugin);
        verify(scheduler).runTaskTimer(eq(plugin), tick.capture(), eq(10L), eq(10L));
        var player = mock(Player.class); var world = mock(World.class); var config = mock(PlotKonfiguration.class); var plots = mock(PlotDienst.class);
        UUID id = UUID.randomUUID(), worldId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.getWorld()).thenReturn(world); when(world.getUID()).thenReturn(worldId);
        when(player.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(server.getPlayer(id)).thenReturn(player); when(player.isOnline()).thenReturn(true); when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 0, 70, 0));
        when(config.raster()).thenReturn(new PlotRaster(32, 7)); when(config.zahl("general.navigation.grenzen-max-sekunden")).thenReturn(60);
        when(config.zahl("general.navigation.grenzen-max-spieler")).thenReturn(32); when(config.zahl("general.navigation.grenzen-reichweite")).thenReturn(32); when(config.zahl("general.navigation.grenzen-punktabstand")).thenReturn(4);
        var plot = Plot.neu(new PlotId(0, 0), id); when(plots.finden(plot.id())).thenReturn(Optional.of(plot));
        assertTrue(grenzen.zeigen(player, plot, plots, config, 15)); assertTrue(grenzen.zeigen(player, plot, plots, config, 30));
        tick.getValue().run(); verify(player, atLeastOnce()).spawnParticle(eq(Particle.DUST), anyDouble(), anyDouble(), anyDouble(), eq(1), eq(0d), eq(0d), eq(0d), eq(0d), any(Particle.DustOptions.class));
        clearInvocations(player); when(player.hasPermission("simpleplots.border")).thenReturn(false); tick.getValue().run();
        verify(player, never()).spawnParticle(any(Particle.class), anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Particle.DustOptions.class));
        grenzen.beenden(); verify(task).cancel();
        verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }
}
