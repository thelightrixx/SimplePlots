package de.lightrixx.plot.paper.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YamlSchreibgrenzeTest {
    @Test void uebergrosseUtf8DateiWirdVorDemErsetzenAbgewiesen(@TempDir Path directory) throws Exception {
        var file = directory.resolve("test.yml"); var storage = new YamlDatei(file);
        var normal = new YamlConfiguration(); normal.set("wert", "erster"); storage.speichern(normal);
        normal.set("wert", "zweiter"); storage.speichern(normal);
        String old = Files.readString(file), backup = Files.readString(directory.resolve("test.yml.bak"));
        var large = mock(YamlConfiguration.class); when(large.saveToString()).thenReturn("wert: " + "ä".repeat(32 * 1024 * 1024));
        assertThrows(IOException.class, () -> storage.speichern(large));
        assertEquals(old, Files.readString(file)); assertEquals(backup, Files.readString(directory.resolve("test.yml.bak")));
        assertEquals("zweiter", storage.laden().orElseThrow().getString("wert"));
    }
}
