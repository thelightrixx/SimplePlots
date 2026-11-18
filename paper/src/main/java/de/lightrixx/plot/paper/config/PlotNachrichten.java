package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.paper.storage.YamlDatei;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;

public final class PlotNachrichten {
    private static final String RESSOURCE = "config/messages.yml";
    private final YamlConfiguration texte;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlotNachrichten(JavaPlugin plugin) throws IOException {
        var pfad = plugin.getDataFolder().toPath().resolve(RESSOURCE);
        if (Files.notExists(pfad)) {
            plugin.saveResource(RESSOURCE, false);
        }
        var datei = new YamlDatei(pfad);
        texte = datei.laden().orElseThrow(() -> new IOException(RESSOURCE + " fehlt."));

        var standard = new YamlConfiguration();
        var ressource = plugin.getResource(RESSOURCE);
        if (ressource == null) {
            throw new IOException(RESSOURCE + " fehlt in der Plugin-JAR.");
        }
        try (var reader = new InputStreamReader(ressource, StandardCharsets.UTF_8)) {
            standard.load(reader);
        } catch (InvalidConfigurationException e) {
            throw new IOException("Ungültige Standardnachrichten.", e);
        }
        boolean prefixAktualisiert = aktualisiereStandardtext(standard, "prefix",
                "<dark_gray>[<green>PlotSystem</green>]</dark_gray> <gray>");
        boolean menueAktualisiert = aktualisiereStandardtext(standard, "menue.titel", "<dark_green>PlotSystem</dark_green>");
        if (prefixAktualisiert || menueAktualisiert) {
            datei.speichern(texte);
        }
        texte.setDefaults(standard);
    }

    private boolean aktualisiereStandardtext(YamlConfiguration standard, String schluessel, String bisher) {
        if (!bisher.equals(texte.getString(schluessel))) {
            return false;
        }
        texte.set(schluessel, standard.getString(schluessel));
        return true;
    }

    public void senden(CommandSender empfaenger, String schluessel, TagResolver... platzhalter) {
        String text = Objects.requireNonNullElse(texte.getString(schluessel), schluessel);
        String praefix = Objects.requireNonNullElse(texte.getString("prefix"), "");
        empfaenger.sendMessage(miniMessage.deserialize(praefix + text, platzhalter));
    }

    public Component komponente(String schluessel, TagResolver... platzhalter) {
        return miniMessage.deserialize(Objects.requireNonNullElse(texte.getString(schluessel), schluessel), platzhalter);
    }
}
