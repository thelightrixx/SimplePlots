package de.lightrixx.plot.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlotIdTest {
    @Test
    void laesstKoordinatenNichtAufDieAndereSeiteDesZahlenbereichsUmklappen() {
        var rand = new PlotId(Integer.MAX_VALUE, 0);
        assertThrows(ArithmeticException.class, () -> rand.verschoben(1, 0));
        assertEquals(3, rand.nachbarn().size());
        assertFalse(rand.nachbarn().contains(new PlotId(Integer.MIN_VALUE, 0)));
        var raster = new PlotRaster(32, 7);
        assertThrows(ArithmeticException.class, () -> raster.mitteX(rand));
        assertThrows(ArithmeticException.class, () -> raster.mitteZ(new PlotId(0, Integer.MIN_VALUE)));
    }

    @Test
    void lehntMehrdeutigeUndUnvollstaendigePlotIdsAb() {
        for (String wert : new String[]{"0", "0;", ";0", "0;0;0", "0.5;1", "2147483648;0"}) {
            assertThrows(IllegalArgumentException.class, () -> PlotId.lesen(wert), wert);
        }
        assertEquals(new PlotId(-12, 34), PlotId.lesen("-12;34"));
    }
}
