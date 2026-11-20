package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.service.PlotDienst;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotYamlJournalTest {
    @TempDir Path ordner;
    private final UUID besitzer = UUID.randomUUID();

    @Test
    void schreibtBeiZehntausendPlotsNurDenGeaendertenPlotUndLaedtIhnBeimNeustart() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var speicher = new PlotYamlSpeicher(pfad);
        var plots = daten(10_000);
        speicher.speichern(plots);
        byte[] grundbestand = Files.readAllBytes(pfad);
        plots.set(500, plots.get(500).mitProfil(plots.get(500).profil().mitName("Neuer Name")));
        speicher.speichern(plots);
        assertArrayEquals(grundbestand, Files.readAllBytes(pfad));
        assertTrue(Files.size(ordner.resolve("plots-aenderungen.yml")) < grundbestand.length / 10);
        assertEquals(plots, new PlotYamlSpeicher(pfad).laden());
    }

    @Test
    void verdichtetBegrenzteJournaleUndIgnoriertBereitsUebernommeneDeltas() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var speicher = new PlotYamlSpeicher(pfad);
        var plots = daten(140);
        speicher.speichern(plots);
        for (int i = 0; i < 127; i++) {
            plots.set(i, plots.get(i).mitProfil(plots.get(i).profil().mitName("Umbau " + i)));
        }
        speicher.speichern(plots);
        byte[] altesJournal = Files.readAllBytes(ordner.resolve("plots-aenderungen.yml"));
        plots.set(127, plots.get(127).mitProfil(plots.get(127).profil().mitName("Verdichten")));
        speicher.speichern(plots);
        // Abbruch direkt nach dem atomaren Grundbestand-Austausch: altes Journal liegt noch da.
        assertArrayEquals(altesJournal, Files.readAllBytes(ordner.resolve("plots-aenderungen.yml")));
        assertEquals(plots, new PlotYamlSpeicher(pfad).laden());
        plots.set(139, plots.get(139).mitProfil(plots.get(139).profil().mitName("Nach der Verdichtung")));
        speicher.speichern(plots);
        assertEquals(plots, new PlotYamlSpeicher(pfad).laden());
    }

    @Test
    void speichertMergeMitEntferntemHauptplotAlsEineAenderung() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var dienst = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        var erster = new PlotId(0, 0);
        var zweiter = new PlotId(1, 0);
        dienst.claim(erster, besitzer, 2);
        dienst.claim(zweiter, besitzer, 2);
        // Beide Claims einmal als Grundbestand sichern, damit das Journal auch Löschungen benötigt.
        var bestand = new PlotYamlSpeicher(pfad);
        bestand.speichern(dienst.alle().reversed());
        dienst = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        dienst.zusammenlegen(erster, zweiter, besitzer, id -> false, true);
        var neustart = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        assertEquals(1, neustart.anzahlPlots());
        assertEquals(2, neustart.anzahlZellen());
        assertEquals(erster, neustart.finden(zweiter).orElseThrow().id());
    }

    @Test
    void behaeltBeiJournalSchreibfehlernRechteUndGespeicherteDaten() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var dienst = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        var id = new PlotId(0, 0);
        dienst.claim(id, besitzer, 1);
        var backup = ordner.resolve("plots-aenderungen.yml.bak");
        Files.delete(backup);
        Files.createDirectory(backup);
        Files.writeString(backup.resolve("behalten.txt"), "behalten");
        UUID gast = UUID.randomUUID();
        assertThrows(IOException.class, () -> dienst.rolleAendern(id, besitzer, gast, Plot.RollenAktion.TRUST));
        assertFalse(dienst.finden(id).orElseThrow().darfBauen(gast));
        var neustart = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        assertFalse(neustart.finden(id).orElseThrow().darfBauen(gast));
    }

    @Test
    void lehntFehlendeOderUnvereinbareBestandteileAb() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var speicher = new PlotYamlSpeicher(pfad);
        speicher.speichern(daten(2));
        Path journal = ordner.resolve("plots-aenderungen.yml");
        String original = Files.readString(journal);
        Files.delete(journal);
        assertThrows(IOException.class, () -> new PlotYamlSpeicher(pfad).laden());
        Files.writeString(journal, original.replace("basis: 1", "basis: 99"));
        assertThrows(IOException.class, () -> new PlotYamlSpeicher(pfad).laden());
        Files.writeString(journal, original);
        Files.delete(pfad);
        assertThrows(IOException.class, () -> new PlotYamlSpeicher(pfad).laden());
    }

    @Test
    void behaeltLegacyDatenWennDieFormatumstellungUnterbrochenWird() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        String alt = "format: 1\nplots:\n  0;0:\n    besitzer: " + besitzer + "\n    trusted: []\n    gebannt: []\n";
        Files.writeString(pfad, alt);
        Files.createDirectories(ordner.resolve("plots.yml.bak"));
        Files.writeString(ordner.resolve("plots.yml.bak/behalten.txt"), "behalten");
        var speicher = new PlotYamlSpeicher(pfad);
        assertThrows(IOException.class, () -> speicher.speichern(daten(2)));
        assertEquals(alt, Files.readString(pfad));
        assertEquals(List.of(Plot.neu(new PlotId(0, 0), besitzer)), new PlotYamlSpeicher(pfad).laden());
    }

    @Test
    void lehntJournaleAusEinemAnderenDatenbestandAb() throws IOException {
        Path erster = ordner.resolve("erster.yml");
        Path zweiter = ordner.resolve("zweiter.yml");
        new PlotYamlSpeicher(erster).speichern(daten(2));
        new PlotYamlSpeicher(zweiter).speichern(daten(2));
        Files.writeString(ordner.resolve("erster-aenderungen.yml"), Files.readString(ordner.resolve("zweiter-aenderungen.yml")));
        var fehler = assertThrows(IOException.class, () -> new PlotYamlSpeicher(erster).laden());
        assertTrue(fehler.getMessage().contains("anderen Datenbestand"));
    }

    @Test
    void behandeltEinLeeresAltesJournalOhneVerdichtetenGrundbestandNichtAlsLeerenSpielstand() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        new PlotYamlSpeicher(pfad).speichern(daten(140));
        Files.delete(pfad);
        assertThrows(IOException.class, () -> new PlotYamlSpeicher(pfad).laden());
    }

    @Test
    void veroeffentlichtKeineRechteWennDerGrundbestandImBetriebVerschwindet() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var dienst = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        var id = new PlotId(0, 0);
        dienst.claim(id, besitzer, 1);
        Files.delete(pfad);
        UUID gast = UUID.randomUUID();
        assertThrows(IOException.class, () -> dienst.rolleAendern(id, besitzer, gast, Plot.RollenAktion.TRUST));
        assertFalse(dienst.finden(id).orElseThrow().darfBauen(gast));
        assertFalse(Files.exists(pfad));
    }

    @Test
    void erhaeltEinenFehlendenJournalbestandAuchBeiWeiterenSchreibversuchen() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var dienst = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        var id = new PlotId(0, 0);
        dienst.claim(id, besitzer, 1);
        Path journal = ordner.resolve("plots-aenderungen.yml");
        Files.delete(journal);
        UUID gast = UUID.randomUUID();
        assertThrows(IOException.class, () -> dienst.rolleAendern(id, besitzer, gast, Plot.RollenAktion.TRUST));
        assertFalse(dienst.finden(id).orElseThrow().darfBauen(gast));
        assertFalse(Files.exists(journal));
    }

    @Test
    void prueftAuchBereitsVerdichteteJournaleAufVollstaendigeDaten() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var speicher = new PlotYamlSpeicher(pfad);
        speicher.speichern(daten(2));
        speicher.speichern(daten(140));
        Path journal = ordner.resolve("plots-aenderungen.yml");
        var yaml = new YamlDatei(journal).laden().orElseThrow();
        yaml.set("plots", null);
        String text = yaml.saveToString();
        Files.writeString(journal, text);
        assertThrows(IOException.class, () -> new PlotYamlSpeicher(pfad).laden());
        assertEquals(text, Files.readString(journal));
    }

    @Test
    void behaeltBeiFehlgeschlagenerVerdichtungDenLetztenBestaetigtenStandUndErlaubtDenNeustart() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var speicher = new PlotYamlSpeicher(pfad);
        var original = daten(140);
        speicher.speichern(original);
        var geaendert = new ArrayList<>(original);
        for (int i = 0; i < 127; i++) {
            geaendert.set(i, geaendert.get(i).mitProfil(geaendert.get(i).profil().mitName("Neu " + i)));
        }
        speicher.speichern(geaendert);
        var bestaetigt = List.copyOf(geaendert);
        Path backup = ordner.resolve("plots.yml.bak");
        Files.delete(backup);
        Files.createDirectory(backup);
        Files.writeString(backup.resolve("behalten.txt"), "behalten");
        geaendert.set(127, geaendert.get(127).mitProfil(geaendert.get(127).profil().mitName("Nicht gespeichert")));
        assertThrows(IOException.class, () -> speicher.speichern(geaendert));
        assertEquals(bestaetigt, new PlotYamlSpeicher(pfad).laden());
        Files.delete(backup.resolve("behalten.txt"));
        Files.delete(backup);
        speicher.speichern(geaendert);
        assertEquals(geaendert, new PlotYamlSpeicher(pfad).laden());
    }

    private ArrayList<Plot> daten(int anzahl) {
        var plots = new ArrayList<Plot>();
        for (int i = 0; i < anzahl; i++) {
            plots.add(Plot.neu(new PlotId(i, 0), besitzer));
        }
        return plots;
    }
}
