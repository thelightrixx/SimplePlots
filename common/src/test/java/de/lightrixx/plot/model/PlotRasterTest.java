package de.lightrixx.plot.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotRasterTest {
    private final PlotRaster raster = new PlotRaster(32, 7);

    @Test
    void ordnetPlotUndWegAnBeidenSeitenDesNullpunktsZu() {
        assertEquals(Optional.of(new PlotId(0, 0)), raster.plotBei(31, 31));
        assertTrue(raster.plotBei(32, 31).isEmpty());
        assertTrue(raster.plotBei(31, 38).isEmpty());
        assertEquals(Optional.of(new PlotId(1, 1)), raster.plotBei(39, 39));
        assertEquals(Optional.of(new PlotId(-1, -1)), raster.plotBei(-8, -8));
        assertEquals(Optional.of(new PlotId(-1, -1)), raster.plotBei(-39, -39));
        assertTrue(raster.plotBei(-7, -8).isEmpty());
        assertTrue(raster.plotBei(-8, -40).isEmpty());
    }

    @Test
    void setztZieleInDiePlotmitteOderAufDenNordweg() {
        var id = new PlotId(-3, 2);
        assertEquals(Optional.of(id), raster.plotBei(raster.mitteX(id), raster.mitteZ(id)));
        assertTrue(raster.istWeg(raster.mitteX(id), raster.nordwegZ(id)));
        assertEquals(Optional.of(id), raster.plotBei(raster.mitteX(id), raster.nordwegZ(id) + 1));
        assertTrue(raster.istWeg(raster.spawnAchse(), raster.spawnAchse()));
    }

    @Test
    void validiertRasterMitPositiverPlotUndWegbreite() {
        assertThrows(IllegalArgumentException.class, () -> new PlotRaster(32, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlotRaster(-1, 7));
        assertThrows(IllegalArgumentException.class, () -> new PlotRaster(1025, 7));
        var eng = new PlotRaster(1, 1);
        assertEquals(Optional.of(new PlotId(0, 0)), eng.plotBei(0, 0));
        assertTrue(eng.istWeg(eng.spawnAchse(), eng.spawnAchse()));
    }
}
