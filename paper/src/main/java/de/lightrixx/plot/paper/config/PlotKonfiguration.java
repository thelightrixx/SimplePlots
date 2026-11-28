package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.storage.YamlDatei;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
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
    private final Path weltDatei;
    private final Map<String, YamlConfiguration> standard = new LinkedHashMap<>();
    private volatile Werte werte;
    private final java.util.function.Predicate<Material> festesMaterial;

    public PlotKonfiguration(Path bisher) throws IOException {
        this(bisher, material -> material.isBlock() && material.isSolid() && !material.hasGravity());
    }

    public PlotKonfiguration(Path bisher, java.util.function.Predicate<Material> festesMaterial) throws IOException {
        weltDatei = null;
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
            boolean fehlt = standardsErgaenzen(standard.get(name), yaml) || Files.notExists(pfad(name));
            if (fehlt) {
                new YamlDatei(pfad(name)).speichern(yaml);
            }
        }
    }

    private Path pfad(String name) { return ordner.resolve(name + ".yml"); }

    private PlotKonfiguration(PlotKonfiguration basis, Path datei) throws IOException {
        ordner = basis.ordner;
        weltDatei = datei;
        festesMaterial = basis.festesMaterial;
        standard.putAll(basis.standard);
        werte = vorbereiten();
    }

    public PlotKonfiguration fuerWelt(String name) throws IOException {
        if (!name.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IOException("Ungültiger Plotweltname: " + name);
        return new PlotKonfiguration(this, ordner.getParent().resolve("worlds").resolve(name + ".yml"));
    }

    public String weltname() {
        return weltDatei == null ? "plots" : weltDatei.getFileName().toString().replaceFirst("\\.yml$", "");
    }

    /** Bei bestehenden Installationen bleibt die ursprüngliche Datenablage unverändert. */
    public Path datenordner() {
        return weltname().equals("plots") ? ordner.getParent() : ordner.getParent().resolve("data").resolve(weltname());
    }

    public List<PlotKonfiguration> weltenLaden() throws IOException {
        Path welten = ordner.getParent().resolve("worlds");
        if (Files.notExists(welten)) {
            var yaml = new YamlConfiguration();
            yaml.options().setHeader(List.of("Plotwelt plots. Fehlende Werte erben die Einstellungen aus config/.",
                    "Weitere Welten: eine YAML-Datei pro Welt anlegen; Übernahme beim nächsten Pluginstart.",
                    "Abschnitte: general, world, protection, fly und contests. Raster nach Erstellung fest."));
            yaml.set("world.raster.plotgroesse", raster().groesse());
            yaml.set("world.raster.wegbreite", raster().wegbreite());
            new YamlDatei(welten.resolve("plots.yml")).speichern(yaml);
        }
        var ergebnis = new java.util.ArrayList<PlotKonfiguration>();
        try (var dateien = Files.list(welten)) {
            for (Path datei : dateien.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                ergebnis.add(fuerWelt(datei.getFileName().toString().replaceFirst("\\.yml$", "")));
            }
        }
        if (ergebnis.stream().noneMatch(config -> config.weltname().equals(text("general.welten.standard")))) {
            throw new IOException("Die konfigurierte Standardwelt general.welten.standard fehlt in worlds/.");
        }
        return List.copyOf(ergebnis);
    }

    static boolean standardsErgaenzen(YamlConfiguration standard, YamlConfiguration geladen) {
        String vorher = geladen.saveToString();
        for (String key : standard.getKeys(true)) {
            if (!standard.isConfigurationSection(key) && !geladen.contains(key, true)) geladen.set(key, standard.get(key));
        }
        for (String key : standard.getKeys(true)) {
            if (!standard.getComments(key).isEmpty()) geladen.setComments(key, standard.getComments(key));
            if (!standard.getInlineComments(key).isEmpty()) geladen.setInlineComments(key, standard.getInlineComments(key));
        }
        return !vorher.equals(geladen.saveToString());
    }

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
        if (weltDatei != null) {
            var welt = new YamlDatei(weltDatei).laden().orElseThrow(() -> new IOException("Weltkonfiguration fehlt: " + weltDatei));
            for (String name : welt.getKeys(false)) {
                if (!DATEIEN.contains(name) || name.equals("updates") || !welt.isConfigurationSection(name))
                    throw new IOException(weltDatei + ": Unbekannter Weltabschnitt " + name);
                for (String key : welt.getConfigurationSection(name).getKeys(true)) {
                    if (welt.isConfigurationSection(name + "." + key)) continue;
                    if (!standard.get(name).contains(key) || key.startsWith("welten.") || key.startsWith("rang-buendel."))
                        throw new IOException(weltDatei + ": Unbekannte oder globale Option " + name + "." + key);
                    geladen.get(name).set(key, welt.get(name + "." + key));
                }
            }
            return pruefen(geladen);
        }
        Werte neu = pruefen(geladen);
        for (String name : DATEIEN) {
            if (standardsErgaenzen(standard.get(name), geladen.get(name))) new YamlDatei(pfad(name)).speichern(geladen.get(name));
        }
        return neu;
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
                        || (normal instanceof String && !(wert instanceof String))
                        || (normal instanceof List<?> && !(wert instanceof List<?>))
                        || (wert instanceof List<?> liste && liste.stream().anyMatch(Objects::isNull))) {
                    throw new IOException(id + ": Ungültiger Werttyp.");
                }
                pruefeWert(id, wert);
                optionen.put(id, wert instanceof List<?> liste ? List.copyOf(liste) : wert);
            }
        }
        var buendel = new LinkedHashMap<String, List<String>>();
        var gruppen = dateien.get("general").getConfigurationSection("rang-buendel");
        if (gruppen != null) {
            for (String gruppe : gruppen.getKeys(false)) {
                Object roh = gruppen.get(gruppe);
                if (!gruppe.matches("[a-z0-9][a-z0-9_-]{0,31}") || !(roh instanceof List<?> liste)
                        || liste.stream().anyMatch(wert -> !(wert instanceof String text)
                        || !text.matches("simpleplots\\.[a-z0-9_.-]+") || text.startsWith("simpleplots.rang.")))
                    throw new IOException("general.rang-buendel: Ungültige Gruppe oder Berechtigung bei " + gruppe);
                buendel.put(gruppe, List.copyOf(gruppen.getStringList(gruppe)));
            }
        } else if (dateien.get("general").contains("rang-buendel")) throw new IOException("general.rang-buendel: Abschnitt erwartet.");
        // Fehlende Standardgruppen erben die Vorgabe; ausdrücklich leere Listen bleiben leer.
        for (String gruppe : standard.get("general").getConfigurationSection("rang-buendel").getKeys(false))
            buendel.putIfAbsent(gruppe, List.copyOf(standard.get("general").getStringList("rang-buendel." + gruppe)));
        optionen.put("general.rang-buendel", Map.copyOf(buendel));
        var basis = new YamlConfiguration();
        basis.set("raster.plotgroesse", optionen.get("world.raster.plotgroesse"));
        basis.set("raster.wegbreite", optionen.get("world.raster.wegbreite"));
        basis.set("claims.pro-spieler", optionen.get("general.claims.pro-spieler"));
        var werte = rasterUndLimit(basis);
        if ((int) optionen.get("general.navigation.grenzen-sekunden") > (int) optionen.get("general.navigation.grenzen-max-sekunden")) throw new IOException("navigation.grenzen-sekunden darf die Höchstdauer nicht überschreiten.");
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
                case "general.wirtschaft.zeitlimit-millis" -> 30000;
                case "general.navigation.lesezeichen-limit" -> 100;
                case "general.navigation.grenzen-sekunden", "general.navigation.grenzen-max-sekunden" -> 60;
                case "general.navigation.grenzen-reichweite", "general.navigation.grenzen-max-spieler" -> 64;
                case "general.navigation.grenzen-punktabstand" -> 16;
                case "protection.zensur-abschnitte-pro-tick" -> 32;
                case "protection.zensur-zeitbudget-millis" -> 20;
                default -> Integer.MAX_VALUE;
            };
            int minimum = key.equals("general.wirtschaft.zeitlimit-millis") ? 100 : 1;
            if (zahl < minimum || zahl > maximum) {
                throw new IOException(key + ": Erwartet " + minimum + " bis " + maximum);
            }
        }
        if (key.equals("general.wirtschaft.preis")) {
            try {
                if (new BigDecimal((String) wert).signum() < 0) {
                    throw new NumberFormatException("negativ");
                }
            } catch (NumberFormatException e) {
                throw new IOException(key + ": Betrag als Text mit Punkt als Dezimaltrennzeichen erwartet, etwa \"250.00\".");
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
        if (key.equals("general.betreten.anzeige") && !List.of("actionbar", "title").contains(wert)) {
            throw new IOException(key + ": actionbar oder title erwartet.");
        }
        if (key.equals("updates.kanal") && !List.of("stable", "beta", "alpha").contains(wert)) {
            throw new IOException("updates.kanal: stable, beta oder alpha erwartet.");
        }
        if (key.equals("general.welten.standard") && !((String) wert).matches("[a-z0-9][a-z0-9_-]{0,63}"))
            throw new IOException(key + ": Ungültiger Weltname.");
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
    public BigDecimal betrag(String key) { return new BigDecimal(text(key)); }
    public String text(String key) { return (String) werte.optionen().get(key); }
    public Material material(String key) { return Material.matchMaterial(text(key)); }

    public void rasterSetzen(PlotRaster wert) throws IOException {
        vorbereiten();
        var datei = new YamlDatei(weltDatei == null ? pfad("world") : weltDatei);
        var yaml = datei.laden().orElseThrow(() -> new IOException("world.yml fehlt."));
        String prefix = weltDatei == null ? "" : "world.";
        yaml.set(prefix + "raster.plotgroesse", wert.groesse());
        yaml.set(prefix + "raster.wegbreite", wert.wegbreite());
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
        var datei = new YamlDatei(weltDatei == null ? pfad("general") : weltDatei);
        var yaml = datei.laden().orElseThrow(() -> new IOException("general.yml fehlt."));
        yaml.set((weltDatei == null ? "" : "general.") + "claims.pro-spieler", wert);
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
