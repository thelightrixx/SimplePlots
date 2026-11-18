package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.model.BauWettbewerb;
import de.lightrixx.plot.model.PlotId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
