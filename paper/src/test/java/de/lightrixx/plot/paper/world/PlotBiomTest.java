package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.storage.PlotSpeicher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotBiomTest {
    @Test
    void veraendertKeineBiomzelleUeberClaimgrenzenUndOeffentlicheWegeHinweg() throws IOException {
        var speicher = mock(PlotSpeicher.class);
        when(speicher.laden()).thenReturn(List.of());
        var plots = new PlotDienst(speicher, PlotKauf.kostenlos());
        var besitzer = UUID.randomUUID();
        var id = new PlotId(0, 0);
        plots.claim(id, besitzer, 2);
        plots.claim(new PlotId(1, 0), besitzer, 2);
        var raster = new PlotRaster(17, 3);
        assertEquals(Optional.of(id), PlotBiom.plotFuerSpalte(plots, raster, 12, 4).map(plot -> plot.id()));
        assertTrue(PlotBiom.plotFuerSpalte(plots, raster, 16, 4).isEmpty());
        plots.zusammenlegen(id, new PlotId(1, 0), besitzer, ziel -> false, true);
        assertEquals(Optional.of(id), PlotBiom.plotFuerSpalte(plots, raster, 16, 4).map(plot -> plot.id()));
        plots.aufteilen(id, besitzer, ziel -> false, true);
        assertTrue(PlotBiom.plotFuerSpalte(plots, raster, 16, 4).isEmpty());
        assertTrue(PlotBiom.plotFuerSpalte(plots, raster, -4, 4).isEmpty());
    }
}
