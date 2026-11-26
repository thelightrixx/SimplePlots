package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
    void repariertFehlendeAutomatischeRaenderUndRespektiertBewusstEntfernteRaender() {
        var plots = mock(PlotDienst.class);
        var raster = new PlotRaster(32, 7);
        var chunk = mock(Chunk.class);
        var daten = mock(PersistentDataContainer.class);
        var oben = mock(Block.class);
        var boden = mock(Block.class);
        var key = new NamespacedKey("simpleplots", "wegrand");
        var markierung = new byte[256];
        markierung[8] = 2;
        when(chunk.getX()).thenReturn(2);
        when(chunk.getPersistentDataContainer()).thenReturn(daten);
        when(daten.get(key, PersistentDataType.BYTE_ARRAY)).thenReturn(markierung);
        when(plots.istWegrand(raster, 32, 8)).thenReturn(true);
        when(chunk.getBlock(0, 64, 8)).thenReturn(oben);
        when(chunk.getBlock(0, 63, 8)).thenReturn(boden);
        when(oben.getType()).thenReturn(Material.AIR);
        when(oben.isEmpty()).thenReturn(true);
        when(boden.getType()).thenReturn(Material.STONE_BRICKS);
        var rand = new PlotWegrand(plots);
        rand.aktualisieren(chunk, raster);
        verify(oben).setType(Material.STONE_BRICK_SLAB, false);
        when(oben.getX()).thenReturn(32);
        when(oben.getZ()).thenReturn(8);
        when(oben.getChunk()).thenReturn(chunk);
        rand.markierungEntfernen(oben);
        clearInvocations(oben);
        rand.aktualisieren(chunk, raster);
        verify(oben, never()).setType(any(), anyBoolean());
    }

    @Test
    void entferntBeimMergeNurAutomatischeSlabsUndStelltNachTrennungDenWegebodenWiederHer() {
        var plots = mock(PlotDienst.class);
        var raster = new PlotRaster(32, 7);
        var chunk = mock(Chunk.class);
        var daten = mock(PersistentDataContainer.class);
        var oben = mock(Block.class);
        var boden = mock(Block.class);
        var slab = mock(Slab.class);
        var markierung = new byte[256];
        markierung[0] = 2;
        when(chunk.getZ()).thenReturn(2);
        when(chunk.getPersistentDataContainer()).thenReturn(daten);
        when(daten.get(new NamespacedKey("simpleplots", "wegrand"), PersistentDataType.BYTE_ARRAY)).thenReturn(markierung);
        when(chunk.getBlock(0, 64, 0)).thenReturn(oben);
        when(oben.getType()).thenReturn(Material.STONE_BRICK_SLAB);
        when(oben.getBlockData()).thenReturn(slab);
        when(slab.getType()).thenReturn(Slab.Type.BOTTOM);
        var rand = new PlotWegrand(plots);
        rand.aktualisieren(chunk, raster);
        verify(oben).setType(Material.AIR, false);
        var innen = new byte[256];
        innen[0] = 1;
        when(daten.get(new NamespacedKey("simpleplots", "innenweg"), PersistentDataType.BYTE_ARRAY)).thenReturn(innen);
        when(chunk.getBlock(0, 63, 0)).thenReturn(boden);
        when(boden.getType()).thenReturn(Material.GRASS_BLOCK);
        rand.aktualisieren(chunk, raster);
        verify(boden).setType(Material.STONE_BRICKS, false);
        when(boden.getType()).thenReturn(Material.DIAMOND_BLOCK);
        innen[0] = 1;
        clearInvocations(boden);
        rand.aktualisieren(chunk, raster);
        verify(boden, never()).setType(any(), anyBoolean());
    }

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
