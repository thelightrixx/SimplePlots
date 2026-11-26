package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.model.PlotRaster;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlotKonfigurationTest {
    @TempDir Path ordner;

    private PlotKonfiguration laden() throws IOException {
        return new PlotKonfiguration(ordner.resolve("config.yml"), material -> material != Material.AIR && material != Material.WATER && material != Material.SAND);
    }

    private void alt(String inhalt) throws IOException { Files.writeString(ordner.resolve("config.yml"), inhalt); }
    private String standard() { return "raster:\n  plotgroesse: 32\n  wegbreite: 7\nclaims:\n  pro-spieler: 3\n"; }

    @Test
    void migriertBestehendeWerteUndLaesstDenAltbestandUnveraendert() throws IOException {
        String bisher = standard().replace("32", "48").replace("3\n", "5\n") + "zusatz: behalten\n";
        alt(bisher);
        var config = laden();
        assertEquals(new PlotRaster(48, 7), config.raster());
        assertEquals(5, config.claimLimit());
        assertEquals(bisher, Files.readString(ordner.resolve("config.yml")));
        for (String name : new String[]{"general", "world", "protection", "fly", "contests", "updates"}) {
            assertTrue(Files.isRegularFile(ordner.resolve("config/" + name + ".yml")));
        }
        alt("ungueltig: [\n");
        assertEquals(5, laden().claimLimit());
    }

    @Test
    void lehntUngueltigeAltdateienVorDerMigrationAb() throws IOException {
        for (String zahl : new String[]{"0", "1025", "true", "32.5", "'32'"}) {
            alt(standard().replace("plotgroesse: 32", "plotgroesse: " + zahl));
            assertThrows(IOException.class, this::laden);
            assertFalse(Files.exists(ordner.resolve("config/general.yml")));
        }
        alt(standard().replace("pro-spieler: 3", "pro-spieler: 0"));
        assertThrows(IOException.class, this::laden);
    }

    @Test
    void legtBeiNeuinstallationNurDieAufgeteilteKonfigurationAn() throws IOException {
        var config = laden();
        assertEquals(new PlotRaster(32, 7), config.raster());
        assertEquals(3, config.claimLimit());
        assertFalse(Files.exists(ordner.resolve("config.yml")));
        assertEquals(config.werte(), laden().werte());
    }

    @Test
    void archiviertAlteKonfigurationenUndSicherungenOhneSpielstaendeZuVerschieben() throws IOException {
        alt(standard());
        Files.writeString(ordner.resolve("messages.yml"), "eigene Texte");
        Files.writeString(ordner.resolve("gui.yml"), "eigene Menüs");
        Files.writeString(ordner.resolve("config.yml.bak"), "Vorgänger");
        Files.writeString(ordner.resolve("plots.yml"), "Spielstand");
        Files.writeString(ordner.resolve("welt.yml"), "Weltmetadaten");
        var config = laden();
        config.altbestandArchivieren();
        for (String name : new String[]{"config.yml", "messages.yml", "gui.yml", "config.yml.bak"}) {
            assertFalse(Files.exists(ordner.resolve(name)));
            assertTrue(Files.isRegularFile(ordner.resolve("config/altbestand/" + name)));
        }
        assertEquals(standard(), Files.readString(ordner.resolve("config/altbestand/config.yml")));
        assertEquals("Spielstand", Files.readString(ordner.resolve("plots.yml")));
        assertEquals("Weltmetadaten", Files.readString(ordner.resolve("welt.yml")));
        assertEquals(config.werte(), laden().werte());
        assertDoesNotThrow(config::altbestandArchivieren);
    }

    @Test
    void behaeltAltdateienBeiArchivierungsfehlernUndSetztNachUnterbrechungFort() throws IOException {
        alt(standard());
        Files.writeString(ordner.resolve("messages.yml"), "Eigene Texte");
        var config = laden();
        var archiv = Files.createDirectories(ordner.resolve("config/altbestand"));
        Files.writeString(archiv.resolve("messages.yml"), "Vorhandene Sicherung");
        assertThrows(IOException.class, config::altbestandArchivieren);
        assertFalse(Files.exists(ordner.resolve("config.yml")));
        assertEquals(standard(), Files.readString(archiv.resolve("config.yml")));
        assertEquals("Eigene Texte", Files.readString(ordner.resolve("messages.yml")));
        assertEquals("Vorhandene Sicherung", Files.readString(archiv.resolve("messages.yml")));
        Files.delete(archiv.resolve("messages.yml"));
        laden().altbestandArchivieren();
        assertEquals("Eigene Texte", Files.readString(archiv.resolve("messages.yml")));
        assertFalse(Files.exists(ordner.resolve("messages.yml")));
    }

    @Test
    void speichertAdminAenderungenInDenNeuenDateienUndErhaeltZusatzwerte() throws IOException {
        alt(standard());
        var config = laden();
        Files.writeString(ordner.resolve("config/general.yml"), Files.readString(ordner.resolve("config/general.yml")) + "zusatz: behalten\n");
        config.rasterSetzen(new PlotRaster(48, 9));
        config.limitSetzen(5);
        var neu = laden();
        assertEquals(new PlotRaster(48, 9), neu.raster());
        assertEquals(5, neu.claimLimit());
        assertTrue(Files.readString(ordner.resolve("config/general.yml")).contains("zusatz: behalten"));
        assertThrows(IllegalArgumentException.class, () -> config.limitSetzen(0));
    }

    @Test
    void behaeltAuchEinBisherGueltigesGrossesClaimLimitBei() throws IOException {
        alt(standard().replace("pro-spieler: 3", "pro-spieler: 2147483647"));
        assertEquals(Integer.MAX_VALUE, laden().claimLimit());
    }

    @Test
    void uebernimmtReloadErstNachGemeinsamerPruefung() throws IOException {
        alt(standard());
        var config = laden();
        var general = ordner.resolve("config/general.yml");
        Files.writeString(general, Files.readString(general).replace("pro-spieler: 3", "pro-spieler: 8"));
        Files.writeString(ordner.resolve("config/fly.yml"), "eigener-plot:\n  geschwindigkeit: 2.0\n");
        assertThrows(IOException.class, config::vorbereiten);
        assertEquals(3, config.claimLimit());
        assertThrows(IOException.class, () -> config.limitSetzen(10));
        assertTrue(Files.readString(general).contains("pro-spieler: 8"));
        Files.writeString(ordner.resolve("config/fly.yml"), "wege:\n  aktiviert: false\n");
        var neu = config.vorbereiten();
        assertEquals(3, config.claimLimit());
        config.uebernehmen(neu);
        assertEquals(8, config.claimLimit());
        assertFalse(config.aktiv("fly.wege.aktiviert"));
        assertThrows(UnsupportedOperationException.class, () -> neu.optionen().clear());
    }

    @Test
    void lehntUngleicheTypenUndUngeeigneteMaterialienAb() throws IOException {
        alt(standard());
        var config = laden();
        var fly = ordner.resolve("config/fly.yml");
        for (String wert : new String[]{"'true'", "[]", "1"}) {
            Files.writeString(fly, "wege:\n  aktiviert: " + wert + "\n");
            assertThrows(IOException.class, config::vorbereiten);
        }
        Files.writeString(fly, "eigener-plot:\n  geschwindigkeit: .nan\n");
        assertThrows(IOException.class, config::vorbereiten);
        Files.writeString(fly, "wege:\n  aktiviert: true\n");
        for (String wert : new String[]{"AIR", "WATER", "SAND", "FEHLT"}) {
            Files.writeString(ordner.resolve("config/world.yml"), "material:\n  oberflaeche: " + wert + "\n");
            assertThrows(IOException.class, config::vorbereiten);
        }
    }

    @Test
    void ersetztKeineVerschwundeneOderUnlesbareNeueKonfigurationDurchAlteWerte() throws IOException {
        alt(standard());
        var config = laden();
        var general = ordner.resolve("config/general.yml");
        Files.delete(general);
        assertThrows(IOException.class, this::laden);
        assertThrows(IOException.class, config::vorbereiten);
        Files.createDirectory(general);
        assertThrows(IOException.class, () -> config.limitSetzen(5));
        assertEquals(3, config.claimLimit());
    }

    @Test
    void ergaenztStandardsUndKommentareBeimReloadOhneEigeneWerteOderSicherungenErneutZuSchreiben() throws IOException {
        var config = laden();
        Path general = ordner.resolve("config/general.yml");
        String eigen = "claims:\n  pro-spieler: 7\nzusatz: behalten\n";
        Files.writeString(general, eigen);
        var neu = config.vorbereiten();
        assertEquals(7, neu.claimLimit());
        assertEquals(3, config.claimLimit());
        assertEquals("actionbar", neu.optionen().get("general.betreten.anzeige"));
        assertTrue(Files.readString(general).contains("zusatz: behalten"));
        assertTrue(Files.readString(general).contains("Zeiten gelten nur für title"));
        assertEquals(eigen, Files.readString(ordner.resolve("config/general.yml.bak")));
        Files.writeString(ordner.resolve("config/general.yml.bak"), "Sicherung erhalten");
        String einmal = Files.readString(general);
        config.vorbereiten();
        assertEquals(einmal, Files.readString(general));
        assertEquals("Sicherung erhalten", Files.readString(ordner.resolve("config/general.yml.bak")));
    }

    @Test
    void validiertNeueAnzeigeUpdateKanaeleUndZensurBudgets() throws IOException {
        var config = laden();
        assertEquals("beta", config.text("updates.kanal"));
        for (String kanal : new String[]{"alpha", "beta", "stable"}) {
            Files.writeString(ordner.resolve("config/updates.yml"), "kanal: " + kanal + "\n");
            assertEquals(kanal, config.vorbereiten().optionen().get("updates.kanal"));
        }
        Files.writeString(ordner.resolve("config/general.yml"), "betreten:\n  anzeige: sidebar\n");
        assertThrows(IOException.class, config::vorbereiten);
        Files.writeString(ordner.resolve("config/general.yml"), "betreten:\n  anzeige: actionbar\n");
        for (String wert : new String[]{"zensur-abschnitte-pro-tick: 0\n", "zensur-abschnitte-pro-tick: 33\n", "zensur-zeitbudget-millis: 21\n"}) {
            Files.writeString(ordner.resolve("config/protection.yml"), wert);
            assertThrows(IOException.class, config::vorbereiten);
        }
    }
}
