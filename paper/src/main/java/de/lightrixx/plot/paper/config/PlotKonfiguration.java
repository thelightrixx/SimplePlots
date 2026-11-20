package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.storage.YamlDatei;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PlotKonfiguration {
    private static final List<String> DATEIEN = List.of("general", "world", "protection", "fly", "contests", "updates");
    private final Path ordner;
    private final Map<String, YamlConfiguration> standard = new LinkedHashMap<>();
    private volatile Werte werte;
    private final java.util.function.Predicate<Material> festesMaterial;

    public PlotKonfiguration(Path bisher) throws IOException {
        this(bisher, material -> material.isBlock() && material.isSolid() && !material.hasGravity());
    }

    public PlotKonfiguration(Path bisher, java.util.function.Predicate<Material> festesMaterial) throws IOException {
        this.festesMaterial = Objects.requireNonNull(festesMaterial);
        ordner = bisher.toAbsolutePath().getParent().resolve("config");
        long vorhanden = DATEIEN.stream().filter(name -> Files.exists(pfad(name))).count();
        if (vorhanden > 0 && vorhanden < DATEIEN.size()) {
            throw new IOException("Unvollständiger Konfigurationsordner. Fehlende YAML-Dateien aus der Sicherung wiederherstellen.");
        }
        for (String name : DATEIEN) {
            try (var ressource = PlotKonfiguration.class.getResourceAsStream("/config/" + name + ".yml")) {
                if (ressource == null) {
                    throw new IOException("Standardkonfiguration fehlt: " + name);
                }
                var yaml = new YamlConfiguration();
                yaml.load(new InputStreamReader(ressource, StandardCharsets.UTF_8));
                standard.put(name, yaml);
            } catch (InvalidConfigurationException e) {
                throw new IOException("Ungültige Standardkonfiguration: " + name, e);
            }
        }
        var dateien = new LinkedHashMap<String, YamlConfiguration>();
        YamlConfiguration alt = null;
        if (Files.notExists(pfad("general")) || Files.notExists(pfad("world"))) {
            alt = new YamlDatei(bisher).laden().orElse(null);
            if (alt != null) rasterUndLimit(alt);
        }
        for (String name : DATEIEN) {
            var geladen = new YamlDatei(pfad(name)).laden().orElseGet(YamlConfiguration::new);
            if (Files.notExists(pfad(name)) && alt != null) {
                if (name.equals("world")) {
                    geladen.set("raster.plotgroesse", alt.getInt("raster.plotgroesse"));
                    geladen.set("raster.wegbreite", alt.getInt("raster.wegbreite"));
                } else if (name.equals("general")) {
                    geladen.set("claims.pro-spieler", alt.getInt("claims.pro-spieler"));
                }
            }
            dateien.put(name, geladen);
        }
        werte = pruefen(dateien);
        for (String name : DATEIEN) {
            var yaml = dateien.get(name);
            boolean fehlt = Files.notExists(pfad(name));
            for (String key : standard.get(name).getKeys(true)) {
                if (!standard.get(name).isConfigurationSection(key) && !yaml.contains(key)) {
                    yaml.set(key, standard.get(name).get(key));
                    fehlt = true;
                }
                yaml.setComments(key, standard.get(name).getComments(key));
            }
            if (fehlt) {
                new YamlDatei(pfad(name)).speichern(yaml);
            }
        }
    }

    private Path pfad(String name) { return ordner.resolve(name + ".yml"); }

    static Optional<YamlConfiguration> altbestand(Path ziel, YamlConfiguration standard, YamlConfiguration aktuell) throws IOException {
        Path config = ziel.toAbsolutePath().getParent();
        if (!config.getFileName().toString().equals("config")) return Optional.empty();
        for (String key : aktuell.getKeys(true)) {
            if (aktuell.isConfigurationSection(key)) {
                if (!standard.isConfigurationSection(key)) return Optional.empty();
            } else if (!Objects.equals(aktuell.get(key), standard.get(key))) {
                return Optional.empty();
            }
        }
        return new YamlDatei(config.getParent().resolve(ziel.getFileName())).laden();
    }

    public void altbestandArchivieren() throws IOException {
        for (String name : List.of("config.yml", "messages.yml", "gui.yml")) {
            for (String endung : List.of("", ".bak")) {
                Path quelle = ordner.getParent().resolve(name + endung);
                if (Files.notExists(quelle)) continue;
                Path ziel = ordner.resolve("altbestand").resolve(name + endung);
                Files.createDirectories(ziel.getParent());
                if (Files.exists(ziel)) {
                    throw new IOException("Altbestand bereits vorhanden: " + ziel + ". Dateien bei gestopptem Server prüfen; " + quelle + " bleibt erhalten.");
                }
                Files.move(quelle, ziel);
            }
        }
    }

    public Werte vorbereiten() throws IOException {
        var geladen = new LinkedHashMap<String, YamlConfiguration>();
        for (String name : DATEIEN) {
            geladen.put(name, new YamlDatei(pfad(name)).laden()
                    .orElseThrow(() -> new IOException("Konfiguration fehlt: " + pfad(name))));
        }
        return pruefen(geladen);
    }

    private Werte pruefen(Map<String, YamlConfiguration> dateien) throws IOException {
        var optionen = new HashMap<String, Object>();
        for (String name : DATEIEN) {
            var vorlage = standard.get(name);
            var yaml = dateien.get(name);
            for (String key : vorlage.getKeys(true)) {
                Object normal = vorlage.get(key);
                if (vorlage.isConfigurationSection(key)) {
                    if (yaml.contains(key) && !yaml.isConfigurationSection(key)) {
                        throw new IOException(name + ".yml: Abschnitt erwartet bei " + key);
                    }
                    continue;
                }
                Object wert = yaml.get(key, normal);
                String id = name + "." + key;
                if ((normal instanceof Boolean && !(wert instanceof Boolean))
                        || (normal instanceof Integer && !(wert instanceof Integer))
                        || (normal instanceof Double && !(wert instanceof Number))
                        || (normal instanceof String && !(wert instanceof String))) {
                    throw new IOException(id + ": Ungültiger Werttyp.");
                }
                pruefeWert(id, wert);
                optionen.put(id, wert);
            }
        }
        var basis = new YamlConfiguration();
        basis.set("raster.plotgroesse", optionen.get("world.raster.plotgroesse"));
        basis.set("raster.wegbreite", optionen.get("world.raster.wegbreite"));
        basis.set("claims.pro-spieler", optionen.get("general.claims.pro-spieler"));
        var werte = rasterUndLimit(basis);
        return new Werte(werte.raster(), werte.claimLimit(), optionen);
    }

    private void pruefeWert(String key, Object wert) throws IOException {
        if (wert instanceof Integer zahl) {
            int maximum = switch (key) {
                case "general.profil.name-laenge" -> 32;
                case "general.profil.beschreibung-laenge" -> 120;
                case "world.erdschichten" -> 64;
                case "fly.eigener-plot.hoehe" -> 1024;
                case "fly.pruefintervall-ticks", "world.rand-chunks-pro-tick" -> 100;
                case "updates.intervall-minuten" -> 10080;
                default -> Integer.MAX_VALUE;
            };
            if (zahl < 1 || zahl > maximum) {
                throw new IOException(key + ": Erwartet 1 bis " + maximum);
            }
        }
        if (wert instanceof Number zahl && key.endsWith("geschwindigkeit")
                && (!Double.isFinite(zahl.doubleValue()) || zahl.doubleValue() <= 0 || zahl.doubleValue() > 1)) {
            throw new IOException(key + ": Fluggeschwindigkeit muss größer 0 und höchstens 1 sein.");
        }
        if (key.startsWith("world.material.") || key.equals("protection.zensur-rand")) {
            Material material = Material.matchMaterial((String) wert);
            if (material == null || !festesMaterial.test(material)
                    || (key.endsWith("rand") && !material.name().endsWith("_SLAB"))) {
                throw new IOException(key + ": Festes Blockmaterial ohne Schwerkraft erwartet (Rand: Slab).");
            }
        }
        if (key.equals("updates.kanal") && !List.of("stable", "alpha").contains(wert)) {
            throw new IOException("updates.kanal: stable oder alpha erwartet.");
        }
    }

    private static Werte rasterUndLimit(YamlConfiguration yaml) throws IOException {
        try {
            if (!yaml.isInt("claims.pro-spieler") || !yaml.isInt("raster.plotgroesse") || !yaml.isInt("raster.wegbreite")) {
                throw new IllegalArgumentException("Raster und Claim-Limit müssen Ganzzahlen sein.");
            }
            return new Werte(new PlotRaster(yaml.getInt("raster.plotgroesse"), yaml.getInt("raster.wegbreite")),
                    yaml.getInt("claims.pro-spieler"));
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültiges Raster oder Claim-Limit.", e);
        }
    }

    public PlotRaster raster() { return werte.raster(); }
    public int claimLimit() { return werte.claimLimit(); }
    public Werte werte() { return werte; }
    public boolean aktiv(String key) { return werte.aktiv(key); }
    public int zahl(String key) { return werte.zahl(key); }
    public double dezimal(String key) { return ((Number) werte.optionen().get(key)).doubleValue(); }
    public String text(String key) { return (String) werte.optionen().get(key); }
    public Material material(String key) { return Material.matchMaterial(text(key)); }

    public void rasterSetzen(PlotRaster wert) throws IOException {
        vorbereiten();
        var datei = new YamlDatei(pfad("world"));
        var yaml = datei.laden().orElseThrow(() -> new IOException("world.yml fehlt."));
        yaml.set("raster.plotgroesse", wert.groesse());
        yaml.set("raster.wegbreite", wert.wegbreite());
        datei.speichern(yaml);
        var optionen = new HashMap<>(werte.optionen());
        optionen.put("world.raster.plotgroesse", wert.groesse());
        optionen.put("world.raster.wegbreite", wert.wegbreite());
        werte = new Werte(wert, werte.claimLimit(), optionen);
    }

    public void limitSetzen(int wert) throws IOException {
        if (wert < 1) {
            throw new IllegalArgumentException("Das Claim-Limit muss mindestens 1 sein.");
        }
        vorbereiten();
        var datei = new YamlDatei(pfad("general"));
        var yaml = datei.laden().orElseThrow(() -> new IOException("general.yml fehlt."));
        yaml.set("claims.pro-spieler", wert);
        datei.speichern(yaml);
        var optionen = new HashMap<>(werte.optionen());
        optionen.put("general.claims.pro-spieler", wert);
        werte = new Werte(werte.raster(), wert, optionen);
    }

    public void uebernehmen(Werte neu) { werte = Objects.requireNonNull(neu); }

    public record Werte(PlotRaster raster, int claimLimit, Map<String, Object> optionen) {
        public Werte(PlotRaster raster, int claimLimit) { this(raster, claimLimit, Map.of()); }
        public Werte {
            Objects.requireNonNull(raster, "Plotraster");
            optionen = Map.copyOf(optionen);
            if (claimLimit < 1) {
                throw new IllegalArgumentException("Das Claim-Limit muss mindestens 1 sein.");
            }
        }
        public boolean aktiv(String key) { return (boolean) optionen.get(key); }
        public int zahl(String key) { return (int) optionen.get(key); }
    }
}
