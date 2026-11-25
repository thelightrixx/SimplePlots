package de.lightrixx.plot.service;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.model.PlotRechte;
import de.lightrixx.plot.storage.PlotSpeicher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class PlotDienst {
    // Leser erhalten einen vollständigen, unveränderlichen Stand ohne I/O oder Sperren.
    private volatile Bestand bestand;
    private final PlotSpeicher speicher;
    private final PlotKauf kauf;
    private int suchRadius;

    public PlotDienst(PlotSpeicher speicher, PlotKauf kauf) throws IOException {
        this.speicher = Objects.requireNonNull(speicher, "Plotspeicher");
        this.kauf = Objects.requireNonNull(kauf, "Plotkauf");
        var plots = new LinkedHashMap<PlotId, Plot>();
        for (Plot plot : speicher.laden()) {
            if (plots.putIfAbsent(plot.id(), plot) != null) {
                throw new IOException("Doppelte Plot-ID " + plot.id());
            }
        }
        bestand = indexieren(plots);
    }

    public Optional<Plot> finden(PlotId id) {
        var aktuell = bestand;
        return Optional.ofNullable(aktuell.plots().get(aktuell.zellen().getOrDefault(id, id)));
    }

    public Optional<PlotId> plotBei(PlotRaster raster, int x, int z) {
        var zellen = bestand.zellen();
        PlotId zelle = raster.zelleBei(x, z);
        PlotId haupt = zellen.get(zelle);
        boolean wegX = raster.istWegachse(x);
        boolean wegZ = raster.istWegachse(z);
        if (!wegX && !wegZ) {
            return Optional.of(haupt == null ? zelle : haupt);
        }
        if (haupt == null
                || (wegX && !haupt.equals(zellen.get(zelle.verschoben(1, 0))))
                || (wegZ && !haupt.equals(zellen.get(zelle.verschoben(0, 1))))
                || (wegX && wegZ && !haupt.equals(zellen.get(zelle.verschoben(1, 1))))) {
            return Optional.empty();
        }
        return Optional.of(haupt);
    }

    public Optional<Plot> nachName(String name) {
        String schluessel = namenschluessel(name);
        var aktuell = bestand;
        return Optional.ofNullable(aktuell.namen().get(schluessel)).map(aktuell.plots()::get).filter(plot -> !plot.zensiert());
    }

    public boolean istWegrand(PlotRaster raster, int x, int z) {
        if ((!raster.istWegrandachse(x) && !raster.istWegrandachse(z)) || plotBei(raster, x, z).isPresent()) {
            return false;
        }
        // Diagonale Nachbarn schließen die Ecken der Umrandung.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (plotBei(raster, x + dx, z + dz).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Plot> galerie(UUID besucher) {
        return galerie(plot -> plot.darfBetreten(besucher));
    }

    public List<Plot> galerie(Predicate<Plot> sichtbar) {
        return bestand.galerie().stream().filter(sichtbar).toList();
    }

    public List<Plot> vomBesitzer(UUID besitzer) {
        return bestand.besitzer().getOrDefault(besitzer, List.of()).stream().filter(plot -> !plot.zensiert()).toList();
    }

    public List<Plot> alle() {
        return List.copyOf(bestand.plots().values());
    }

    public int anzahlPlots() {
        return bestand.plots().size();
    }

    public int anzahlZellen() {
        return bestand.zellen().size();
    }

    public boolean hatZensiertePlots() { return bestand.hatZensiertePlots(); }

    public synchronized ClaimErgebnis claim(PlotId id, UUID besitzer, int limit) throws IOException {
        return claim(id, new PlotKauf.Kaeufer(besitzer, null), limit);
    }

    public synchronized ClaimErgebnis claim(PlotId id, PlotKauf.Kaeufer kaeufer, int limit) throws IOException {
        Objects.requireNonNull(kaeufer, "Käufer");
        return claimMit(id, kaeufer, limit, kauf);
    }

    /** Zuweisungen durch die Verwaltung buchen keinen Kaufpreis ab. */
    public synchronized ClaimErgebnis claimKostenlos(PlotId id, UUID besitzer, int limit) throws IOException {
        return claimMit(id, new PlotKauf.Kaeufer(besitzer, null), limit, PlotKauf.kostenlos());
    }

    private ClaimErgebnis claimMit(PlotId id, PlotKauf.Kaeufer kaeufer, int limit, PlotKauf zahlung) throws IOException {
        Objects.requireNonNull(id, "Plot-ID");
        if (limit < 1) {
            throw new IllegalArgumentException("Das Claim-Limit muss mindestens 1 sein.");
        }
        var aktuell = bestand;
        if (aktuell.zellen().containsKey(id)) {
            return ClaimErgebnis.BELEGT;
        }
        if (aktuell.belegteZellen().getOrDefault(kaeufer.id(), 0L) >= limit) {
            return ClaimErgebnis.LIMIT;
        }
        Plot plot = Plot.neu(id, kaeufer.id());
        return switch (zahlung.abschliessen(kaeufer, id, () -> uebernehmen(plot))) {
            case GEKAUFT -> ClaimErgebnis.CLAIMED;
            case KEIN_GELD -> ClaimErgebnis.KEIN_GELD;
            case NICHT_VERFUEGBAR -> ClaimErgebnis.WIRTSCHAFT_FEHLT;
        };
    }

    public synchronized PlotId naechsterFreierPlot() {
        var zellen = bestand.zellen();
        // Claims und Merges geben keine Zellen frei; vollständig belegte Ringe bleiben belegt.
        for (int radius = suchRadius; ; radius++) {
            suchRadius = radius;
            for (int x = -radius; x <= radius; x++) {
                var nord = new PlotId(x, -radius);
                if (!zellen.containsKey(nord)) {
                    return nord;
                }
                var sued = new PlotId(x, radius);
                if (!zellen.containsKey(sued)) {
                    return sued;
                }
            }
            for (int z = 1 - radius; z < radius; z++) {
                var west = new PlotId(-radius, z);
                if (!zellen.containsKey(west)) {
                    return west;
                }
                var ost = new PlotId(radius, z);
                if (!zellen.containsKey(ost)) {
                    return ost;
                }
            }
        }
    }

    public synchronized RollenErgebnis rolleAendern(PlotId id, UUID besitzer, UUID ziel, Plot.RollenAktion aktion)
            throws IOException {
        Plot plot = finden(id).orElse(null);
        if (plot == null || !plot.besitzer().equals(besitzer)) {
            return RollenErgebnis.FREMDER_PLOT;
        }
        if (besitzer.equals(ziel)) {
            return RollenErgebnis.BESITZER;
        }
        if (plot.zensiert()) {
            return RollenErgebnis.GESPERRT;
        }
        if ((aktion == Plot.RollenAktion.ADD || aktion == Plot.RollenAktion.STORAGE) && !plot.darfBetreten(ziel)) {
            return RollenErgebnis.GEBANNT;
        }
        Plot geaendert = plot.mitRolle(ziel, aktion);
        if (geaendert.equals(plot)) {
            return RollenErgebnis.UNVERAENDERT;
        }
        uebernehmen(geaendert);
        return RollenErgebnis.GEAENDERT;
    }

    public synchronized Aenderung profilAendern(PlotId id, UUID besitzer, UnaryOperator<PlotProfil> aenderung) throws IOException {
        Plot plot = finden(id).orElse(null);
        if (plot == null || !plot.besitzer().equals(besitzer)) {
            return Aenderung.FREMDER_PLOT;
        }
        if (plot.zensiert()) {
            return Aenderung.GESPERRT;
        }
        PlotProfil profil = aenderung.apply(plot.profil());
        PlotId namensinhaber = bestand.namen().get(namenschluessel(profil.name()));
        if (namensinhaber != null && !namensinhaber.equals(plot.id())) {
            return Aenderung.NAME_VERGEBEN;
        }
        return aendern(plot, plot.mitProfil(profil));
    }

    public synchronized Aenderung besucherRecht(PlotId id, UUID besitzer, PlotRechte.BesucherRecht recht, boolean erlaubt)
            throws IOException {
        Plot plot = finden(id).orElse(null);
        if (plot == null || !plot.besitzer().equals(besitzer)) {
            return Aenderung.FREMDER_PLOT;
        }
        return plot.zensiert() ? Aenderung.GESPERRT
                : aendern(plot, plot.mitRechten(plot.rechte().mitBesucherRecht(recht, erlaubt)));
    }

    public synchronized Aenderung like(PlotId id, UUID spieler, boolean vergeben) throws IOException {
        Plot plot = finden(id).orElse(null);
        if (plot == null || plot.zensiert() || plot.profil().kategorie().isEmpty()) {
            return Aenderung.NICHT_GELISTET;
        }
        if (plot.besitzer().equals(spieler)) {
            return Aenderung.EIGENER_PLOT;
        }
        if (!plot.darfBetreten(spieler)) {
            return Aenderung.GEBANNT;
        }
        return aendern(plot, plot.mitProfil(plot.profil().mitLike(spieler, vergeben)));
    }

    public synchronized MergeErgebnis zusammenlegen(PlotId von, PlotId nach, UUID besitzer, Predicate<PlotId> gesperrt,
                                       boolean bestaetigt) throws IOException {
        Plot ausgang = finden(von).orElse(null);
        Plot ziel = finden(nach).orElse(null);
        if (ausgang == null || ziel == null || !ausgang.besitzer().equals(besitzer) || !ziel.besitzer().equals(besitzer)) {
            return MergeErgebnis.FREMDER_PLOT;
        }
        if (ausgang.id().equals(ziel.id())) {
            return MergeErgebnis.GLEICH;
        }
        if (ausgang.zensiert() || ziel.zensiert() || gesperrt.test(ausgang.id()) || gesperrt.test(ziel.id())) {
            return MergeErgebnis.GESPERRT;
        }
        if (ausgang.zellen().stream().flatMap(zelle -> zelle.nachbarn().stream()).noneMatch(ziel.zellen()::contains)) {
            return MergeErgebnis.KEIN_NACHBAR;
        }
        if (!bestaetigt) {
            return MergeErgebnis.BESTAETIGEN;
        }
        var neu = new LinkedHashMap<>(bestand.plots());
        neu.remove(ziel.id());
        neu.put(ausgang.id(), ausgang.vereinigtMit(ziel));
        speichern(neu);
        return MergeErgebnis.VEREINIGT;
    }

    private Aenderung aendern(Plot alt, Plot neu) throws IOException {
        if (alt.equals(neu)) {
            return Aenderung.UNVERAENDERT;
        }
        uebernehmen(neu);
        return Aenderung.GEAENDERT;
    }

    public synchronized Aenderung zensieren(PlotId id, boolean wert) throws IOException {
        Plot plot = finden(id).orElse(null);
        return plot == null ? Aenderung.FREMDER_PLOT : aendern(plot, plot.mitZensur(wert));
    }

    public synchronized Uebertragung uebertragen(PlotId id, UUID bisher, UUID ziel, int limit,
                                               Predicate<PlotId> gesperrt) throws IOException {
        Objects.requireNonNull(ziel, "Neuer Besitzer");
        if (limit < 1) {
            throw new IllegalArgumentException("Das Claim-Limit muss mindestens 1 sein.");
        }
        Plot plot = finden(id).orElse(null);
        if (plot == null || !plot.besitzer().equals(bisher)) {
            return Uebertragung.FREMDER_PLOT;
        }
        if (plot.zensiert() || gesperrt.test(plot.id())) {
            return Uebertragung.GESPERRT;
        }
        if (bisher.equals(ziel)) {
            return Uebertragung.GLEICH;
        }
        if (bestand.belegteZellen().getOrDefault(ziel, 0L) + plot.zellen().size() > limit) {
            return Uebertragung.LIMIT;
        }
        uebernehmen(plot.mitBesitzer(ziel));
        return Uebertragung.UEBERTRAGEN;
    }

    public synchronized Aenderung freigeben(PlotId id, Predicate<PlotId> gesperrt) throws IOException {
        Plot plot = finden(id).orElse(null);
        if (plot == null) {
            return Aenderung.FREMDER_PLOT;
        }
        if (plot.zensiert() || gesperrt.test(plot.id())) {
            return Aenderung.GESPERRT;
        }
        var neu = new LinkedHashMap<>(bestand.plots());
        neu.remove(plot.id());
        speichern(neu);
        suchRadius = 0;
        return Aenderung.GEAENDERT;
    }

    private void uebernehmen(Plot plot) throws IOException {
        var neu = new LinkedHashMap<>(bestand.plots());
        neu.put(plot.id(), plot);
        speichern(neu);
    }

    private void speichern(Map<PlotId, Plot> neu) throws IOException {
        Bestand index = indexieren(neu);
        speicher.speichern(List.copyOf(neu.values()));
        bestand = index;
    }

    private static Bestand indexieren(Map<PlotId, Plot> plots) throws IOException {
        var index = new HashMap<PlotId, PlotId>();
        var namen = new HashMap<String, PlotId>();
        var besitzer = new HashMap<UUID, List<Plot>>();
        var belegt = new HashMap<UUID, Long>();
        var galerie = new ArrayList<Plot>();
        for (Plot plot : plots.values()) {
            String name = namenschluessel(plot.profil().name());
            if (!name.isEmpty() && namen.putIfAbsent(name, plot.id()) != null) {
                throw new IOException("Doppelter Plotname bei " + plot.id());
            }
            besitzer.computeIfAbsent(plot.besitzer(), id -> new ArrayList<>()).add(plot);
            belegt.merge(plot.besitzer(), (long) plot.zellen().size(), Long::sum);
            if (!plot.zensiert() && plot.profil().kategorie().isPresent()) {
                galerie.add(plot);
            }
            for (PlotId zelle : plot.zellen()) {
                if (index.putIfAbsent(zelle, plot.id()) != null) {
                    throw new IOException("Mehrere Plots beanspruchen die Zelle " + zelle);
                }
            }
        }
        besitzer.replaceAll((id, liste) -> List.copyOf(liste));
        return new Bestand(Collections.unmodifiableMap(new LinkedHashMap<>(plots)), Map.copyOf(index),
                Map.copyOf(namen), Map.copyOf(besitzer), Map.copyOf(belegt), List.copyOf(galerie),
                plots.values().stream().anyMatch(Plot::zensiert));
    }

    private static String namenschluessel(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private record Bestand(Map<PlotId, Plot> plots, Map<PlotId, PlotId> zellen, Map<String, PlotId> namen,
                           Map<UUID, List<Plot>> besitzer, Map<UUID, Long> belegteZellen, List<Plot> galerie, boolean hatZensiertePlots) {
    }

    public enum ClaimErgebnis {
        CLAIMED, BELEGT, LIMIT, KEIN_GELD, WIRTSCHAFT_FEHLT
    }

    public enum RollenErgebnis {
        GEAENDERT, UNVERAENDERT, FREMDER_PLOT, BESITZER, GEBANNT, GESPERRT
    }

    public enum Aenderung {
        GEAENDERT, UNVERAENDERT, FREMDER_PLOT, NAME_VERGEBEN, NICHT_GELISTET, EIGENER_PLOT, GEBANNT, GESPERRT
    }

    public enum Uebertragung {
        UEBERTRAGEN, FREMDER_PLOT, GESPERRT, GLEICH, LIMIT
    }

    public enum MergeErgebnis {
        VEREINIGT, BESTAETIGEN, FREMDER_PLOT, GLEICH, GESPERRT, KEIN_NACHBAR
    }
}
