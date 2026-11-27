package de.lightrixx.plot.paper;

import de.lightrixx.plot.paper.command.PlotAuftraege;
import de.lightrixx.plot.paper.command.PlotBefehl;
import de.lightrixx.plot.paper.command.PlotBetrieb;
import de.lightrixx.plot.paper.command.PlotWeltenBefehl;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotMenueKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.economy.EconomySystemWirtschaft;
import de.lightrixx.plot.paper.economy.PlotKaeufer;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.listener.PlotBauListener;
import de.lightrixx.plot.paper.listener.PlotMechanikListener;
import de.lightrixx.plot.paper.listener.PlotZutrittListener;
import de.lightrixx.plot.paper.listener.PlotFlug;
import de.lightrixx.plot.paper.update.PlotUpdater;
import de.lightrixx.plot.paper.storage.PlotYamlSpeicher;
import de.lightrixx.plot.paper.storage.WettbewerbYamlSpeicher;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.paper.world.PlotZensur;
import de.lightrixx.plot.paper.world.PlotEntfernen;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import de.lightrixx.plot.util.StartBanner;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class SimplePlots extends JavaPlugin {
    private static final String ALTER_PLUGINNAME = "PlotSystem";
    private PlotAuftraege auftraege;
    private final List<PlotMenue> menues = new ArrayList<>();
    private final List<PlotFlug> fluege = new ArrayList<>();
    private final List<PlotEntfernen> entfernungen = new ArrayList<>();
    private PlotUpdater updater;

    @Override
    public void onEnable() {
        try {
            for (String zeile : StartBanner.zeilen(getPluginMeta().getVersion(), getServer().getName())) {
                getSLF4JLogger().info(zeile);
            }
            var datenordner = getDataFolder().toPath();
            var pluginManager = getServer().getPluginManager();
            if (pluginManager.getPlugin(ALTER_PLUGINNAME) != null) {
                throw new IOException("Ersetze die PlotSystem-JAR bei gestopptem Server durch SimplePlots.");
            }
            if (uebernehmeDatenordner(datenordner)) {
                getSLF4JLogger().info("PlotSystem-Datenordner nach {} übernommen.", datenordner);
            }
            var konfiguration = new PlotKonfiguration(datenordner.resolve("config.yml"));
            var texte = new PlotNachrichten(this);
            var gui = new PlotMenueKonfiguration(this);
            konfiguration.altbestandArchivieren();
            var rechte = new PlotRangBuendel(pluginManager);
            rechte.uebernehmen(konfiguration.werte());
            auftraege = new PlotAuftraege(this, texte);
            updater = new PlotUpdater(this, konfiguration, texte, datenordner.getParent().resolve(getFile().getName()));
            var kontexte = new LinkedHashMap<String, PlotWeltenBefehl.Kontext>();
            var betriebe = new ArrayList<PlotBetrieb>();
            var zutritte = new ArrayList<PlotZutrittListener>();
            var zensuren = new ArrayList<PlotZensur>();
            // Zuerst alle Konfigurationen prüfen, bevor eine Welt geladen wird.
            for (var config : konfiguration.weltenLaden()) {
                var ablage = config.datenordner();
                var wirtschaft = new EconomySystemWirtschaft(config, getSLF4JLogger());
                var plots = new PlotDienst(new PlotYamlSpeicher(ablage.resolve("plots.yml")),
                        new PlotKaeufer(wirtschaft, config, getSLF4JLogger()));
                var welt = new PlotWelt(this, config, plots, ablage.resolve("welt.yml"));
                var wettbewerbe = new WettbewerbDienst(new WettbewerbYamlSpeicher(ablage.resolve("wettbewerb.yml")), plots);
                var menue = new PlotMenue(this, plots, welt, texte, wettbewerbe, gui, auftraege);
                menues.add(menue);
                var betrieb = new PlotBetrieb(this, config, texte, gui, menue, welt, plots, wettbewerbe, auftraege);
                betriebe.add(betrieb);
                var flug = new PlotFlug(this, welt, plots, texte);
                fluege.add(flug);
                var zensur = new PlotZensur(this, welt, plots, auftraege, texte, menue::schliessen);
                zensuren.add(zensur);
                var entfernen = new PlotEntfernen(this, welt, plots, auftraege, texte, menue::schliessen);
                entfernen.sperre(wettbewerbe::gesperrt);
                entfernungen.add(entfernen);
                betrieb.nachReload(() -> {
                    updater.planen(false); wirtschaft.neuBinden(); rechte.uebernehmen(konfiguration.werte());
                });
                var zutritt = new PlotZutrittListener(this, welt, plots, texte);
                zutritte.add(zutritt);
                pluginManager.registerEvents(new PlotBauListener(welt, plots, wettbewerbe), this);
                pluginManager.registerEvents(new PlotMechanikListener(welt, plots, wettbewerbe), this);
                pluginManager.registerEvents(zutritt, this);
                pluginManager.registerEvents(welt, this);
                pluginManager.registerEvents(menue, this);
                pluginManager.registerEvents(flug, this);
                pluginManager.registerEvents(zensur, this);
                pluginManager.registerEvents(entfernen, this);
                var befehl = new PlotBefehl(getServer(), getSLF4JLogger(), plots, welt, config, texte, menue, wettbewerbe,
                        auftraege, betrieb, wirtschaft);
                befehl.funktionen(flug, updater, zensur, entfernen);
                kontexte.put(config.weltname(), new PlotWeltenBefehl.Kontext(welt, befehl));
                getSLF4JLogger().info("Plotwelt {} bereit: {} Plots mit {} Rasterzellen, {} entfernte Flächen.",
                        config.weltname(), plots.anzahlPlots(), plots.anzahlZellen(), plots.entfernungen().size());
            }
            for (var betrieb : betriebe) betrieb.verbund(konfiguration, betriebe);
            for (var eintrag : kontexte.entrySet()) {
                int index = new ArrayList<>(kontexte.keySet()).indexOf(eintrag.getKey());
                java.util.function.Predicate<org.bukkit.World> anderePlotwelt = welt -> kontexte.values().stream()
                        .anyMatch(kontext -> kontext != eintrag.getValue() && kontext.welt().istPlotWelt(welt));
                fluege.get(index).anderePlotwelten(anderePlotwelt);
                zutritte.get(index).anderePlotwelten(anderePlotwelt);
                eintrag.getValue().welt().vorhandeneWeltLaden();
                zensuren.get(index).fortsetzen(); entfernungen.get(index).fortsetzen();
            }
            updater.planen(true);
            var befehl = new PlotWeltenBefehl(kontexte, () -> konfiguration.text("general.welten.standard"), texte);
            var plotBefehl = Objects.requireNonNull(getCommand("plot"), "/plot fehlt in plugin.yml");
            plotBefehl.setExecutor(befehl);
            plotBefehl.setTabCompleter(befehl);
            getSLF4JLogger().info("SimplePlots {} bereit: {} Plotwelten, gemeinsame YAML-I/O-Warteschlange.",
                    getPluginMeta().getVersion(), kontexte.size());
        } catch (IOException | RuntimeException e) {
            getSLF4JLogger().error("SimplePlots konnte nicht gestartet werden.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        fluege.forEach(PlotFlug::beenden);
        entfernungen.forEach(PlotEntfernen::beenden);
        if (updater != null) updater.beenden();
        menues.forEach(PlotMenue::schliessen);
        if (auftraege != null && !auftraege.beenden(Duration.ofSeconds(15))) {
            var status = auftraege.status();
            getSLF4JLogger().error("Speicherung nach 15 Sekunden noch nicht beendet: {} laufende und {} wartende Aufträge. "
                    + "Dateien vor einem Neustart prüfen.", status.laufend(), status.wartend());
        }
    }

    static boolean uebernehmeDatenordner(Path ziel) throws IOException {
        Path alt = ziel.resolveSibling(ALTER_PLUGINNAME);
        if (Files.notExists(alt, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (var eintraege = Files.list(alt)) {
            if (eintraege.findAny().isEmpty()) {
                return false;
            }
        }
        try {
            if (Files.isDirectory(ziel, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(ziel);
            }
            Files.move(alt, ziel);
            return true;
        } catch (DirectoryNotEmptyException | FileAlreadyExistsException e) {
            throw new IOException("Zwei Plugin-Datenordner vorhanden: " + alt + " und " + ziel
                    + ". Prüfe die Datenbestände bei gestopptem Server.", e);
        }
    }
}
