package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotMenueKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Map;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

public final class PlotBetrieb {
    private final JavaPlugin plugin;
    private final PlotKonfiguration konfiguration;
    private final PlotNachrichten texte;
    private final PlotMenueKonfiguration gui;
    private final PlotMenue menue;
    private final PlotWelt welt;
    private final PlotDienst plots;
    private final WettbewerbDienst wettbewerbe;
    private final PlotAuftraege auftraege;
    private boolean reloadLaeuft;
    private Runnable nachReload = () -> { };
    private java.util.List<PlotBetrieb> verbund;
    private PlotKonfiguration basis;

    public PlotBetrieb(JavaPlugin plugin, PlotKonfiguration konfiguration, PlotNachrichten texte,
                      PlotMenueKonfiguration gui, PlotMenue menue, PlotWelt welt, PlotDienst plots,
                      WettbewerbDienst wettbewerbe, PlotAuftraege auftraege) {
        this.plugin = plugin;
        this.konfiguration = konfiguration;
        this.texte = texte;
        this.gui = gui;
        this.menue = menue;
        this.welt = welt;
        this.plots = plots;
        this.wettbewerbe = wettbewerbe;
        this.auftraege = auftraege;
    }

    public boolean reloadLaeuft() {
        return reloadLaeuft;
    }

    public void nachReload(Runnable auftrag) { nachReload = java.util.Objects.requireNonNull(auftrag); }

    public void verbund(PlotKonfiguration basis, java.util.List<PlotBetrieb> betriebe) {
        this.basis = java.util.Objects.requireNonNull(basis);
        this.verbund = java.util.List.copyOf(betriebe);
    }

    public void neuladen(CommandSender sender) {
        if (verbund != null) { verbundNeuladen(sender); return; }
        if (reloadLaeuft) {
            texte.senden(sender, "admin.reload-laeuft");
            return;
        }
        reloadLaeuft = true;
        auftraege.ausfuehren(sender, "/plot admin reload", this::vorbereiten, neu -> {
            if (welt.rasterFestgelegt() && !welt.raster().equals(neu.einstellungen().raster())) {
                texte.senden(sender, "admin.reload-raster");
                return;
            }
            uebernehmen(konfiguration, texte, gui, neu);
            welt.konfigurationAktualisieren();
            menue.schliessen();
            nachReload.run();
            texte.senden(sender, "admin.reload-erfolg");
        }, () -> reloadLaeuft = false);
    }

    private void verbundNeuladen(CommandSender sender) {
        if (verbund.stream().anyMatch(PlotBetrieb::reloadLaeuft)) { texte.senden(sender, "admin.reload-laeuft"); return; }
        verbund.forEach(betrieb -> betrieb.reloadLaeuft = true);
        auftraege.ausfuehren(sender, "/plot admin reload", () -> {
            var global = basis.vorbereiten();
            var einstellungen = new java.util.LinkedHashMap<PlotBetrieb, PlotKonfiguration.Werte>();
            for (var betrieb : verbund) einstellungen.put(betrieb, betrieb.konfiguration.vorbereiten());
            String standardwelt = (String) global.optionen().get("general.welten.standard");
            if (verbund.stream().noneMatch(betrieb -> betrieb.konfiguration.weltname().equals(standardwelt)))
                throw new IOException("Standardwelt ist nicht registriert; neue Welten benötigen einen Pluginneustart.");
            return new VerbundReload(global, Map.copyOf(einstellungen), texte.vorbereiten(), gui.vorbereiten());
        }, neu -> {
            if (verbund.stream().anyMatch(betrieb -> betrieb.welt.rasterFestgelegt()
                    && !betrieb.welt.raster().equals(neu.einstellungen().get(betrieb).raster()))) {
                texte.senden(sender, "admin.reload-raster"); return;
            }
            basis.uebernehmen(neu.basis());
            texte.uebernehmen(neu.texte()); gui.uebernehmen(neu.gui());
            for (var betrieb : verbund) {
                betrieb.konfiguration.uebernehmen(neu.einstellungen().get(betrieb));
                betrieb.welt.konfigurationAktualisieren(); betrieb.menue.schliessen(); betrieb.nachReload.run();
            }
            texte.senden(sender, "admin.reload-erfolg");
        }, () -> verbund.forEach(betrieb -> betrieb.reloadLaeuft = false));
    }

