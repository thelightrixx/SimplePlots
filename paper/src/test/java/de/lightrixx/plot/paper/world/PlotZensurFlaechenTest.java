package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.storage.PlotSpeicher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotZensurFlaechenTest {
    @Test
    void decktNurDenClaimOhneUeberlappungOderFremdeWegeAb() throws IOException {
        for (var raster : List.of(new PlotRaster(1, 1), new PlotRaster(17, 5), new PlotRaster(32, 7))) {
            for (var zellen : List.of(Set.of(new PlotId(-1, -1)),
                    Set.of(new PlotId(-1, -1), new PlotId(0, -1), new PlotId(-1, 0)),
                    Set.of(new PlotId(-1, -1), new PlotId(0, -1), new PlotId(-1, 0), new PlotId(0, 0)))) {
                Plot plot = Plot.neu(new PlotId(-1, -1), UUID.randomUUID());
                plot = new Plot(plot.id(), plot.besitzer(), plot.rechte(), plot.profil(), zellen);
                Plot daten = plot;
                var dienst = new PlotDienst(new PlotSpeicher() {
                    public List<Plot> laden() { return List.of(daten); }
                    public void speichern(List<Plot> neu) { fail("Flächenprüfung muss ohne I/O bleiben"); }
                }, PlotKauf.kostenlos());
                var erfasst = new HashSet<Long>();
                for (var teil : PlotZensur.flaechen(plot, raster)) {
                    assertTrue(teil.breite() > 0 && teil.breite() <= 16 && teil.tiefe() > 0 && teil.tiefe() <= 16);
                    assertEquals(teil.x() >> 4, (teil.x() + teil.breite() - 1) >> 4);
                    assertEquals(teil.z() >> 4, (teil.z() + teil.tiefe() - 1) >> 4);
                    for (int x = teil.x(); x < teil.x() + teil.breite(); x++) {
                        for (int z = teil.z(); z < teil.z() + teil.tiefe(); z++) {
                            assertTrue(erfasst.add(((long) x << 32) | (z & 0xffffffffL)), "Doppelter Block " + x + ";" + z);
                            assertEquals(plot.id(), dienst.plotBei(raster, x, z).orElseThrow(), "Fremder Weg " + x + ";" + z);
                        }
                    }
                }
                int weite = raster.rasterweite();
                for (int x = -weite - 1; x <= weite; x++) {
                    for (int z = -weite - 1; z <= weite; z++) {
                        boolean gehoert = dienst.plotBei(raster, x, z).flatMap(dienst::finden).isPresent();
                        assertEquals(gehoert, erfasst.contains(((long) x << 32) | (z & 0xffffffffL)), x + ";" + z);
                    }
                }
            }
        }
    }
}
