package de.lightrixx.plot.service;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.model.PlotRechte.BesucherRecht;
import de.lightrixx.plot.storage.PlotSpeicher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static de.lightrixx.plot.model.Plot.RollenAktion.DENY;
import static de.lightrixx.plot.model.Plot.RollenAktion.TRUST;
import static de.lightrixx.plot.model.Plot.RollenAktion.UNDENY;
import static de.lightrixx.plot.model.Plot.RollenAktion.UNTRUST;
import static de.lightrixx.plot.service.PlotDienst.ClaimErgebnis.*;
import static de.lightrixx.plot.service.PlotDienst.RollenErgebnis.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotDienstTest {
    private final PlotId id = new PlotId(0, 0);
    private final UUID besitzer = UUID.randomUUID();
    private final UUID gast = UUID.randomUUID();
    private final TestSpeicher speicher = new TestSpeicher();

    @Test
    void reserviertNurFreiePlotsInnerhalbDesLimits() throws IOException {
        var aufrufe = new AtomicInteger();
        var dienst = new PlotDienst(speicher, (spieler, plot, speichern) -> {
            aufrufe.incrementAndGet();
            speichern.speichern();
            return true;
        });
        assertEquals(id, dienst.naechsterFreierPlot());
        assertEquals(CLAIMED, dienst.claim(id, besitzer, 1));
        PlotId frei = dienst.naechsterFreierPlot();
        assertNotEquals(id, frei);
        assertEquals(BELEGT, dienst.claim(id, gast, 1));
        assertEquals(LIMIT, dienst.claim(frei, besitzer, 1));
        assertEquals(1, aufrufe.get());
        assertEquals(List.of(Plot.neu(id, besitzer)), dienst.vomBesitzer(besitzer));
    }

    @Test
    void stelltBaurechtUmUndSpeichertDenBann() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        assertEquals(CLAIMED, dienst.claim(id, besitzer, 3));
        var plot = dienst.finden(id).orElseThrow();
        assertTrue(plot.darfBauen(besitzer));
        assertFalse(plot.darfBauen(gast));
        assertTrue(plot.darfBetreten(gast));

        assertEquals(FREMDER_PLOT, dienst.rolleAendern(id, gast, besitzer, TRUST));
        assertEquals(BESITZER, dienst.rolleAendern(id, besitzer, besitzer, DENY));
        assertEquals(GEAENDERT, dienst.rolleAendern(id, besitzer, gast, TRUST));
        assertEquals(UNVERAENDERT, dienst.rolleAendern(id, besitzer, gast, TRUST));
        assertTrue(dienst.finden(id).orElseThrow().darfBauen(gast));
        assertEquals(GEAENDERT, dienst.rolleAendern(id, besitzer, gast, DENY));

        var nachNeustart = new PlotDienst(speicher, PlotKauf.kostenlos());
        var gesperrt = nachNeustart.finden(id).orElseThrow();
        assertFalse(gesperrt.darfBauen(gast));
        assertFalse(gesperrt.darfBetreten(gast));
        assertEquals(GEAENDERT, nachNeustart.rolleAendern(id, besitzer, gast, UNDENY));
        assertTrue(nachNeustart.finden(id).orElseThrow().darfBetreten(gast));
        assertFalse(nachNeustart.finden(id).orElseThrow().darfBauen(gast));
        assertEquals(GEAENDERT, nachNeustart.rolleAendern(id, besitzer, gast, TRUST));
        assertEquals(GEAENDERT, nachNeustart.rolleAendern(id, besitzer, gast, UNTRUST));
        assertFalse(nachNeustart.finden(id).orElseThrow().darfBauen(gast));
    }

    @Test
    void belaesstDenSpeicherBeiAbgelehntemKauf() throws IOException {
        var dienst = new PlotDienst(speicher, (spieler, plot, speichern) -> false);
        assertEquals(ABGELEHNT, dienst.claim(id, besitzer, 1));
        assertTrue(dienst.finden(id).isEmpty());
        assertTrue(speicher.laden().isEmpty());
    }

    @Test
    void behaeltDenBisherigenZustandBeiSpeicherfehlern() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        speicher.schreibfehler = true;
        assertThrows(IOException.class, () -> dienst.claim(id, besitzer, 1));
        assertTrue(dienst.finden(id).isEmpty());

        speicher.schreibfehler = false;
        assertEquals(CLAIMED, dienst.claim(id, besitzer, 1));
        speicher.schreibfehler = true;
        assertThrows(IOException.class, () -> dienst.rolleAendern(id, besitzer, gast, TRUST));
        assertFalse(dienst.finden(id).orElseThrow().darfBauen(gast));
        assertEquals(List.of(Plot.neu(id, besitzer)), speicher.laden());
    }

    @Test
    void verbindetInnenwegeUndBewahrtRasterIdsUndClaimLimit() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var west = new PlotId(-1, 0);
        var raster = new PlotRaster(32, 7);
        assertEquals(CLAIMED, dienst.claim(id, besitzer, 2));
        assertEquals(CLAIMED, dienst.claim(west, besitzer, 2));
        assertTrue(dienst.plotBei(raster, -1, 15).isEmpty());
        assertEquals(PlotDienst.MergeErgebnis.BESTAETIGEN, dienst.zusammenlegen(id, west, besitzer, ziel -> false, false));
        assertTrue(dienst.plotBei(raster, -1, 15).isEmpty());
        assertEquals(PlotDienst.MergeErgebnis.VEREINIGT, dienst.zusammenlegen(id, west, besitzer, ziel -> false, true));
        var geladen = new PlotDienst(speicher, PlotKauf.kostenlos());
        assertEquals(id, geladen.finden(west).orElseThrow().id());
        assertEquals(Optional.of(id), geladen.plotBei(raster, -1, 15));
        assertTrue(geladen.plotBei(raster, -1, -1).isEmpty());
        assertEquals(west, geladen.finden(id).orElseThrow().nordrand());
        assertEquals(BELEGT, geladen.claim(west, gast, 2));
        assertEquals(LIMIT, geladen.claim(geladen.naechsterFreierPlot(), besitzer, 2));
    }

    @Test
    void umrandetBeideWegseitenUndDieEcken() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var raster = new PlotRaster(32, 7);
        assertTrue(dienst.istWegrand(raster, 32, 15));
        assertTrue(dienst.istWegrand(raster, 38, 15));
        assertTrue(dienst.istWegrand(raster, 15, 32));
        assertTrue(dienst.istWegrand(raster, 15, 38));
        assertTrue(dienst.istWegrand(raster, -1, -1));
        assertFalse(dienst.istWegrand(raster, -1, -4));
        assertFalse(dienst.istWegrand(raster, 35, 15));
        assertFalse(dienst.istWegrand(raster, 31, 15));
        assertTrue(dienst.istWegrand(new PlotRaster(1, 1), -1, -1));
    }

    @Test
    void folgtDerAussengrenzeZusammengelegterPlots() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var ost = id.verschoben(1, 0);
        var raster = new PlotRaster(32, 7);
        dienst.claim(id, besitzer, 2);
        dienst.claim(ost, besitzer, 2);
        assertTrue(dienst.istWegrand(raster, 32, 15));
        assertFalse(dienst.istWegrand(raster, 35, 32));

        assertEquals(PlotDienst.MergeErgebnis.VEREINIGT, dienst.zusammenlegen(id, ost, besitzer, ziel -> false, true));
        assertFalse(dienst.istWegrand(raster, 32, 15));
        assertFalse(dienst.istWegrand(raster, 38, 15));
        assertTrue(dienst.istWegrand(raster, 35, 32));
        assertTrue(dienst.istWegrand(raster, 35, -1));
        assertFalse(dienst.istWegrand(raster, 35, 35));
    }

    @Test
    void vereinigtNurEigeneNachbarzellenUndKreuzungenErstMitVierZellen() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var ost = id.verschoben(1, 0);
        var sued = id.verschoben(0, 1);
        var ecke = id.verschoben(1, 1);
        var raster = new PlotRaster(32, 7);
        for (PlotId zelle : List.of(id, ost, sued, ecke)) {
            assertEquals(CLAIMED, dienst.claim(zelle, besitzer, 4));
        }
        assertEquals(PlotDienst.MergeErgebnis.FREMDER_PLOT, dienst.zusammenlegen(id, ost, gast, ziel -> false, true));
        assertEquals(PlotDienst.MergeErgebnis.KEIN_NACHBAR, dienst.zusammenlegen(id, ecke, besitzer, ziel -> false, true));
        assertEquals(PlotDienst.MergeErgebnis.GESPERRT, dienst.zusammenlegen(id, ost, besitzer, ost::equals, true));
        assertEquals(PlotDienst.MergeErgebnis.VEREINIGT, dienst.zusammenlegen(id, ost, besitzer, ziel -> false, true));
        assertEquals(PlotDienst.MergeErgebnis.VEREINIGT, dienst.zusammenlegen(id, sued, besitzer, ziel -> false, true));
        assertTrue(dienst.plotBei(raster, 32, 32).isEmpty());
        assertEquals(PlotDienst.MergeErgebnis.VEREINIGT, dienst.zusammenlegen(id, ecke, besitzer, ziel -> false, true));
        assertEquals(Optional.of(id), dienst.plotBei(raster, 32, 32));
        assertTrue(dienst.plotBei(raster, 71, 32).isEmpty());
    }

    @Test
    void erteiltBeiEinemMergeNurGemeinsameRechteUndUebernimmtAlleBanns() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var nachbar = id.verschoben(0, 1);
        var weiterer = UUID.randomUUID();
        dienst.claim(id, besitzer, 3);
        dienst.claim(nachbar, besitzer, 3);
        dienst.rolleAendern(id, besitzer, gast, TRUST);
        dienst.rolleAendern(nachbar, besitzer, gast, DENY);
        dienst.rolleAendern(id, besitzer, weiterer, Plot.RollenAktion.STORAGE);
        dienst.rolleAendern(nachbar, besitzer, weiterer, Plot.RollenAktion.STORAGE);
        dienst.besucherRecht(id, besitzer, BesucherRecht.DOORS, true);
        speicher.schreibfehler = true;
        assertThrows(IOException.class, () -> dienst.zusammenlegen(id, nachbar, besitzer, ziel -> false, true));
        assertEquals(nachbar, dienst.finden(nachbar).orElseThrow().id());
        speicher.schreibfehler = false;
        dienst.zusammenlegen(id, nachbar, besitzer, ziel -> false, true);
        var plot = dienst.finden(nachbar).orElseThrow();
        assertEquals(Set.of(gast), plot.rechte().gebannt());
        assertTrue(plot.rechte().trusted().isEmpty());
        assertEquals(Set.of(weiterer), plot.rechte().lager());
        assertTrue(plot.rechte().besucher().isEmpty());
    }

    @Test
    void bautMitBauhelfernOnlineUndErlaubtSeparatenLagerzugriff() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        dienst.claim(id, besitzer, 1);
        assertEquals(GEAENDERT, dienst.rolleAendern(id, besitzer, gast, Plot.RollenAktion.ADD));
        var plot = dienst.finden(id).orElseThrow();
        assertFalse(plot.darfBauen(gast, false));
        assertTrue(plot.darfBauen(gast, true));
        assertFalse(plot.darfLagerNutzen(gast, false));
        assertEquals(GEAENDERT, dienst.rolleAendern(id, besitzer, gast, Plot.RollenAktion.STORAGE));
        assertTrue(dienst.finden(id).orElseThrow().darfLagerNutzen(gast, false));
        assertFalse(dienst.finden(id).orElseThrow().darfBauen(gast, false));
        dienst.rolleAendern(id, besitzer, gast, DENY);
        assertEquals(GEBANNT, dienst.rolleAendern(id, besitzer, gast, Plot.RollenAktion.ADD));
        assertFalse(dienst.finden(id).orElseThrow().darfLagerNutzen(gast, true));
    }

    @Test
    void listetOptInProfileUndZaehltEinLikeProGast() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var zweiter = id.verschoben(1, 0);
        dienst.claim(id, besitzer, 2);
        dienst.claim(zweiter, besitzer, 2);
        assertEquals(PlotDienst.Aenderung.GEAENDERT, dienst.profilAendern(id, besitzer, profil -> profil.mitName("Großer Hafen")));
        assertEquals(PlotDienst.Aenderung.NAME_VERGEBEN,
                dienst.profilAendern(zweiter, besitzer, profil -> profil.mitName("großer hafen")));
        assertEquals(id, dienst.nachName("GROßER HAFEN").orElseThrow().id());
        assertTrue(dienst.galerie(gast).isEmpty());
        assertEquals(PlotDienst.Aenderung.NICHT_GELISTET, dienst.like(id, gast, true));
        dienst.profilAendern(id, besitzer, profil -> profil.mitKategorie(Optional.of(PlotProfil.Kategorie.BUILD), 100));
        assertEquals(PlotDienst.Aenderung.EIGENER_PLOT, dienst.like(id, besitzer, true));
        assertEquals(PlotDienst.Aenderung.GEAENDERT, dienst.like(id, gast, true));
        assertEquals(PlotDienst.Aenderung.UNVERAENDERT, dienst.like(id, gast, true));
        assertEquals(Set.of(gast), dienst.galerie(gast).getFirst().profil().likes());
        assertEquals(PlotDienst.Aenderung.GEAENDERT, dienst.like(id, gast, false));
        dienst.rolleAendern(id, besitzer, gast, DENY);
        assertTrue(dienst.galerie(gast).isEmpty());
        assertEquals(List.of(id), dienst.galerie(plot -> true).stream().map(Plot::id).toList());
    }

    private static final class TestSpeicher implements PlotSpeicher {
        private List<Plot> daten = List.of();
        private boolean schreibfehler;

        @Override
        public List<Plot> laden() {
            return daten;
        }

        @Override
        public void speichern(List<Plot> plots) throws IOException {
            if (schreibfehler) {
                throw new IOException("Test-Schreibfehler");
            }
            daten = List.copyOf(plots);
        }
    }
}
