package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.model.BauWettbewerb;
import de.lightrixx.plot.model.PlotId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WettbewerbYamlSpeicherTest {
    @TempDir
    Path ordner;

    @Test
    void erhaeltBeitraegeUndStimmenBeiNeustart() throws IOException {
        Path pfad = ordner.resolve("wettbewerb.yml");
        var speicher = new WettbewerbYamlSpeicher(pfad);
        assertTrue(speicher.laden().isEmpty());
        var id = new PlotId(-2, 3);
        var daten = new BauWettbewerb("Hafen und Flüsse", BauWettbewerb.Phase.ABSTIMMEN,
                Map.of(id, UUID.randomUUID()), Map.of(UUID.randomUUID(), id));
        speicher.speichern(Optional.of(daten));
        assertEquals(Optional.of(daten), new WettbewerbYamlSpeicher(pfad).laden());
        speicher.speichern(Optional.empty());
        assertTrue(new WettbewerbYamlSpeicher(pfad).laden().isEmpty());
    }

    @Test
    void lehntUngueltigePhasenUndEigenstimmenAbOhneDatenZuVeraendern() throws IOException {
        Path pfad = ordner.resolve("wettbewerb.yml");
        UUID besitzer = UUID.randomUUID();
        String vorlage = """
                format: 1
                wettbewerb:
                  thema: Hafen
                  phase: %s
                  beitraege:
                    '0;0': %s
                  stimmen:
                    '%s': '0;0'
                """;
        for (String phase : new String[]{"UNBEKANNT", "BAUEN", "ABSTIMMEN"}) {
            String inhalt = vorlage.formatted(phase, besitzer, besitzer);
            Files.writeString(pfad, inhalt);
            assertThrows(IOException.class, () -> new WettbewerbYamlSpeicher(pfad).laden());
            assertEquals(inhalt, Files.readString(pfad));
        }
    }
}
