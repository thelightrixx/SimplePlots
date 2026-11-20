package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotRaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZensurSpeicherTest {
    @TempDir Path ordner;
    private final Plot plot = Plot.neu(new PlotId(-1, 2), UUID.randomUUID());
    private ZensurSpeicher speicher() { return new ZensurSpeicher(ordner, plot, new PlotRaster(32, 7), "welt", -64, 320); }

    @Test
    void setztUnterbrocheneSicherungFortUndErhaeltOriginaldateien() throws IOException {
        var speicher = speicher();
        assertFalse(speicher.oeffnen(false, true));
        UUID entity = UUID.randomUUID();
        speicher.entitiesSichern(Map.of(entity, new byte[]{1, 2}));
        speicher.teilSichern("0-0", new byte[]{3, 4});
        var neustart = speicher();
        assertFalse(neustart.oeffnen(false, false));
        assertArrayEquals(new byte[]{3, 4}, neustart.teil("0-0").orElseThrow());
        assertArrayEquals(new byte[]{1, 2}, neustart.entities().orElseThrow().get(entity));
        assertTrue(neustart.teil("0-1").isEmpty());
        assertThrows(IOException.class, () -> neustart.teilSichern("0-0", new byte[]{9}));
        assertThrows(IOException.class, () -> neustart.oeffnen(true, false));
        neustart.vollstaendig();
        assertTrue(neustart.oeffnen(true, false));
        var nachAbbruch = speicher();
        assertTrue(nachAbbruch.oeffnen(false, false));
        assertArrayEquals(new byte[]{3, 4}, nachAbbruch.teil("0-0").orElseThrow());
    }

    @Test
    void erkenntBeschaedigteOderFehlendeSicherungenUndAbweichendeWelt() throws IOException {
        var speicher = speicher();
        assertThrows(IOException.class, () -> speicher.oeffnen(false, false));
        speicher.oeffnen(false, true);
        speicher.teilSichern("0-0", new byte[]{3, 4});
        try (var dateien = Files.walk(ordner)) {
            Path teil = dateien.filter(path -> path.toString().endsWith("0-0.nbt")).findFirst().orElseThrow();
            Files.write(teil, new byte[]{9, 9});
        }
        assertThrows(IOException.class, () -> speicher.teil("0-0"));
        var andereWelt = new ZensurSpeicher(ordner, plot, new PlotRaster(32, 7), "andere-welt", -64, 320);
        assertThrows(IOException.class, () -> andereWelt.oeffnen(false, false));
    }

    @Test
    void behaeltBeiEinemManifestSchreibfehlerDenLetztenBestaetigtenStand() throws IOException {
        var speicher = speicher();
        speicher.oeffnen(false, true);
        speicher.teilSichern("0-0", new byte[]{1});
        Path manifest = ordner.resolve(plot.id().toString()).resolve("status.yml");
        Files.delete(manifest);
        Files.createDirectory(manifest);
        assertThrows(IOException.class, () -> speicher.teilSichern("0-1", new byte[]{2}));
        assertTrue(speicher.teil("0-1").isEmpty());
        assertArrayEquals(new byte[]{1}, speicher.teil("0-0").orElseThrow());
        assertThrows(IOException.class, speicher::vollstaendig);
    }
}
