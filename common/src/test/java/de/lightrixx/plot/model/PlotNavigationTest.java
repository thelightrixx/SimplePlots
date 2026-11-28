package de.lightrixx.plot.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotNavigationTest {
    @Test void persoenlicheLesezeichenUeberlebenNeuladenMitUnicodeNegativenIdsUndWeltbindung() {
        var bookmark = new PlotLesezeichen("Schönes Haus", UUID.randomUUID(), new PlotId(-12, 6), UUID.randomUUID());
        assertEquals(List.of(bookmark), PlotLesezeichen.lesen(PlotLesezeichen.schreiben(List.of(bookmark))));
        assertEquals(List.of(), PlotLesezeichen.lesen(null));
        assertThrows(UnsupportedOperationException.class, () -> PlotLesezeichen.lesen(PlotLesezeichen.schreiben(List.of(bookmark))).clear());
        assertThrows(IllegalArgumentException.class, () -> PlotLesezeichen.schreiben(List.of(bookmark, bookmark)));
        for (String text : List.of("2\n", "1\nkein-eintrag", "1\n!!!|kein-uuid|1;2|kein-uuid", "X".repeat(32769))) assertThrows(IllegalArgumentException.class, () -> PlotLesezeichen.lesen(text));
        assertThrows(IllegalArgumentException.class, () -> new PlotLesezeichen("<red>Test", bookmark.welt(), bookmark.zelle(), bookmark.besitzer()));
    }
    @Test void umrissEnthaeltNurAussenkantenAuchAnInnenwegenUndViererKreuzungen() {
        var r = new PlotRaster(32, 7); PlotId a = new PlotId(-1, -1), b = new PlotId(0, -1), c = new PlotId(-1, 0), d = new PlotId(0, 0);
        assertEquals(128, laenge(PlotUmriss.kanten(Set.of(a), r)));
        assertEquals(206, laenge(PlotUmriss.kanten(Set.of(a, b), r)));
        assertEquals(284, laenge(PlotUmriss.kanten(Set.of(a, b, c), r)));
        var rectangle = PlotUmriss.kanten(Set.of(a, b, c, d), r);
        assertEquals(284, laenge(rectangle));
        assertTrue(rectangle.stream().allMatch(k -> k.x1() == -39 && k.x2() == -39 || k.x1() == 32 && k.x2() == 32 || k.z1() == -39 && k.z2() == -39 || k.z1() == 32 && k.z2() == 32));
    }
    private long laenge(List<PlotUmriss.Kante> kanten) { return kanten.stream().mapToLong(k -> Math.abs(k.x2() - k.x1()) + Math.abs(k.z2() - k.z1())).sum(); }
    @Test void partikelBleibenImSichtbereichUndHaltenDieFesteObergrenzeEin() {
        var kanten = PlotUmriss.kanten(Set.of(new PlotId(0, 0)), new PlotRaster(1024, 128));
        var points = PlotUmriss.punkte(kanten, 0, 0, 32, 1, 10);
        assertEquals(10, points.size());
        assertTrue(points.stream().allMatch(p -> p.x() * p.x() + p.z() * p.z() <= 32 * 32));
        assertTrue(PlotUmriss.punkte(kanten, 512, 512, 32, 1, 128).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PlotUmriss.punkte(kanten, Double.NaN, 0, 32, 1, 128));
    }
}
