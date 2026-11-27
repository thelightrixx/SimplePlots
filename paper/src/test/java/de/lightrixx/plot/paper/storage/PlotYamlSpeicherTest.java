package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRechte;
import de.lightrixx.plot.service.PlotDienst;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotYamlSpeicherTest {
    @TempDir
    Path ordner;

    @Test
    void schreibtUndErsetztDieYamldateiMitStabilerPlotReihenfolge() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var speicher = new PlotYamlSpeicher(pfad);
        assertTrue(speicher.laden().isEmpty());
        UUID besitzer = UUID.randomUUID();
        var erster = new Plot(new PlotId(2, 0), besitzer, Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()));
        var zweiter = Plot.neu(new PlotId(-1, -2), besitzer);
        speicher.speichern(List.of(erster));
        speicher.speichern(List.of(erster, zweiter));
        assertEquals(List.of(erster, zweiter), new PlotYamlSpeicher(pfad).laden());
        try (var dateien = Files.list(ordner)) {
            assertTrue(dateien.noneMatch(datei -> datei.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void lehntBeschaedigteRechteAb() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        Files.writeString(pfad, """
                format: 1
                plots:
                  0;0:
                    besitzer: 527675ff-55bd-47a0-b068-2aa794fc366f
                    trusted: [ungueltig]
                    gebannt: []
                """);
        assertThrows(IOException.class, () -> new PlotYamlSpeicher(pfad).laden());
    }

    @Test
    void erhaeltBiomBeiProfilwechselBesitzerwechselUndNeustartUndEntferntEsExplizit() throws IOException {
        var pfad = ordner.resolve("plots.yml");
        var besitzer = UUID.randomUUID();
        var id = new PlotId(0, 0);
        var plots = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        plots.claim(id, besitzer, 1);
        plots.profilAendern(id, besitzer, profil -> profil.mitBiom(Optional.of("minecraft:desert")).mitName("Wüste").mitBeschreibung("Oase"));
        var neu = UUID.randomUUID();
        plots.uebertragen(id, besitzer, neu, 1, ziel -> false);
        var neustart = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos());
        assertEquals(Optional.of("minecraft:desert"), neustart.finden(id).orElseThrow().profil().biom());
        neustart.profilAendern(id, neu, profil -> profil.mitBiom(Optional.empty()));
        assertTrue(new PlotYamlSpeicher(pfad).laden().getFirst().profil().biom().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PlotProfil.leer().mitBiom(Optional.of("../fremd")));
    }

    @Test
    void erhaeltDieZensurImJournalUndLehntFalscheDatentypenAb() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        var speicher = new PlotYamlSpeicher(pfad);
        speicher.laden();
        Plot plot = Plot.neu(new PlotId(0, 0), UUID.randomUUID()).mitZensur(true);
        speicher.speichern(List.of(plot));
        assertEquals(List.of(plot), new PlotYamlSpeicher(pfad).laden());
        Path journal = ordner.resolve("plots-aenderungen.yml");
        String original = Files.readString(journal);
        assertTrue(original.contains("zensiert: true"));
        Files.writeString(journal, original.replace("zensiert: true", "zensiert: 'true'"));
        assertThrows(IOException.class, () -> new PlotYamlSpeicher(pfad).laden());
    }

    @Test
    void lehntVerkuerzteUndDoppelteUuidsAb() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        UUID besitzer = UUID.randomUUID();
        UUID gast = UUID.randomUUID();
        for (String liste : List.of("1-1-1-1-1", gast + ", " + gast)) {
            String text = "format: 1\nplots:\n  0;0:\n    besitzer: " + besitzer
                    + "\n    trusted: [" + liste + "]\n    gebannt: []\n";
            Files.writeString(pfad, text);
            assertThrows(IOException.class, () -> new PlotYamlSpeicher(pfad).laden());
            assertEquals(text, Files.readString(pfad));
        }
    }

    @Test
    void uebernimmtAlteDatenUndLaedtErweiterteFelderNachDerNaechstenAenderung() throws IOException {
        Path pfad = ordner.resolve("plots.yml");
        UUID besitzer = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        UUID gebannt = UUID.randomUUID();
        UUID helfer = UUID.randomUUID();
        var id = new PlotId(0, 0);
        Files.writeString(pfad, """
                format: 1
                plots:
                  0;0:
                    besitzer: %s
                    trusted: [%s]
                    gebannt: [%s]
                """.formatted(besitzer, trusted, gebannt));
        var speicher = new PlotYamlSpeicher(pfad);
        var dienst = new PlotDienst(speicher, PlotKauf.kostenlos());
        var alt = dienst.finden(id).orElseThrow();
        assertEquals(Set.of(id), alt.zellen());
        assertEquals(Set.of(trusted), alt.rechte().trusted());
        assertEquals(Set.of(gebannt), alt.rechte().gebannt());
        assertEquals(PlotProfil.leer(), alt.profil());
        assertTrue(Files.readString(pfad).startsWith("format: 1"));

        var punkt = new PlotProfil.Besuchspunkt(4, 65, 2, -40);
        var zweiter = id.verschoben(1, 0);
        dienst.claim(zweiter, besitzer, 3);
        dienst.zusammenlegen(id, zweiter, besitzer, ziel -> false, true);
        dienst.rolleAendern(id, besitzer, helfer, Plot.RollenAktion.ADD);
        dienst.besucherRecht(id, besitzer, PlotRechte.BesucherRecht.SWITCHES, true);
        dienst.profilAendern(id, besitzer, profil -> profil.mitName("Marktplatz").mitBeschreibung("Läden und Lager")
                .mitKategorie(Optional.of(PlotProfil.Kategorie.SHOP), 1_800_000_000_000L).mitBesuchspunkt(Optional.of(punkt)));
        dienst.like(id, trusted, true);

        var neu = new PlotDienst(new PlotYamlSpeicher(pfad), PlotKauf.kostenlos()).finden(zweiter).orElseThrow();
        assertEquals(dienst.finden(id).orElseThrow(), neu);
        assertEquals(Set.of(id, zweiter), neu.zellen());
        assertEquals(Set.of(gebannt), neu.rechte().gebannt());
        assertEquals(1, neu.profil().likes().size());
        assertEquals(Optional.of(punkt), neu.profil().besuchspunkt());
        assertTrue(Files.readString(pfad).startsWith("format: 4"));
    }
}
