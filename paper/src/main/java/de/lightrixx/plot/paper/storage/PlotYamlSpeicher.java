package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRechte;
import de.lightrixx.plot.storage.PlotSpeicher;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlotYamlSpeicher implements PlotSpeicher {
    private static final int FORMAT = 3;
    private static final int MAX_JOURNALEINTRAEGE = 128;
    private final YamlDatei datei;
    private final YamlDatei journal;
    private final Path grundbestandPfad;
    private Map<PlotId, Plot> basis = Map.of();
    private Map<PlotId, Plot> zuletzt = Map.of();
    private long basisStand;
    private long aktuellerStand;
    private boolean initialisiert;
    private boolean aktuellesFormat;
    private UUID bestandId;

    public PlotYamlSpeicher(Path pfad) {
        this.datei = new YamlDatei(pfad);
        grundbestandPfad = pfad.toAbsolutePath();
        String name = pfad.getFileName().toString();
        String stamm = name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
        journal = new YamlDatei(pfad.resolveSibling(stamm + "-aenderungen.yml"));
    }

    @Override
    public List<Plot> laden() throws IOException {
        var vorhanden = datei.laden();
        if (vorhanden.isEmpty()) {
            var rest = journal.laden();
            if (rest.isPresent()) {
                throw new IOException("Plot-Änderungsjournal ohne zugehörigen Grundbestand gefunden.");
            }
            basis = Map.of();
            zuletzt = Map.of();
            basisStand = 0;
            aktuellerStand = 0;
            aktuellesFormat = false;
            initialisiert = true;
            return List.of();
        }
        var yaml = vorhanden.get();
        var sektion = yaml.getConfigurationSection("plots");
        int version = yaml.getInt("format");
        if (!yaml.isInt("format") || version < 1 || version > FORMAT || sektion == null) {
            throw new IOException("plots.yml hat kein gültiges Plotdatenformat.");
        }

        long stand = version == FORMAT ? standLesen(yaml, "stand") : 0;
        UUID kennung = version == FORMAT ? bestandskennung(yaml) : null;
        Map<PlotId, Plot> grundbestand = plotMap(plotsLesen(sektion, version));
        Map<PlotId, Plot> gesamt = new LinkedHashMap<>(grundbestand);
        long neuerStand = stand;
        var aenderungen = journal.laden();
        if (version == FORMAT && aenderungen.isEmpty()) {
            throw new IOException("Das zugehörige Plot-Änderungsjournal fehlt. Grundbestand und Journal gemeinsam wiederherstellen.");
        }
        if (aenderungen.isPresent() && version != FORMAT && !leeresStartjournal(aenderungen.get())) {
            throw new IOException("Plot-Änderungsjournal passt nicht zum älteren Grundbestand.");
        }
        if (aenderungen.isPresent() && version == FORMAT) {
            var delta = aenderungen.get();
            if (!kennung.equals(bestandskennung(delta))) {
                throw new IOException("Plot-Änderungsjournal gehört zu einem anderen Datenbestand.");
            }
            if (!delta.isInt("format") || delta.getInt("format") != 1) {
                throw new IOException("Ungültiges Plot-Änderungsjournal.");
            }
            long von = standLesen(delta, "basis");
            long bis = standLesen(delta, "stand");
            if (bis < von || von > stand || (bis > stand && von != stand)
                    || (bis == von && !leeresStartjournal(delta))) {
                throw new IOException("Plot-Änderungsjournal und Grundbestand haben unvereinbare Speicherstände.");
            }
            // Nach einer abgeschlossenen Verdichtung darf das ältere Journal liegen bleiben.
            if (bis > stand) {
                var geaendert = delta.getConfigurationSection("plots");
                if (geaendert == null) {
                    throw new IOException("Plots fehlen im Änderungsjournal.");
                }
                try {
                    var entfernt = new HashSet<PlotId>();
                    for (String id : textListe(delta, "entfernt")) {
                        PlotId plotId = PlotId.lesen(id);
                        if (!entfernt.add(plotId) || gesamt.remove(plotId) == null) {
                            throw new IllegalArgumentException("Ungültiger entfernter Plot " + id);
                        }
                    }
                    for (Plot plot : plotMap(plotsLesen(geaendert, FORMAT)).values()) {
                        if (entfernt.contains(plot.id())) {
                            throw new IllegalArgumentException("Plot zugleich geändert und entfernt.");
                        }
                        gesamt.put(plot.id(), plot);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IOException("Ungültige Daten im Plot-Änderungsjournal.", e);
                }
                neuerStand = bis;
            }
        }
        basis = grundbestand;
        zuletzt = gesamt;
        basisStand = stand;
        aktuellerStand = neuerStand;
        aktuellesFormat = version == FORMAT;
        bestandId = kennung;
        initialisiert = true;
        return List.copyOf(gesamt.values());
    }

    private static List<Plot> plotsLesen(ConfigurationSection sektion, int version) throws IOException {
        var plots = new ArrayList<Plot>();
        for (String id : sektion.getKeys(false)) {
            try {
                var plot = sektion.getConfigurationSection(id);
                if (plot == null || !plot.isString("besitzer")) {
                    throw new IllegalArgumentException("Besitzer fehlt.");
                }
                plots.add(lesen(PlotId.lesen(id), plot, version));
            } catch (IllegalArgumentException e) {
                throw new IOException("Ungültiger Plot " + id + " in plots.yml.", e);
            }
        }
        return List.copyOf(plots);
    }

    private static Plot lesen(PlotId id, ConfigurationSection plot, int version) {
        UUID besitzer = YamlWerte.uuid(plot.getString("besitzer"));
        Set<UUID> trusted = spielerListe(plot, "trusted");
        Set<UUID> gebannt = spielerListe(plot, "gebannt");
        if (version == 1) {
            return new Plot(id, besitzer, trusted, gebannt);
        }
        var flags = new HashSet<PlotRechte.BesucherRecht>();
        for (String flag : textListe(plot, "besucherrechte")) {
            if (!flags.add(PlotRechte.BesucherRecht.valueOf(flag))) {
                throw new IllegalArgumentException("Doppeltes Besucherrecht.");
            }
        }
        var rechte = new PlotRechte(trusted, spielerListe(plot, "bauhelfer"), spielerListe(plot, "lager"), gebannt, flags);
        var zellen = new HashSet<PlotId>();
        for (String zelle : textListe(plot, "zellen")) {
            if (!zellen.add(PlotId.lesen(zelle))) {
                throw new IllegalArgumentException("Doppelte Rasterzelle.");
            }
        }
        return new Plot(id, besitzer, rechte, profilLesen(plot.getConfigurationSection("profil")), zellen);
    }

    private static PlotProfil profilLesen(ConfigurationSection profil) {
        if (profil == null || !profil.isString("name") || !profil.isString("beschreibung")
                || (!profil.isInt("gelistet-seit") && !profil.isLong("gelistet-seit"))
                || (profil.contains("kategorie") && !profil.isString("kategorie"))) {
            throw new IllegalArgumentException("Ungültiges Plot-Profil.");
        }
        var kategorie = Optional.ofNullable(profil.getString("kategorie")).map(PlotProfil.Kategorie::valueOf);
        Optional<PlotProfil.Besuchspunkt> punkt = Optional.empty();
        if (profil.contains("besuchspunkt")) {
            var ort = profil.getConfigurationSection("besuchspunkt");
            if (ort == null || !ort.isInt("x") || !ort.isInt("y") || !ort.isInt("z")
                    || !(ort.get("yaw") instanceof Number)) {
                throw new IllegalArgumentException("Ungültiger Besuchspunkt.");
            }
            punkt = Optional.of(new PlotProfil.Besuchspunkt(ort.getInt("x"), ort.getInt("y"), ort.getInt("z"),
                    (float) ort.getDouble("yaw")));
        }
        return new PlotProfil(profil.getString("name"), profil.getString("beschreibung"), kategorie,
                profil.getLong("gelistet-seit"), punkt, spielerListe(profil, "likes"));
    }

    @Override
    public void speichern(List<Plot> plots) throws IOException {
        if (!initialisiert) {
            laden();
        }
        if (aktuellesFormat && !Files.isRegularFile(grundbestandPfad)) {
            throw new IOException("Der Plot-Grundbestand fehlt oder ist keine reguläre Datei: " + grundbestandPfad);
        }
        if (!aktuellesFormat) {
            // Erst den bisherigen Stand unverändert umstellen. Ein Fehler während des
            // ersten eigentlichen Deltas lässt damit den vorherigen Spielstand erhalten.
            var leer = plotsSchreiben(List.of());
            UUID kennung = UUID.randomUUID();
            leer.set("bestand", kennung.toString());
            leer.set("format", 1);
            leer.set("basis", 1);
            leer.set("stand", 1);
            leer.set("entfernt", List.of());
            journal.speichern(leer);
            var grundbestand = plotsSchreiben(List.copyOf(zuletzt.values()));
            grundbestand.set("bestand", kennung.toString());
            grundbestand.set("stand", 1);
            datei.speichern(grundbestand);
            basis = zuletzt;
            basisStand = 1;
            aktuellerStand = 1;
            aktuellesFormat = true;
            bestandId = kennung;
        }
        var neu = plotMap(plots);
        if (aktuellesFormat && neu.equals(zuletzt) && List.copyOf(neu.keySet()).equals(List.copyOf(zuletzt.keySet()))) {
            return;
        }
        long stand = Math.incrementExact(aktuellerStand);
        var geaendert = neu.values().stream().filter(plot -> !plot.equals(basis.get(plot.id()))).toList();
        var entfernt = basis.keySet().stream().filter(id -> !neu.containsKey(id)).toList();
        var replay = new LinkedHashMap<>(basis);
        entfernt.forEach(replay::remove);
        geaendert.forEach(plot -> replay.put(plot.id(), plot));
        if (geaendert.size() + entfernt.size() >= MAX_JOURNALEINTRAEGE
                || !List.copyOf(replay.keySet()).equals(List.copyOf(neu.keySet()))) {
            var yaml = plotsSchreiben(plots);
            yaml.set("bestand", bestandId.toString());
            yaml.set("format", FORMAT);
            yaml.set("stand", stand);
            datei.speichern(yaml);
            basis = neu;
            basisStand = stand;
            aktuellesFormat = true;
        } else {
            var yaml = plotsSchreiben(geaendert);
            yaml.set("bestand", bestandId.toString());
            yaml.set("format", 1);
            yaml.set("basis", basisStand);
            yaml.set("stand", stand);
            yaml.set("entfernt", entfernt.stream().map(PlotId::toString).toList());
            journal.speichern(yaml);
        }
        zuletzt = neu;
        aktuellerStand = stand;
    }

    private static YamlConfiguration plotsSchreiben(List<Plot> plots) {
        var yaml = new YamlConfiguration();
        yaml.set("format", FORMAT);
        var wurzel = yaml.createSection("plots");
        for (Plot plot : plots) {
            var sektion = wurzel.createSection(plot.id().toString());
            sektion.set("besitzer", plot.besitzer().toString());
            sektion.set("trusted", sortierteUuids(plot.rechte().trusted()));
            sektion.set("gebannt", sortierteUuids(plot.rechte().gebannt()));
            sektion.set("bauhelfer", sortierteUuids(plot.rechte().bauhelfer()));
            sektion.set("lager", sortierteUuids(plot.rechte().lager()));
            sektion.set("besucherrechte", plot.rechte().besucher().stream().map(Enum::name).sorted().toList());
            sektion.set("zellen", plot.zellen().stream().map(PlotId::toString).sorted().toList());
            profilSchreiben(sektion.createSection("profil"), plot.profil());
        }
        return yaml;
    }

    private static boolean leeresStartjournal(YamlConfiguration yaml) {
        var plots = yaml.getConfigurationSection("plots");
        return yaml.isInt("format") && yaml.getInt("format") == 1
                && yaml.isInt("basis") && yaml.getInt("basis") == 1
                && yaml.isInt("stand") && yaml.getInt("stand") == 1
                && plots != null && plots.getKeys(false).isEmpty()
                && yaml.isList("entfernt") && yaml.getList("entfernt").isEmpty();
    }

    private static Map<PlotId, Plot> plotMap(List<Plot> plots) throws IOException {
        var ergebnis = new LinkedHashMap<PlotId, Plot>();
        for (Plot plot : plots) {
            if (ergebnis.putIfAbsent(plot.id(), plot) != null) {
                throw new IOException("Doppelte Plot-ID " + plot.id());
            }
        }
        return ergebnis;
    }

    private static long standLesen(YamlConfiguration yaml, String schluessel) throws IOException {
        long wert = yaml.getLong(schluessel);
        if ((!yaml.isInt(schluessel) && !yaml.isLong(schluessel)) || wert < 1) {
            throw new IOException("Ungültiger Speicherstand " + schluessel + " in den Plotdaten.");
        }
        return wert;
    }

    private static UUID bestandskennung(YamlConfiguration yaml) throws IOException {
        try {
            return YamlWerte.uuid(yaml.getString("bestand"));
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültige Bestandskennung in den Plotdaten.", e);
        }
    }

    private static void profilSchreiben(ConfigurationSection sektion, PlotProfil profil) {
        sektion.set("name", profil.name());
        sektion.set("beschreibung", profil.beschreibung());
        sektion.set("gelistet-seit", profil.gelistetSeit());
        profil.kategorie().ifPresent(kategorie -> sektion.set("kategorie", kategorie.name()));
        sektion.set("likes", sortierteUuids(profil.likes()));
        profil.besuchspunkt().ifPresent(punkt -> {
            var ort = sektion.createSection("besuchspunkt");
            ort.set("x", punkt.x());
            ort.set("y", punkt.y());
            ort.set("z", punkt.z());
            ort.set("yaw", (double) punkt.yaw());
        });
    }

    private static Set<UUID> spielerListe(ConfigurationSection sektion, String schluessel) {
        var ergebnis = new HashSet<UUID>();
        for (String wert : textListe(sektion, schluessel)) {
            if (!ergebnis.add(YamlWerte.uuid(wert))) {
                throw new IllegalArgumentException("Doppelte UUID in " + schluessel);
            }
        }
        return ergebnis;
    }

    private static List<String> textListe(ConfigurationSection sektion, String schluessel) {
        List<?> werte = sektion.getList(schluessel);
        if (werte == null) {
            throw new IllegalArgumentException("Liste fehlt: " + schluessel);
        }
        var ergebnis = new ArrayList<String>();
        for (Object wert : werte) {
            if (!(wert instanceof String text)) {
                throw new IllegalArgumentException("Ungültiger Eintrag in " + schluessel);
            }
            ergebnis.add(text);
        }
        return ergebnis;
    }

    private static List<String> sortierteUuids(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().toList();
    }
}
