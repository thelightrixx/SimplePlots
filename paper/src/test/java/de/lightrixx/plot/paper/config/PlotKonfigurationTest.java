package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.model.PlotRaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotKonfigurationTest {
    @TempDir
    Path ordner;

    @Test
    void laedtDieMitgelieferteKonfiguration() throws IOException {
        Path pfad = ordner.resolve("config.yml");
        try (var standard = getClass().getResourceAsStream("/config.yml")) {
            Files.copy(standard, pfad);
        }
        var konfiguration = new PlotKonfiguration(pfad);
        assertEquals(new PlotRaster(32, 7), konfiguration.raster());
        assertEquals(3, konfiguration.claimLimit());
    }

    @Test
    void lehntFehlendeOderUngueltigeKonfigurationAb() throws IOException {
        Path pfad = ordner.resolve("config.yml");
        assertThrows(IOException.class, () -> new PlotKonfiguration(pfad));
        for (String raster : new String[]{"0", "1025", "true", "32.5", "'32'"}) {
            Files.writeString(pfad, "raster:\n  plotgroesse: " + raster + "\n  wegbreite: 7\nclaims:\n  pro-spieler: 3\n");
            assertThrows(IOException.class, () -> new PlotKonfiguration(pfad), raster);
        }
        Files.writeString(pfad, "raster:\n  plotgroesse: 32\n  wegbreite: 7\nclaims:\n  pro-spieler: 0\n");
        assertThrows(IOException.class, () -> new PlotKonfiguration(pfad));
    }

    @Test
    void speichertAenderungenUndErhaeltWeitereEinstellungen() throws IOException {
        Path pfad = ordner.resolve("config.yml");
        Files.writeString(pfad, "raster:\n  plotgroesse: 32\n  wegbreite: 7\nclaims:\n  pro-spieler: 3\nzusatz: behalten\n");
        var konfiguration = new PlotKonfiguration(pfad);
        konfiguration.rasterSetzen(new PlotRaster(48, 9));
        konfiguration.limitSetzen(5);
        var neustart = new PlotKonfiguration(pfad);
        assertEquals(new PlotRaster(48, 9), neustart.raster());
        assertEquals(5, neustart.claimLimit());
        assertTrue(Files.readString(pfad).contains("zusatz: behalten"));
        assertThrows(IllegalArgumentException.class, () -> konfiguration.limitSetzen(0));
        assertEquals(5, konfiguration.claimLimit());
    }

    @Test
    void behaeltDenZustandWennDieKonfigurationsdateiNichtMehrLesbarIst() throws IOException {
        Path pfad = ordner.resolve("config.yml");
        Files.writeString(pfad, "raster:\n  plotgroesse: 32\n  wegbreite: 7\nclaims:\n  pro-spieler: 3\n");
        var konfiguration = new PlotKonfiguration(pfad);
        Files.delete(pfad);
        Files.createDirectory(pfad);
        assertThrows(IOException.class, () -> konfiguration.rasterSetzen(new PlotRaster(48, 9)));
        assertThrows(IOException.class, () -> konfiguration.limitSetzen(5));
        assertEquals(new PlotRaster(32, 7), konfiguration.raster());
        assertEquals(3, konfiguration.claimLimit());
        assertTrue(Files.isDirectory(pfad));
    }
}
