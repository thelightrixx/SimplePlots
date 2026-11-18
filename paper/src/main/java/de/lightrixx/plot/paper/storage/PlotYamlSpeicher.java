package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRechte;
import de.lightrixx.plot.storage.PlotSpeicher;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlotYamlSpeicher implements PlotSpeicher {
    private static final int FORMAT = 2;
    private final YamlDatei datei;

    public PlotYamlSpeicher(Path pfad) {
        this.datei = new YamlDatei(pfad);
    }

    @Override
    public List<Plot> laden() throws IOException {
        var vorhanden = datei.laden();
        if (vorhanden.isEmpty()) {
            return List.of();
        }
        var yaml = vorhanden.get();
        var sektion = yaml.getConfigurationSection("plots");
        int version = yaml.getInt("format");
        if (!yaml.isInt("format") || (version != 1 && version != FORMAT) || sektion == null) {
            throw new IOException("plots.yml hat kein gültiges Plotdatenformat.");
        }

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
        UUID besitzer = UUID.fromString(plot.getString("besitzer"));
        Set<UUID> trusted = spielerListe(plot, "trusted");
        Set<UUID> gebannt = spielerListe(plot, "gebannt");
        if (version == 1) {
            return new Plot(id, besitzer, trusted, gebannt);
        }
        var flags = new HashSet<PlotRechte.BesucherRecht>();
        for (String flag : textListe(plot, "besucherrechte")) {
            flags.add(PlotRechte.BesucherRecht.valueOf(flag));
        }
        var rechte = new PlotRechte(trusted, spielerListe(plot, "bauhelfer"), spielerListe(plot, "lager"), gebannt, flags);
        var zellen = new HashSet<PlotId>();
        for (String zelle : textListe(plot, "zellen")) {
            zellen.add(PlotId.lesen(zelle));
        }
        return new Plot(id, besitzer, rechte, profilLesen(plot.getConfigurationSection("profil")), zellen);
    }

    private static PlotProfil profilLesen(ConfigurationSection profil) {
        if (profil == null || !profil.isString("name") || !profil.isString("beschreibung")
                || (!profil.isInt("gelistet-seit") && !profil.isLong("gelistet-seit"))) {
            throw new IllegalArgumentException("Ungültiges Plot-Profil.");
        }
        var kategorie = Optional.ofNullable(profil.getString("kategorie")).map(PlotProfil.Kategorie::valueOf);
        Optional<PlotProfil.Besuchspunkt> punkt = Optional.empty();
        if (profil.contains("besuchspunkt")) {
            var ort = profil.getConfigurationSection("besuchspunkt");
            if (ort == null || !ort.isInt("x") || !ort.isInt("y") || !ort.isInt("z") || !ort.isDouble("yaw")) {
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
        datei.speichern(yaml);
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
            ergebnis.add(UUID.fromString(wert));
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
