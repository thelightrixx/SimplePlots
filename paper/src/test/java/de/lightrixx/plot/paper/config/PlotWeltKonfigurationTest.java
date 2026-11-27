package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.model.PlotRaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlotWeltKonfigurationTest {
    @TempDir Path ordner;
    private PlotKonfiguration laden() throws IOException { return new PlotKonfiguration(ordner.resolve("config.yml"), material -> true); }

    @Test
    void legtDieBisherigeWeltMitUnveraenderterDatenablageAnUndErbtGlobaleWerte() throws IOException {
        var basis = laden();
        var welten = basis.weltenLaden();
        assertEquals(1, welten.size());
        var plots = welten.getFirst();
        assertEquals("plots", plots.weltname()); assertEquals(ordner.toAbsolutePath(), plots.datenordner());
        assertEquals(basis.werte(), plots.werte());
        assertTrue(Files.readString(ordner.resolve("worlds/plots.yml")).contains("Fehlende Werte"));
    }

    @Test
    void trenntRasterLimitsUndSchutzUndLaesstGlobaleKonfigurationUnveraendert() throws IOException {
        var basis = laden(); basis.weltenLaden();
        Files.writeString(ordner.resolve("worlds/creative.yml"), "world:\n  raster:\n    plotgroesse: 48\n    wegbreite: 9\ngeneral:\n  claims:\n    pro-spieler: 10\nfly:\n  eigener-plot:\n    hoehe: 80\n");
        var creative = basis.fuerWelt("creative");
        assertEquals(new PlotRaster(48, 9), creative.raster()); assertEquals(10, creative.claimLimit());
        assertEquals(80, creative.zahl("fly.eigener-plot.hoehe"));
        assertEquals(ordner.resolve("data/creative").toAbsolutePath(), creative.datenordner());
        assertEquals(3, basis.claimLimit()); assertEquals(new PlotRaster(32, 7), basis.raster());
        creative.limitSetzen(12); creative.rasterSetzen(new PlotRaster(16, 3));
        assertEquals(12, basis.fuerWelt("creative").claimLimit());
        assertEquals(new PlotRaster(16, 3), basis.fuerWelt("creative").raster());
        assertEquals(3, laden().claimLimit()); assertEquals(2, basis.weltenLaden().size());
    }

    @Test
    void behaeltAktivenStandBeiUngueltigemWertUndLehntUnbekannteWeltoptionenAb() throws IOException {
        var basis = laden(); var config = basis.weltenLaden().getFirst();
        Path datei = ordner.resolve("worlds/plots.yml");
        for (String inhalt : new String[]{"world:\n  raster:\n    plotgroesse: -1\n", "updates:\n  kanal: stable\n",
                "world:\n  unbekannt: true\n", "general:\n  claims: []\n", "", "world: [\n"}) {
            Files.writeString(datei, inhalt);
            assertThrows(IOException.class, config::vorbereiten, inhalt);
            assertEquals(new PlotRaster(32, 7), config.raster());
            assertEquals(inhalt, Files.readString(datei));
        }
        assertThrows(IOException.class, () -> basis.fuerWelt("../fremd"));
    }

    @Test
    void erkenntFehlendeStandardweltStattEineAndereWeltZuWaehlen() throws IOException {
        var basis = laden(); basis.weltenLaden();
        Files.move(ordner.resolve("worlds/plots.yml"), ordner.resolve("worlds/andere.yml"));
        assertThrows(IOException.class, basis::weltenLaden);
        assertFalse(Files.exists(ordner.resolve("worlds/plots.yml")));
    }

    @Test
    void erhaeltEigeneRechtebuendelUndPrueftUngueltigeVererbung() throws IOException {
        var basis = laden(); Path general = ordner.resolve("config/general.yml");
        var datei = new de.lightrixx.plot.paper.storage.YamlDatei(general);
        var yaml = datei.laden().orElseThrow();
        yaml.set("rang-buendel.admin", java.util.List.of());
        yaml.set("rang-buendel.team", java.util.List.of("simpleplots.admin.censor"));
        datei.speichern(yaml);
        var neu = basis.vorbereiten();
        var buendel = (java.util.Map<?, ?>) neu.optionen().get("general.rang-buendel");
        assertEquals(java.util.List.of(), buendel.get("admin"));
        assertEquals(java.util.List.of("simpleplots.admin.censor"), buendel.get("team"));
        assertThrows(UnsupportedOperationException.class, () -> ((java.util.List<?>) buendel.get("team")).clear());
        yaml.set("rang-buendel.admin", java.util.List.of("simpleplots.rang.admin")); datei.speichern(yaml);
        assertThrows(IOException.class, basis::vorbereiten);
    }
}