    public void einrichtung(CommandSender sender) {
        auftraege.ausfuehren(sender, "/plot admin setup", () -> {
            if (basis != null) basis.vorbereiten();
            for (var betrieb : verbund == null ? java.util.List.of(this) : verbund) {
                var neu = betrieb.vorbereiten();
                if (betrieb.welt.rasterFestgelegt() && !betrieb.welt.raster().equals(neu.einstellungen().raster()))
                    throw new IOException("Abweichendes Raster für Welt " + betrieb.konfiguration.weltname());
            }
            return true;
        }, geprueft -> { diagnose(sender); texte.senden(sender, "diagnose.einrichtung"); });
    }

    private Reload vorbereiten() throws IOException {
        return new Reload(konfiguration.vorbereiten(), texte.vorbereiten(), gui.vorbereiten());
    }

    // Nur auf dem Serverthread aufrufen, nachdem sämtliche Dateien erfolgreich geprüft wurden.
    static void uebernehmen(PlotKonfiguration konfiguration, PlotNachrichten texte,
                            PlotMenueKonfiguration gui, Reload neu) {
        konfiguration.uebernehmen(neu.einstellungen());
        texte.uebernehmen(neu.texte());
        gui.uebernehmen(neu.gui());
    }

    public void diagnose(CommandSender sender) {
        var status = auftraege.status();
        version(sender);
        if (verbund != null) texte.senden(sender, "diagnose.welten", unparsed("worlds", verbund.stream()
                .map(betrieb -> betrieb.konfiguration.weltname()).sorted().collect(java.util.stream.Collectors.joining(", "))));
        texte.senden(sender, "diagnose.welt", unparsed("world", welt.schluessel().toString()),
                unparsed("loaded", Boolean.toString(welt.istGeladen())),
                unparsed("size", Integer.toString(welt.raster().groesse())),
                unparsed("road", Integer.toString(welt.raster().wegbreite())));
        texte.senden(sender, "diagnose.daten", unparsed("plots", Integer.toString(plots.anzahlPlots())),
                unparsed("cells", Integer.toString(plots.anzahlZellen())),
                unparsed("entries", Integer.toString(wettbewerbe.aktuell().map(wert -> wert.beitraege().size()).orElse(0))));
        texte.senden(sender, "diagnose.speicher", unparsed("queued", Integer.toString(status.wartend())),
                unparsed("running", Integer.toString(status.laufend())),
                unparsed("success", Long.toString(status.erfolgreich())),
                unparsed("failed", Long.toString(status.fehlgeschlagen())),
                unparsed("duration", Long.toString(status.letzteDauerMillis())));
        texte.senden(sender, "diagnose.zensur", unparsed("count", Long.toString(plots.alle().stream().filter(de.lightrixx.plot.model.Plot::zensiert).count())),
                unparsed("sections", Integer.toString(konfiguration.zahl("protection.zensur-abschnitte-pro-tick"))),
                unparsed("budget", Integer.toString(konfiguration.zahl("protection.zensur-zeitbudget-millis"))));
        texte.senden(sender, "diagnose.hinweis");
        texte.senden(sender, "diagnose.entfernt", unparsed("count", Integer.toString(plots.entfernungen().size())));
    }

    public void version(CommandSender sender) {
        texte.senden(sender, "diagnose.version", unparsed("version", plugin.getPluginMeta().getVersion()),
                unparsed("server", plugin.getServer().getVersion()), unparsed("java", Runtime.version().toString()));
    }

    record Reload(PlotKonfiguration.Werte einstellungen, Map<String, String> texte, PlotMenueKonfiguration.Daten gui) {
    }
    private record VerbundReload(PlotKonfiguration.Werte basis, Map<PlotBetrieb, PlotKonfiguration.Werte> einstellungen,
                                 Map<String, String> texte, PlotMenueKonfiguration.Daten gui) { }
}
