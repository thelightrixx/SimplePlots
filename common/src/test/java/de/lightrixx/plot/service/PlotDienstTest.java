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
import java.util.ArrayList;
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

    @Test
    void haeltIndizesNachUmbenennenMergeUndSpeicherfehlerKonsistent() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var nachbar = id.verschoben(1, 0);
        dienst.claim(id, besitzer, 2);
        dienst.claim(nachbar, besitzer, 2);
        dienst.profilAendern(id, besitzer, profil -> profil.mitName("Alt"));
        dienst.profilAendern(nachbar, besitzer, profil -> profil.mitName("Nachbar"));
        speicher.schreibfehler = true;
        assertThrows(IOException.class, () -> dienst.profilAendern(id, besitzer, profil -> profil.mitName("Neu")));
        assertTrue(dienst.nachName("Neu").isEmpty());
        assertEquals(id, dienst.nachName("Alt").orElseThrow().id());
        speicher.schreibfehler = false;
        dienst.profilAendern(id, besitzer, profil -> profil.mitName("Neu"));
        assertTrue(dienst.nachName("Alt").isEmpty());
        dienst.zusammenlegen(id, nachbar, besitzer, plot -> false, true);
        assertTrue(dienst.nachName("Nachbar").isEmpty());
        assertEquals(dienst.alle(), dienst.vomBesitzer(besitzer));
        assertEquals(1, dienst.anzahlPlots());
        assertEquals(2, dienst.anzahlZellen());
        assertEquals(LIMIT, dienst.claim(id.verschoben(2, 0), besitzer, 2));
        assertThrows(UnsupportedOperationException.class, () -> dienst.vomBesitzer(besitzer).clear());
    }

    @Test
    void laedtZehntausendPlotsUndFindetBesitzNameUndFreieZelle() throws IOException {
        var daten = new ArrayList<Plot>();
        for (int i = 0; i < 10_000; i++) {
            daten.add(Plot.neu(new PlotId(i, 0), new UUID(0, i + 1)).mitProfil(PlotProfil.leer().mitName("Plot " + i)));
        }
        speicher.daten = daten;
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        assertEquals(10_000, dienst.anzahlPlots());
        assertEquals(10_000, dienst.anzahlZellen());
        assertEquals(daten.getLast(), dienst.nachName("PLOT 9999").orElseThrow());
        assertEquals(List.of(daten.getLast()), dienst.vomBesitzer(new UUID(0, 10_000)));
        assertTrue(dienst.finden(dienst.naechsterFreierPlot()).isEmpty());
    }

    @Test
    void veroeffentlichtWaehrendDerSpeicherungKeinenTeilstand() throws Exception {
        var begonnen = new java.util.concurrent.CountDownLatch(1);
        var freigabe = new java.util.concurrent.CountDownLatch(1);
        var dienst = new PlotDienst(new PlotSpeicher() {
            public List<Plot> laden() { return List.of(); }
            public void speichern(List<Plot> plots) throws IOException {
                begonnen.countDown();
                try {
                    if (!freigabe.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IOException("Testfreigabe fehlt");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
        }, PlotKauf.kostenlos());
        var auftraege = new de.lightrixx.plot.storage.SpeicherAuftraege(4);
        try {
            var claim = auftraege.einreichen(() -> dienst.claim(id, besitzer, 1));
            assertTrue(begonnen.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, dienst.anzahlPlots());
            assertTrue(dienst.finden(id).isEmpty());
            assertTrue(dienst.vomBesitzer(besitzer).isEmpty());
            freigabe.countDown();
            assertEquals(CLAIMED, claim.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, dienst.anzahlPlots());
            assertEquals(List.of(Plot.neu(id, besitzer)), dienst.vomBesitzer(besitzer));
        } finally {
            freigabe.countDown();
            assertTrue(auftraege.beenden(java.time.Duration.ofSeconds(5)));
        }
    }

    @Test
    void lehntUeberlappendeZellenUndDoppelteNamenBeimLadenAb() {
        var nachbar = id.verschoben(1, 0);
        var erster = Plot.neu(id, besitzer);
        var zweiter = Plot.neu(nachbar, besitzer);
        speicher.daten = List.of(erster.vereinigtMit(zweiter), zweiter);
        assertThrows(IOException.class, () -> new PlotDienst(speicher, PlotKauf.kostenlos()));
        speicher.daten = List.of(erster.mitProfil(PlotProfil.leer().mitName("Hafen")),
                zweiter.mitProfil(PlotProfil.leer().mitName("HAFEN")));
        assertThrows(IOException.class, () -> new PlotDienst(speicher, PlotKauf.kostenlos()));
        speicher.daten = List.of(erster, erster);
        assertThrows(IOException.class, () -> new PlotDienst(speicher, PlotKauf.kostenlos()));
    }

    @Test
    void prueftAlleZellenEinerZusammenlegungAuchAnNegativenKoordinaten() throws IOException {
        for (var raster : List.of(new PlotRaster(1, 1), new PlotRaster(32, 7), new PlotRaster(1024, 128))) {
            var dienst = new PlotDienst(new TestSpeicher(), PlotKauf.kostenlos());
            var haupt = new PlotId(-1, -1);
            var zellen = List.of(haupt, new PlotId(0, -1), new PlotId(-1, 0), new PlotId(0, 0));
            for (var zelle : zellen) {
                assertEquals(CLAIMED, dienst.claim(zelle, besitzer, 4));
            }
            for (var zelle : zellen.subList(1, zellen.size())) {
                assertEquals(PlotDienst.MergeErgebnis.VEREINIGT,
                        dienst.zusammenlegen(haupt, zelle, besitzer, ziel -> false, true));
            }
            for (int x : new int[]{-raster.rasterweite(), -1, 0, raster.groesse() - 1}) {
                for (int z : new int[]{-raster.rasterweite(), -1, 0, raster.groesse() - 1}) {
                    assertEquals(Optional.of(haupt), dienst.plotBei(raster, x, z), x + ";" + z);
                }
            }
            assertTrue(dienst.plotBei(raster, raster.groesse(), 0).isEmpty());
            assertTrue(dienst.plotBei(raster, 0, raster.groesse()).isEmpty());
            assertEquals(LIMIT, dienst.claim(new PlotId(1, 0), besitzer, 4));
        }
    }

    @Test
    void sperrtZensiertePlotsUndHaeltNamenSowieClaimLimitReserviert() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        dienst.claim(id, besitzer, 1);
        dienst.profilAendern(id, besitzer, profil -> profil.mitName("Museum").mitKategorie(Optional.of(PlotProfil.Kategorie.MUSEUM), 1));
        dienst.rolleAendern(id, besitzer, gast, DENY);
        dienst.zensieren(id, true);
        var zensiert = dienst.finden(id).orElseThrow();
        assertTrue(zensiert.darfBetreten(gast));
        assertFalse(zensiert.darfBauen(besitzer, true));
        assertFalse(zensiert.darfLagerNutzen(besitzer, true));
        assertTrue(dienst.galerie(plot -> true).isEmpty());
        assertTrue(dienst.vomBesitzer(besitzer).isEmpty());
        assertTrue(dienst.nachName("Museum").isEmpty());
        assertEquals(PlotDienst.RollenErgebnis.GESPERRT, dienst.rolleAendern(id, besitzer, gast, TRUST));
        assertEquals(PlotDienst.Aenderung.GESPERRT, dienst.profilAendern(id, besitzer, profil -> profil.mitName("Anders")));
        assertEquals(PlotDienst.Aenderung.GESPERRT, dienst.besucherRecht(id, besitzer, BesucherRecht.DOORS, true));
        assertEquals(PlotDienst.Aenderung.NICHT_GELISTET, dienst.like(id, gast, true));
        assertEquals(LIMIT, dienst.claim(new PlotId(1, 0), besitzer, 1));
        dienst.claim(new PlotId(1, 0), gast, 1);
        assertEquals(PlotDienst.Aenderung.NAME_VERGEBEN,
                dienst.profilAendern(new PlotId(1, 0), gast, profil -> profil.mitName("Museum")));
        var neustart = new PlotDienst(speicher, PlotKauf.kostenlos());
        assertTrue(neustart.finden(id).orElseThrow().zensiert());
        neustart.zensieren(id, false);
        assertEquals(1, neustart.galerie(plot -> true).size());
        assertFalse(neustart.finden(id).orElseThrow().darfBetreten(gast));
    }

    @Test
    void uebertraegtAlleZellenUndEntferntAlteRollenUndLikesErstNachDemSpeichern() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        dienst.claim(id, besitzer, 2);
        dienst.claim(id.verschoben(1, 0), besitzer, 2);
        dienst.zusammenlegen(id, id.verschoben(1, 0), besitzer, ziel -> false, true);
        dienst.profilAendern(id, besitzer, profil -> profil.mitName("Hafen").mitKategorie(Optional.of(PlotProfil.Kategorie.BUILD), 1));
        dienst.like(id, gast, true);
        dienst.rolleAendern(id, besitzer, gast, DENY);
        Plot vorher = dienst.finden(id).orElseThrow();
        assertEquals(PlotDienst.Uebertragung.LIMIT, dienst.uebertragen(id, besitzer, gast, 1, ziel -> false));
        assertEquals(PlotDienst.Uebertragung.GESPERRT, dienst.uebertragen(id, besitzer, gast, 2, ziel -> true));
        speicher.schreibfehler = true;
        assertThrows(IOException.class, () -> dienst.uebertragen(id, besitzer, gast, 2, ziel -> false));
        assertEquals(vorher, dienst.finden(id).orElseThrow());
        assertEquals(List.of(vorher), dienst.vomBesitzer(besitzer));
        assertTrue(dienst.vomBesitzer(gast).isEmpty());
        speicher.schreibfehler = false;
        assertEquals(PlotDienst.Uebertragung.UEBERTRAGEN, dienst.uebertragen(id, besitzer, gast, 2, ziel -> false));
        Plot nachher = dienst.finden(id.verschoben(1, 0)).orElseThrow();
        assertEquals(gast, nachher.besitzer());
        assertTrue(nachher.rechte().gebannt().isEmpty());
        assertTrue(nachher.profil().likes().isEmpty());
        assertEquals("Hafen", nachher.profil().name());
        assertTrue(dienst.vomBesitzer(besitzer).isEmpty());
        assertEquals(nachher, dienst.nachName("Hafen").orElseThrow());
    }

    @Test
    void behaeltBeiZensurUndFreigabeSchreibfehlernAlleIndizesBeiUndFindetFreigegebeneRingeWieder() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        dienst.claim(id, besitzer, 10);
        dienst.naechsterFreierPlot();
        speicher.schreibfehler = true;
        assertThrows(IOException.class, () -> dienst.zensieren(id, true));
        assertFalse(dienst.finden(id).orElseThrow().zensiert());
        assertThrows(IOException.class, () -> dienst.freigeben(id, ziel -> false));
        assertEquals(1, dienst.anzahlZellen());
        speicher.schreibfehler = false;
        dienst.zensieren(id, true);
        assertEquals(PlotDienst.Aenderung.GESPERRT, dienst.freigeben(id, ziel -> false));
        assertEquals(PlotDienst.Uebertragung.GESPERRT, dienst.uebertragen(id, besitzer, gast, 10, ziel -> false));
        dienst.zensieren(id, false);
        assertEquals(PlotDienst.Aenderung.GEAENDERT, dienst.freigeben(id, ziel -> false));
        assertEquals(id, dienst.naechsterFreierPlot());
        assertTrue(dienst.finden(id).isEmpty());
        assertTrue(dienst.vomBesitzer(besitzer).isEmpty());
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
