package de.lightrixx.plot.paper;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.paper.command.PlotAuftraege;
import de.lightrixx.plot.paper.command.PlotBefehl;
import de.lightrixx.plot.paper.command.PlotBetrieb;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotMenueKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
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
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public final class SimplePlots extends JavaPlugin {
    private static final String ALTER_PLUGINNAME = "PlotSystem";
    private PlotAuftraege auftraege;
    private PlotMenue menue;
    private PlotFlug flug;
    private PlotUpdater updater;

    @Override
    public void onEnable() {
        try {
            var datenordner = getDataFolder().toPath();
            var pluginManager = getServer().getPluginManager();
            if (pluginManager.getPlugin(ALTER_PLUGINNAME) != null) {
                throw new IOException("Ersetze die PlotSystem-JAR bei gestopptem Server durch SimplePlots.");
            }
            if (uebernehmeDatenordner(datenordner)) {
                getSLF4JLogger().info("PlotSystem-Datenordner nach {} übernommen.", datenordner);
            }
            saveDefaultConfig();
            var konfiguration = new PlotKonfiguration(datenordner.resolve("config.yml"));
            var texte = new PlotNachrichten(this);
            var gui = new PlotMenueKonfiguration(this);
            var plots = new PlotDienst(new PlotYamlSpeicher(datenordner.resolve("plots.yml")), PlotKauf.kostenlos());
            var welt = new PlotWelt(this, konfiguration, plots, datenordner.resolve("welt.yml"));
            var wettbewerbe = new WettbewerbDienst(new WettbewerbYamlSpeicher(datenordner.resolve("wettbewerb.yml")), plots);
            auftraege = new PlotAuftraege(this, texte);
            menue = new PlotMenue(this, plots, welt, texte, wettbewerbe, gui, auftraege);
            var betrieb = new PlotBetrieb(this, konfiguration, texte, gui, menue, welt, plots, wettbewerbe, auftraege);
            flug = new PlotFlug(this, welt, plots, texte);
            updater = new PlotUpdater(this, konfiguration, texte, datenordner.getParent().resolve(getFile().getName()));
            var zensur = new PlotZensur(this, welt, plots, auftraege, texte, menue::schliessen);
            betrieb.nachReload(() -> updater.planen(false));

            pluginManager.registerEvents(new PlotBauListener(welt, plots, wettbewerbe), this);
            pluginManager.registerEvents(new PlotMechanikListener(welt, plots, wettbewerbe), this);
            pluginManager.registerEvents(new PlotZutrittListener(this, welt, plots, texte), this);
            pluginManager.registerEvents(welt, this);
            pluginManager.registerEvents(menue, this);
            pluginManager.registerEvents(flug, this);
            pluginManager.registerEvents(zensur, this);
            welt.vorhandeneWeltLaden();
            zensur.fortsetzen();
            updater.planen(true);

            var befehl = new PlotBefehl(getServer(), getSLF4JLogger(), plots, welt, konfiguration, texte, menue, wettbewerbe,
                    auftraege, betrieb);
            befehl.funktionen(flug, updater, zensur);
            var plotBefehl = Objects.requireNonNull(getCommand("plot"), "/plot fehlt in plugin.yml");
            plotBefehl.setExecutor(befehl);
            plotBefehl.setTabCompleter(befehl);
            getSLF4JLogger().info("SimplePlots {} bereit: {} Plots mit {} Rasterzellen, YAML-Speicherung über I/O-Warteschlange.",
                    getPluginMeta().getVersion(), plots.anzahlPlots(), plots.anzahlZellen());
        } catch (IOException | RuntimeException e) {
            getSLF4JLogger().error("SimplePlots konnte nicht gestartet werden.", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (flug != null) flug.beenden();
        if (updater != null) updater.beenden();
        if (menue != null) {
            menue.schliessen();
        }
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
