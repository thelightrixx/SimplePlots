package de.lightrixx.plot.paper.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            assertEquals(1, dateien.count());
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
}
