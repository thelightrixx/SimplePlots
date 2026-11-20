package de.lightrixx.plot.service;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.BauWettbewerb;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.storage.PlotSpeicher;
import de.lightrixx.plot.storage.WettbewerbSpeicher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.lightrixx.plot.service.WettbewerbDienst.Ergebnis.*;
import static org.junit.jupiter.api.Assertions.*;

class WettbewerbDienstTest {
    private final PlotId erster = new PlotId(0, 0);
    private final PlotId zweiter = new PlotId(1, 0);
    private final PlotId dritter = new PlotId(2, 0);
    private final UUID anna = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();
    private final UUID gast = UUID.randomUUID();
    private final TestSpeicher wetter = new TestSpeicher();

    @Test
    void sperrtBeitraegeBisZumSchlussUndErlaubtEineAenderbareStimme() throws IOException {
        var plots = plots();
        var dienst = new WettbewerbDienst(wetter, plots);
        assertEquals(FALSCHE_PHASE, dienst.einreichen(erster, anna));
        assertEquals(ERFOLG, dienst.starten("Hafen"));
        assertEquals(KEINE_BEITRAEGE, dienst.abstimmungOeffnen());
        assertEquals(FREMDER_PLOT, dienst.einreichen(erster, ben));
        assertEquals(ERFOLG, dienst.einreichen(erster, anna));
        assertEquals(BEREITS_EINGEREICHT, dienst.einreichen(dritter, anna));
        assertTrue(dienst.gesperrt(erster));
        assertEquals(ERFOLG, dienst.einreichen(zweiter, ben));
        assertEquals(FALSCHE_PHASE, dienst.abstimmen(erster, gast));
        assertEquals(ERFOLG, dienst.abstimmungOeffnen());
        var neustart = new WettbewerbDienst(wetter, plots);
        assertTrue(neustart.gesperrt(erster));
        assertEquals(FALSCHE_PHASE, neustart.zurueckziehen(anna));
        assertEquals(EIGENER_PLOT, neustart.abstimmen(erster, anna));
        assertEquals(ERFOLG, neustart.abstimmen(erster, gast));
        assertEquals(BEREITS_GEWAEHLT, neustart.abstimmen(erster, gast));
        assertEquals(ERFOLG, neustart.abstimmen(zweiter, gast));
        assertEquals(ERFOLG, neustart.beenden());
        assertFalse(neustart.hatGesperrtePlots());
        assertEquals(List.of(new BauWettbewerb.Platzierung(zweiter, 1), new BauWettbewerb.Platzierung(erster, 0)),
                neustart.aktuell().orElseThrow().ergebnisse());
    }

    @Test
    void entsperrtBeiRueckzugUndLaesstBeiSpeicherfehlerAllesUnberuehrt() throws IOException {
        var dienst = new WettbewerbDienst(wetter, plots());
        dienst.starten("Museum");
        dienst.einreichen(erster, anna);
        wetter.schreibfehler = true;
        assertThrows(IOException.class, () -> dienst.zurueckziehen(anna));
        assertTrue(dienst.gesperrt(erster));
        wetter.schreibfehler = false;
        assertEquals(ERFOLG, dienst.zurueckziehen(anna));
        assertFalse(dienst.gesperrt(erster));
        dienst.einreichen(dritter, anna);
        assertEquals(ERFOLG, dienst.abbrechen());
        assertFalse(dienst.gesperrt(dritter));
        assertTrue(dienst.aktuell().isEmpty());
    }

    @Test
    void behaeltPhasenUndStimmenBeiJederFehlgeschlagenenSpeicherung() throws IOException {
        var dienst = new WettbewerbDienst(wetter, plots());
        dienst.starten("Hafen");
        dienst.einreichen(erster, anna);
        var bauen = dienst.aktuell();
        wetter.schreibfehler = true;
        assertThrows(IOException.class, dienst::abstimmungOeffnen);
        assertEquals(bauen, dienst.aktuell());
        wetter.schreibfehler = false;
        dienst.abstimmungOeffnen();
        var abstimmen = dienst.aktuell();
        wetter.schreibfehler = true;
        assertThrows(IOException.class, () -> dienst.abstimmen(erster, gast));
        assertEquals(abstimmen, dienst.aktuell());
        assertThrows(IOException.class, dienst::beenden);
        assertThrows(IOException.class, dienst::abbrechen);
        assertEquals(abstimmen, dienst.aktuell());
        assertEquals(abstimmen, new WettbewerbDienst(wetter, plots()).aktuell());
    }

    @Test
    void lehntAktiveBeitraegeMitFalschemBesitzerAbUndBewahrtAbgeschlosseneHistorie() throws IOException {
        wetter.daten = Optional.of(new BauWettbewerb("Hafen", BauWettbewerb.Phase.BAUEN,
                java.util.Map.of(erster, ben), java.util.Map.of()));
        assertThrows(IOException.class, () -> new WettbewerbDienst(wetter, plots()));
        wetter.daten = Optional.of(new BauWettbewerb("Hafen", BauWettbewerb.Phase.BEENDET,
                java.util.Map.of(erster, ben), java.util.Map.of()));
        var dienst = new WettbewerbDienst(wetter, plots());
        assertEquals(wetter.daten, dienst.aktuell());
        assertFalse(dienst.gesperrt(erster));
    }

    private PlotDienst plots() throws IOException {
        return new PlotDienst(new PlotSpeicher() {
            @Override
            public List<Plot> laden() {
                return List.of(Plot.neu(erster, anna), Plot.neu(zweiter, ben), Plot.neu(dritter, anna));
            }

            @Override
            public void speichern(List<Plot> plots) {
                throw new UnsupportedOperationException("Test ändert nur den Wettbewerb.");
            }
        }, PlotKauf.kostenlos());
    }

    private static final class TestSpeicher implements WettbewerbSpeicher {
        private Optional<BauWettbewerb> daten = Optional.empty();
        private boolean schreibfehler;

        @Override
        public Optional<BauWettbewerb> laden() {
            return daten;
        }

        @Override
        public void speichern(Optional<BauWettbewerb> wettbewerb) throws IOException {
            if (schreibfehler) {
                throw new IOException("Test-Schreibfehler");
            }
            daten = wettbewerb;
        }
    }
}
