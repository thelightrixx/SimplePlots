package de.lightrixx.plot.paper.config;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlotMenueKonfigurationTest {
    @TempDir Path ordner;

    @Test
    void laedtStandardmenuesUndBerechnetDieNavigationszeile() throws IOException {
        var gui = laden(ordner.resolve("gui.yml"));
        assertEquals(27, gui.daten().hauptgroesse());
        assertEquals(45, gui.daten().seitengroesse());
        assertEquals(53, gui.daten().navigationSlot("naechste"));
        assertEquals(Material.GRASS_BLOCK, gui.symbol("eigene", Material.STONE));
        assertThrows(UnsupportedOperationException.class, () -> gui.daten().hauptslots().clear());
    }

    @Test
    void ergaenztFehlendeWerteUndUebernimmtAenderungenGemeinsam() throws IOException {
        Path pfad = ordner.resolve("gui.yml");
        var gui = laden(pfad);
        Files.writeString(pfad, "hauptmenue:\n  slots:\n    eigene: 0\nseiten:\n  zeilen: 3\nsymbole:\n  eigene: DIAMOND\n");
        var neu = gui.vorbereiten();
        assertEquals(45, gui.daten().seitengroesse());
        gui.uebernehmen(neu);
        assertEquals(18, gui.daten().seitengroesse());
        assertEquals(26, gui.daten().navigationSlot("naechste"));
        assertEquals(0, gui.daten().hauptslots().get("eigene"));
        assertEquals(Material.DIAMOND, gui.symbol("eigene", Material.STONE));
    }

    @Test
    void lehntUngueltigeSlotsMaterialienUndTypenAbOhneDenStandZuAendern() throws IOException {
        Path pfad = ordner.resolve("gui.yml");
        var gui = laden(pfad);
        var vorher = gui.daten();
        for (String inhalt : new String[]{
                "hauptmenue:\n  slots:\n    eigene: 12\n",
                "hauptmenue:\n  slots:\n    eigene: 27\n",
                "seiten:\n  zeilen: 2\n",
                "seiten:\n  zeilen: '6'\n",
                "seiten:\n  navigation:\n    vorherige: 8\n",
                "symbole:\n  eigene: GIBT_ES_NICHT\n",
                "symbole:\n  eigene: AIR\n",
                "symbole:\n  eigene: WATER\n",
                "symbole: []\n"}) {
            Files.writeString(pfad, inhalt);
            assertThrows(IOException.class, gui::vorbereiten, inhalt);
            assertSame(vorher, gui.daten());
        }
    }

    private PlotMenueKonfiguration laden(Path pfad) throws IOException {
        // Die Material-Registries selbst stehen erst im Paper-Laufzeittest bereit.
        return new PlotMenueKonfiguration(pfad, getClass().getResourceAsStream("/config/gui.yml"),
                material -> material != Material.AIR && material != Material.WATER);
    }

    @Test
    void waehltFuerDenNeuenFlugknopfEinenFreienPlatzOhneAlteSlotsZuVerdraengen() throws IOException {
        Path pfad = ordner.resolve("gui.yml");
        Files.writeString(pfad, "hauptmenue:\n  slots:\n    eigene: 22\n");
        var gui = laden(pfad);
        assertEquals(22, gui.daten().hauptslots().get("eigene"));
        assertNotEquals(22, gui.daten().hauptslots().get("flug"));
        assertEquals(gui.daten().hauptslots().get("flug"), laden(pfad).daten().hauptslots().get("flug"));
    }
}
