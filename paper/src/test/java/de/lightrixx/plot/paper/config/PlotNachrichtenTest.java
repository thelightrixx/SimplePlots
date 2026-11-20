package de.lightrixx.plot.paper.config;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;
import static org.junit.jupiter.api.Assertions.*;

class PlotNachrichtenTest {
    @TempDir Path ordner;

    @Test
    void ergaenztNeueStandardtexteUndBewahrtEigeneTexte() throws IOException {
        Path pfad = ordner.resolve("messages.yml");
        Files.writeString(pfad, "prefix: '<gold>Community</gold> '\nclaim:\n  erfolg: 'Dein Plot: <id>'\n");
        var texte = laden(pfad);
        assertEquals("Dein Plot: 0;0", klartext(texte.komponente("claim.erfolg", unparsed("id", "0;0"))));
        assertTrue(klartext(texte.komponente("admin.reload-erfolg")).contains("neu geladen"));
        assertTrue(Files.readString(pfad).contains("reload-erfolg"));
    }

    @Test
    void setztSpielertexteAlsKlartextEin() throws IOException {
        var texte = laden(ordner.resolve("messages.yml"));
        String eingabe = "<red>Fremde Farbe</red><click:run_command:'/op test'>Klick</click>";
        var nachricht = texte.komponente("menue.plot-name", unparsed("name", eingabe));
        assertEquals(eingabe, klartext(nachricht));
        assertNull(nachricht.clickEvent());
        assertNotEquals(NamedTextColor.RED, nachricht.color());
    }

    @Test
    void verwirftFalscheTypenUndLaesstDenAktivenStandErhalten() throws IOException {
        Path pfad = ordner.resolve("messages.yml");
        var texte = laden(pfad);
        var vorher = texte.komponente("admin.reload-erfolg");
        for (String inhalt : new String[]{"admin: 42\n", "prefix: []\n", "claim:\n  erfolg: false\n"}) {
            Files.writeString(pfad, inhalt);
            assertThrows(IOException.class, texte::vorbereiten);
            assertEquals(vorher, texte.komponente("admin.reload-erfolg"));
            assertEquals(inhalt, Files.readString(pfad));
        }
    }

    @Test
    void uebernimmtNachrichtenErstNachAusdruecklicherFreigabeDesGesamtReloads() throws IOException {
        Path pfad = ordner.resolve("messages.yml");
        var texte = laden(pfad);
        Files.writeString(pfad, "claim:\n  erfolg: 'Neu: <id>'\n");
        var neu = texte.vorbereiten();
        assertFalse(klartext(texte.komponente("claim.erfolg")).startsWith("Neu:"));
        texte.uebernehmen(neu);
        assertEquals("Neu: 4;2", klartext(texte.komponente("claim.erfolg", unparsed("id", "4;2"))));
    }

    @Test
    void laesstEineUngueltigeDateiAuchBeiAnstehenderNamensmigrationUnveraendert() throws IOException {
        Path pfad = ordner.resolve("messages.yml");
        String inhalt = "prefix: '<dark_gray>[<green>PlotSystem</green>]</dark_gray> <gray>'\nclaim: false\n";
        Files.writeString(pfad, inhalt);
        assertThrows(IOException.class, () -> laden(pfad));
        assertEquals(inhalt, Files.readString(pfad));
        assertFalse(Files.exists(ordner.resolve("messages.yml.bak")));
    }

    private PlotNachrichten laden(Path pfad) throws IOException {
        return new PlotNachrichten(pfad, getClass().getResourceAsStream("/config/messages.yml"));
    }

    private static String klartext(net.kyori.adventure.text.Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }
}
