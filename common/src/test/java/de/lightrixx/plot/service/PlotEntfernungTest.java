package de.lightrixx.plot.service;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotEntfernung;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.storage.PlotSpeicher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotEntfernungTest {
    private final PlotId id = new PlotId(0, 0);
    private final UUID besitzer = UUID.randomUUID();
    private final Speicher speicher = new Speicher();

    @Test
    void entferntGesamtenMergeMitIndizesUndSperrtAuchDieInnenwegeNachNeustart() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var raster = new PlotRaster(32, 7);
        var nachbar = id.verschoben(1, 0);
        dienst.claim(id, besitzer, 2); dienst.claim(nachbar, besitzer, 2);
        dienst.profilAendern(id, besitzer, profil -> profil.mitName("Hafen").mitKategorie(Optional.of(PlotProfil.Kategorie.BUILD), 100));
        dienst.zusammenlegen(id, nachbar, besitzer, ziel -> false, true);
        var vorher = dienst.finden(id);
        speicher.vorSpeichern = () -> {
            assertEquals(vorher, dienst.finden(nachbar));
            assertTrue(dienst.entfernungen().isEmpty());
            assertEquals(vorher, dienst.nachName("Hafen"));
        };
        assertTrue(dienst.entfernen(nachbar, vorher, ziel -> false));
        speicher.vorSpeichern = () -> { };
        var neu = new PlotDienst(speicher, PlotKauf.kostenlos());
        assertEquals(0, neu.anzahlPlots()); assertEquals(0, neu.anzahlZellen());
        assertTrue(neu.vomBesitzer(besitzer).isEmpty()); assertTrue(neu.nachName("Hafen").isEmpty());
        assertTrue(neu.galerie(besitzer).isEmpty());
        assertEquals(neu.entfernung(id), neu.entfernung(nachbar));
        assertTrue(neu.plotBei(raster, 5, 5).isEmpty());
        assertTrue(neu.entfernungBei(raster, 35, 5).isPresent());
        assertTrue(neu.entfernungBei(raster, 5, 35).isEmpty());
        assertEquals(PlotDienst.ClaimErgebnis.BELEGT, neu.claim(nachbar, besitzer, 3));
        assertNotEquals(id, neu.naechsterFreierPlot());
    }

    @Test
    void behaeltBeiSchreibfehlernBesitzIndizesUndEntfernungszustand() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        dienst.claim(id, besitzer, 1);
        var vorher = dienst.finden(id);
        speicher.fehler = true;
        assertThrows(IOException.class, () -> dienst.entfernen(id, vorher, ziel -> false));
        assertEquals(vorher, dienst.finden(id)); assertTrue(dienst.entfernungen().isEmpty());
        assertEquals(vorher, new PlotDienst(speicher, PlotKauf.kostenlos()).finden(id));
    }

    @Test
    void stelltErstNachGespeichertemAbschlussWiederFreieZellenBereit() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        assertTrue(dienst.entfernen(id, Optional.empty(), ziel -> false));
        var entfernen = dienst.entfernung(id).orElseThrow();
        assertFalse(dienst.wiederhergestellt(entfernen));
        assertTrue(dienst.entfernungsphase(entfernen, PlotEntfernung.Phase.ENTFERNT));
        var entfernt = dienst.entfernung(id).orElseThrow();
        assertTrue(dienst.entfernungsphase(entfernt, PlotEntfernung.Phase.WIEDERHERSTELLEN));
        var restore = dienst.entfernung(id).orElseThrow();
        speicher.fehler = true;
        assertThrows(IOException.class, () -> dienst.wiederhergestellt(restore));
        assertEquals(PlotDienst.ClaimErgebnis.BELEGT, dienst.claim(id, besitzer, 1));
        var neustart = new PlotDienst(speicher, PlotKauf.kostenlos());
        assertEquals(restore, neustart.entfernung(id).orElseThrow());
        speicher.fehler = false;
        assertTrue(neustart.wiederhergestellt(restore));
        assertEquals(id, neustart.naechsterFreierPlot());
        assertEquals(PlotDienst.ClaimErgebnis.CLAIMED, neustart.claim(id, besitzer, 1));
        assertEquals(Plot.neu(id, besitzer), neustart.finden(id).orElseThrow());
    }

    @Test
    void weistVeralteteErwartungenWettbewerbeUndZensurenAb() throws IOException {
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        dienst.claim(id, besitzer, 1);
        assertFalse(dienst.entfernen(id, Optional.empty(), ziel -> false));
        var vorher = dienst.finden(id);
        assertFalse(dienst.entfernen(id, vorher, ziel -> true));
        dienst.zensieren(id, true);
        assertFalse(dienst.entfernen(id, vorher, ziel -> false));
        assertFalse(dienst.entfernen(id, dienst.finden(id), ziel -> false));
        assertTrue(dienst.entfernungen().isEmpty());
    }

    private static final class Speicher implements PlotSpeicher {
        List<Plot> plots = List.of();
        List<PlotEntfernung> entfernt = List.of();
        boolean fehler;
        Runnable vorSpeichern = () -> { };
        public List<Plot> laden() { return plots; }
        public List<PlotEntfernung> entfernungen() { return entfernt; }
        public void speichern(List<Plot> plots) throws IOException { speichern(plots, entfernt); }
        public void speichern(List<Plot> plots, List<PlotEntfernung> entfernt) throws IOException {
            vorSpeichern.run();
            if (fehler) throw new IOException("Test-Schreibfehler");
            this.plots = List.copyOf(plots); this.entfernt = List.copyOf(entfernt);
        }
    }
}
