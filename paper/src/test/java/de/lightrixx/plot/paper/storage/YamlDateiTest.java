package de.lightrixx.plot.paper.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AccessDeniedException;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class YamlDateiTest {
    @TempDir
    Path ordner;

    @Test
    void unterscheidetFehlendeVonBeschaedigtenDateien() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var datei = new YamlDatei(pfad);
        assertTrue(datei.laden().isEmpty());
        Files.writeString(pfad, "plots: [kaputt\n");
        var fehler = assertThrows(IOException.class, datei::laden);
        assertTrue(fehler.getMessage().contains(pfad.toString()));
        assertEquals("plots: [kaputt\n", Files.readString(pfad));
    }

    @Test
    void ersetztDenGesamtenStandUndErhaeltUtf8() throws IOException {
        Path pfad = ordner.resolve("unterordner/plots.yml");
        var datei = new YamlDatei(pfad);
        var daten = new YamlConfiguration();
        daten.set("name", "Flüsse und Häfen 🧱");
        daten.set("alt", true);
        datei.speichern(daten);
        daten.set("alt", null);
        datei.speichern(daten);
        var neustart = datei.laden().orElseThrow();
        assertEquals("Flüsse und Häfen 🧱", neustart.getString("name"));
        assertEquals(1, neustart.getKeys(false).size());
        try (var dateien = Files.list(pfad.getParent())) {
            assertEquals(2, dateien.count());
        }
    }

    @Test
    void entferntTemporaereDateiNachFehlgeschlagenemErsetzen() throws IOException {
        Path pfad = Files.createDirectory(ordner.resolve("plots.yml"));
        Files.writeString(pfad.resolve("behalten.txt"), "vorhanden");
        var datei = new YamlDatei(pfad);
        var daten = new YamlConfiguration();
        daten.set("plots", "neu");
        assertThrows(IOException.class, () -> datei.speichern(daten));
        assertEquals("vorhanden", Files.readString(pfad.resolve("behalten.txt")));
        try (var dateien = Files.list(ordner)) {
            assertEquals(1, dateien.count());
        }
    }

    @Test
    void sichertDenVorherigenStandUndBegrenztDieAnzahlDerSicherungen() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var datei = new YamlDatei(pfad);
        var daten = new YamlConfiguration();
        daten.set("name", "Erster Stand");
        datei.speichern(daten);
        String erster = Files.readString(pfad);
        daten.set("name", "Zweiter Stand");
        datei.speichern(daten);
        assertEquals(erster, Files.readString(ordner.resolve("plots.yml.bak")));
        String zweiter = Files.readString(pfad);
        daten.set("name", "Dritter Stand");
        datei.speichern(daten);
        assertEquals(zweiter, Files.readString(ordner.resolve("plots.yml.bak")));
        try (var dateien = Files.list(ordner)) {
            assertEquals(2, dateien.count());
        }
    }

    @Test
    void verwirftDieAenderungWennDieSicherungFehlschlaegt() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var datei = new YamlDatei(pfad);
        var daten = new YamlConfiguration();
        daten.set("name", "Behalten");
        datei.speichern(daten);
        Files.createDirectory(ordner.resolve("plots.yml.bak"));
        Files.writeString(ordner.resolve("plots.yml.bak/behalten.txt"), "geschützt");
        daten.set("name", "Verwerfen");
        assertThrows(IOException.class, () -> datei.speichern(daten));
        assertEquals("Behalten", datei.laden().orElseThrow().getString("name"));
        try (var dateien = Files.list(ordner)) {
            assertEquals(2, dateien.count());
        }
    }

    @Test
    void lehntDoppelteYamlSchluesselAbOhneSieZuUeberschreiben() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        String text = "format: 1\nformat: 2\nplots: {}\n";
        Files.writeString(pfad, text);
        assertThrows(IOException.class, () -> new YamlDatei(pfad).laden());
        assertEquals(text, Files.readString(pfad));
    }

    @Test
    void wiederholtKurzzeitigGesperrteAtomareAustauscheOhneDenVorstandZuVerlieren() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var daten = new YamlConfiguration();
        daten.set("name", "Vorher");
        new YamlDatei(pfad).speichern(daten);
        String vorher = Files.readString(pfad);
        var versuche = new AtomicInteger();
        var datei = new YamlDatei(pfad, (quelle, ziel) -> {
            if (ziel.equals(pfad) && versuche.incrementAndGet() < 3) {
                throw new AccessDeniedException(ziel.toString());
            }
            Files.move(quelle, ziel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        });
        daten.set("name", "Nachher");
        datei.speichern(daten);
        assertEquals(3, versuche.get());
        assertEquals("Nachher", datei.laden().orElseThrow().getString("name"));
        assertEquals(vorher, Files.readString(ordner.resolve("plots.yml.bak")));
    }

    @Test
    void brichtDauerhafteDateisperrenBegrenztAbUndEntferntTemporaereDateien() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var daten = new YamlConfiguration();
        daten.set("name", "Behalten");
        new YamlDatei(pfad).speichern(daten);
        String vorher = Files.readString(pfad);
        var versuche = new AtomicInteger();
        var datei = new YamlDatei(pfad, (quelle, ziel) -> {
            versuche.incrementAndGet();
            throw new AccessDeniedException(ziel.toString());
        });
        daten.set("name", "Nicht gespeichert");
        assertThrows(AccessDeniedException.class, () -> datei.speichern(daten));
        assertEquals(5, versuche.get());
        assertEquals(vorher, Files.readString(pfad));
        try (var dateien = Files.list(ordner)) {
            assertFalse(dateien.anyMatch(dateiPfad -> dateiPfad.toString().endsWith(".tmp")));
        }
    }

    @Test
    void erhaeltUnterbrechungenWaehrendEinerDateisperreUndBeendetDenSchreibversuch() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var daten = new YamlConfiguration();
        daten.set("name", "Nicht gespeichert");
        var versuche = new AtomicInteger();
        var datei = new YamlDatei(pfad, (quelle, ziel) -> {
            versuche.incrementAndGet();
            Thread.currentThread().interrupt();
            throw new AccessDeniedException(ziel.toString());
        });
        try {
            assertThrows(IOException.class, () -> datei.speichern(daten));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, versuche.get());
        } finally {
            Thread.interrupted();
        }
        assertFalse(Files.exists(pfad));
        try (var dateien = Files.list(ordner)) {
            assertEquals(0, dateien.count());
        }
    }
}
