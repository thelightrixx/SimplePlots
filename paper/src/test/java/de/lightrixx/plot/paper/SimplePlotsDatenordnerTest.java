package de.lightrixx.plot.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplePlotsDatenordnerTest {
    @TempDir
    Path plugins;

    @Test
    void uebernimmtDieVorhandenenDateienEinmalig() throws IOException {
        Path alt = Files.createDirectory(plugins.resolve("PlotSystem"));
        Path neu = Files.createDirectory(plugins.resolve("SimplePlots"));
        Files.writeString(alt.resolve("plots.yml"), "claims");
        Files.writeString(Files.createDirectory(alt.resolve("config")).resolve("messages.yml"), "eigene Texte");

        assertTrue(SimplePlots.uebernehmeDatenordner(neu));
        assertFalse(Files.exists(alt));
        assertEquals("claims", Files.readString(neu.resolve("plots.yml")));
        assertEquals("eigene Texte", Files.readString(neu.resolve("config/messages.yml")));
        assertFalse(SimplePlots.uebernehmeDatenordner(neu));
    }

    @Test
    void verhindertDasUeberschreibenEinesZweitenDatenbestands() throws IOException {
        Path alt = Files.createDirectory(plugins.resolve("PlotSystem"));
        Path neu = Files.createDirectory(plugins.resolve("SimplePlots"));
        Files.writeString(alt.resolve("plots.yml"), "alter Stand");
        Files.writeString(neu.resolve("plots.yml"), "neuer Stand");

        assertThrows(IOException.class, () -> SimplePlots.uebernehmeDatenordner(neu));
        assertEquals("alter Stand", Files.readString(alt.resolve("plots.yml")));
        assertEquals("neuer Stand", Files.readString(neu.resolve("plots.yml")));
    }
}
