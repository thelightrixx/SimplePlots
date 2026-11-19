package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotWegrandTest {
    @Test
    void verteiltGrosseAenderungenAufMehrereTicksUndFasstDoppelteChunksZusammen() {
        var plugin = mock(Plugin.class);
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        var ticks = new ArrayDeque<Runnable>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(aufruf -> {
            ticks.add(aufruf.getArgument(1));
            return null;
        });
        var rand = spy(new PlotWegrand(mock(PlotDienst.class)));
        var raster = new PlotRaster(32, 7);
        doNothing().when(rand).aktualisieren(any(), eq(raster));
        var chunks = new ArrayList<Chunk>();
        for (int i = 0; i < 20; i++) {
            var chunk = mock(Chunk.class);
            when(chunk.getX()).thenReturn(i);
            when(chunk.isLoaded()).thenReturn(true);
            chunks.add(chunk);
        }
        rand.vormerken(plugin, chunks, raster);
        rand.vormerken(plugin, List.of(chunks.getFirst()), raster);
        verify(rand, never()).aktualisieren(any(), any());
        assertEquals(1, ticks.size());
        ticks.remove().run();
        verify(rand, times(8)).aktualisieren(any(), eq(raster));
        ticks.remove().run();
        verify(rand, times(16)).aktualisieren(any(), eq(raster));
        ticks.remove().run();
        verify(rand, times(20)).aktualisieren(any(), eq(raster));
        assertTrue(ticks.isEmpty());
    }
}
