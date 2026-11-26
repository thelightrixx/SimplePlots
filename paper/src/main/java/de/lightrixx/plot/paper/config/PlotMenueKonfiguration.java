package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.paper.storage.YamlDatei;
import org.bukkit.Material;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class PlotMenueKonfiguration {
    private static final String RESSOURCE = "config/gui.yml";
    private static final List<String> HAUPT = List.of("eigene", "galerie", "karte", "wettbewerb", "plotwelt", "hauptwelt", "claim", "rechte", "flug");
    private static final List<String> RECHTE = List.of("trusted", "bauhelfer", "lager", "doors", "switches", "zurueck");
    private static final List<String> NAVIGATION = List.of("vorherige", "filter", "zurueck", "extra", "naechste");
    private final YamlDatei datei;
    private final YamlConfiguration standard;
    private volatile Daten daten;
    private final Predicate<Material> gueltigesItem;

    public PlotMenueKonfiguration(JavaPlugin plugin) throws IOException {
        this(plugin.getDataFolder().toPath().resolve(RESSOURCE), plugin.getResource(RESSOURCE));
    }

    public PlotMenueKonfiguration(Path pfad, InputStream ressource) throws IOException {
        this(pfad, ressource, material -> !material.isAir() && material.isItem());
    }

    PlotMenueKonfiguration(Path pfad, InputStream ressource, Predicate<Material> gueltigesItem) throws IOException {
        this.gueltigesItem = gueltigesItem;
        datei = new YamlDatei(pfad);
        standard = new YamlConfiguration();
        if (ressource == null) {
            throw new IOException(RESSOURCE + " fehlt in der Plugin-JAR.");
        }
        try (var reader = new InputStreamReader(ressource, StandardCharsets.UTF_8)) {
            standard.load(reader);
        } catch (InvalidConfigurationException e) {
            throw new IOException("Ungültige Standardmenüs.", e);
        }
        var yaml = datei.laden().orElseGet(YamlConfiguration::new);
        var alt = PlotKonfiguration.altbestand(pfad, standard, yaml);
        if (alt.isPresent()) yaml = alt.get();
        boolean ergaenzt = !yaml.contains("hauptmenue.slots.flug", true);
        daten = pruefen(yaml);
        for (String key : standard.getKeys(true)) {
            if (!standard.isConfigurationSection(key) && !yaml.contains(key, true)) {
                yaml.set(key, key.equals("hauptmenue.slots.flug") ? daten.hauptslots().get("flug") : standard.get(key));
                ergaenzt = true;
            }
        }
        ergaenzt |= PlotKonfiguration.standardsErgaenzen(standard, yaml);
        if (Files.notExists(pfad) || alt.isPresent() || ergaenzt) datei.speichern(yaml);
    }

    public Daten vorbereiten() throws IOException {
        var yaml = datei.laden().orElseThrow(() -> new IOException(RESSOURCE + " fehlt."));
        String vorher = yaml.saveToString();
        var neu = pruefen(yaml);
        PlotKonfiguration.standardsErgaenzen(standard, yaml);
        if (!vorher.equals(yaml.saveToString())) datei.speichern(yaml);
        return neu;
    }

    private Daten pruefen(YamlConfiguration yaml) throws IOException {
        for (String key : standard.getKeys(true)) {
            if (standard.isConfigurationSection(key) && yaml.contains(key) && !yaml.isConfigurationSection(key)) {
                throw new IOException(RESSOURCE + ": " + key + " muss ein Abschnitt sein.");
            }
        }
        yaml.setDefaults(standard);
        int hauptzeilen = zeilen(yaml, "hauptmenue.zeilen");
        if (!yaml.contains("hauptmenue.slots.flug", true)) {
            var belegt = new HashSet<Integer>();
            for (String key : HAUPT) if (!key.equals("flug")) belegt.add(yaml.getInt("hauptmenue.slots." + key));
            int flug = standard.getInt("hauptmenue.slots.flug");
            if (belegt.contains(flug)) {
                flug = java.util.stream.IntStream.range(0, hauptzeilen * 9).filter(slot -> !belegt.contains(slot)).findFirst()
                        .orElseThrow(() -> new IOException("Kein freier Platz für den Flugknopf."));
            }
            yaml.set("hauptmenue.slots.flug", flug);
        }
        int rechtezeilen = zeilen(yaml, "rechte.zeilen");
        int seitenzeilen = zeilen(yaml, "seiten.zeilen");
        int kartenzeilen = zeilen(yaml, "karte.zeilen");
        int kartenbreite = yaml.getInt("karte.spalten");
        int kartenZurueck = yaml.getInt("karte.zurueck-slot");
        if (!yaml.isInt("karte.spalten") || kartenbreite < 1 || kartenbreite > 9 || kartenbreite % 2 == 0
                || !yaml.isInt("karte.zurueck-slot") || kartenZurueck < (kartenzeilen - 1) * 9 || kartenZurueck >= kartenzeilen * 9) {
            throw new IOException("Ungültige Kartengröße oder Zurück-Position in gui.yml.");
        }
        var symbole = new HashMap<String, Material>();
        for (String key : standard.getConfigurationSection("symbole").getKeys(true)) {
            String pfad = "symbole." + key;
            if (standard.isConfigurationSection(pfad)) {
                continue;
            }
            Material material = yaml.isString(pfad) ? Material.matchMaterial(yaml.getString(pfad)) : null;
            if (material == null || !gueltigesItem.test(material)) {
                throw new IOException(RESSOURCE + ": " + pfad + " muss ein gültiges Item-Material sein.");
            }
            symbole.put(key, material);
        }
        return new Daten(hauptzeilen * 9, rechtezeilen * 9, (seitenzeilen - 1) * 9,
                slots(yaml, "hauptmenue.slots", HAUPT, hauptzeilen * 9),
                slots(yaml, "rechte.slots", RECHTE, rechtezeilen * 9),
                slots(yaml, "seiten.navigation", NAVIGATION, 9), Map.copyOf(symbole), kartenzeilen, kartenbreite, kartenZurueck);
    }

    public void uebernehmen(Daten neu) {
        daten = neu;
    }

    public Daten daten() {
        return daten;
    }

    public Material symbol(String key, Material standardwert) {
        return daten.symbole().getOrDefault(key, standardwert);
    }

    private static int zeilen(YamlConfiguration yaml, String key) throws IOException {
        int wert = yaml.getInt(key);
        if (!yaml.isInt(key) || wert < 3 || wert > 6) {
            throw new IOException(RESSOURCE + ": " + key + " muss zwischen 3 und 6 liegen.");
        }
        return wert;
    }

    private static Map<String, Integer> slots(YamlConfiguration yaml, String pfad, List<String> keys, int groesse)
            throws IOException {
        var ergebnis = new HashMap<String, Integer>();
        var vergeben = new HashSet<Integer>();
        for (String key : keys) {
            int slot = yaml.getInt(pfad + "." + key);
            if (!yaml.isInt(pfad + "." + key) || slot < 0 || slot >= groesse || !vergeben.add(slot)) {
                throw new IOException(RESSOURCE + ": Ungültiger oder doppelt belegter Platz " + pfad + "." + key);
            }
            ergebnis.put(key, slot);
        }
        return Map.copyOf(ergebnis);
    }

    public record Daten(int hauptgroesse, int rechtegroesse, int seitengroesse, Map<String, Integer> hauptslots,
                        Map<String, Integer> rechteslots, Map<String, Integer> navigation, Map<String, Material> symbole,
                        int kartenzeilen, int kartenbreite, int kartenZurueck) {
        public Daten(int hauptgroesse, int rechtegroesse, int seitengroesse, Map<String, Integer> hauptslots,
                     Map<String, Integer> rechteslots, Map<String, Integer> navigation, Map<String, Material> symbole) {
            this(hauptgroesse, rechtegroesse, seitengroesse, hauptslots, rechteslots, navigation, symbole, 6, 7, 49);
        }
        public Daten {
            hauptslots = Map.copyOf(hauptslots);
            rechteslots = Map.copyOf(rechteslots);
            navigation = Map.copyOf(navigation);
            symbole = Map.copyOf(symbole);
        }

        public int navigationSlot(String name) {
            return seitengroesse + navigation.get(name);
        }
    }
}
