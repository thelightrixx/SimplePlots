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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class PlotNachrichten {
    private static final String RESSOURCE = "config/messages.yml";
    private volatile Map<String, String> texte;
    private final YamlDatei datei;
    private final YamlConfiguration standard;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlotNachrichten(JavaPlugin plugin) throws IOException {
        this(plugin.getDataFolder().toPath().resolve(RESSOURCE), plugin.getResource(RESSOURCE));
    }

    public PlotNachrichten(Path pfad, InputStream ressource) throws IOException {
        datei = new YamlDatei(pfad);
        standard = new YamlConfiguration();
        if (ressource == null) {
            throw new IOException(RESSOURCE + " fehlt in der Plugin-JAR.");
        }
        try (var reader = new InputStreamReader(ressource, StandardCharsets.UTF_8)) {
            standard.load(reader);
        } catch (InvalidConfigurationException e) {
            throw new IOException("Ungültige Standardnachrichten.", e);
        }
        var geladen = datei.laden().orElseGet(YamlConfiguration::new);
        boolean aktualisiert = aktualisiereStandardtexte(geladen);
        var alt = PlotKonfiguration.altbestand(pfad, standard, geladen);
        if (alt.isPresent()) {
            geladen = alt.get();
            aktualisiert = aktualisiereStandardtexte(geladen);
        }
        var geprueft = pruefen(geladen);
        boolean ergaenzt = false;
        for (String key : standard.getKeys(true)) {
            if (!standard.isConfigurationSection(key) && !geladen.contains(key)) {
                geladen.set(key, standard.get(key));
                ergaenzt = true;
            }
        }
        if (Files.notExists(pfad) || alt.isPresent() || aktualisiert || ergaenzt) {
            datei.speichern(geladen);
        }
        texte = geprueft;
    }

    private boolean aktualisiereStandardtexte(YamlConfiguration geladen) {
        boolean prefix = aktualisiereStandardtext(geladen, "prefix", "<dark_gray>[<green>PlotSystem</green>]</dark_gray> <gray>");
        boolean menue = aktualisiereStandardtext(geladen, "menue.titel", "<dark_green>PlotSystem</dark_green>");
        boolean modus = aktualisiereStandardtext(geladen, "admin.modus-hilfe",
                "<green>/plot admin gamemode</green> <yellow>creative</yellow> oder <yellow>survival</yellow>");
        boolean raster = aktualisiereStandardtext(geladen, "admin.reload-raster",
                "<red>Reload abgelehnt: Das Raster in config.yml weicht von der bestehenden Plotwelt ab. Der bisherige Stand bleibt aktiv.</red>");
        return prefix || menue || modus || raster;
    }

    private boolean aktualisiereStandardtext(YamlConfiguration geladen, String schluessel, String bisher) {
        if (!bisher.equals(geladen.getString(schluessel))) {
            return false;
        }
        geladen.set(schluessel, standard.getString(schluessel));
        return true;
    }

    public Map<String, String> vorbereiten() throws IOException {
        return pruefen(datei.laden().orElseThrow(() -> new IOException(RESSOURCE + " fehlt.")));
    }

    public void uebernehmen(Map<String, String> neu) {
        texte = Map.copyOf(neu);
    }

    private Map<String, String> pruefen(YamlConfiguration geladen) throws IOException {
        var ergebnis = new HashMap<String, String>();
        for (String schluessel : standard.getKeys(true)) {
            if (standard.isConfigurationSection(schluessel)) {
                if (geladen.contains(schluessel) && !geladen.isConfigurationSection(schluessel)) {
                    throw new IOException(RESSOURCE + ": " + schluessel + " muss ein Abschnitt sein.");
                }
                continue;
            }
            if (geladen.contains(schluessel) && !geladen.isString(schluessel)) {
                throw new IOException(RESSOURCE + ": " + schluessel + " muss Text sein.");
            }
            String text = geladen.getString(schluessel, standard.getString(schluessel));
            try {
                miniMessage.deserialize(text);
            } catch (RuntimeException e) {
                throw new IOException(RESSOURCE + ": Ungültiges MiniMessage bei " + schluessel, e);
            }
            ergebnis.put(schluessel, text);
        }
        return Map.copyOf(ergebnis);
    }

    public void senden(CommandSender empfaenger, String schluessel, TagResolver... platzhalter) {
        var aktuell = texte;
        String text = aktuell.getOrDefault(schluessel, schluessel);
        String praefix = aktuell.getOrDefault("prefix", "");
        empfaenger.sendMessage(miniMessage.deserialize(praefix + text, platzhalter));
    }

    public Component komponente(String schluessel, TagResolver... platzhalter) {
        return miniMessage.deserialize(texte.getOrDefault(schluessel, schluessel), platzhalter);
    }
}
