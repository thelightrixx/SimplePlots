package de.lightrixx.plot.paper;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.paper.command.PlotBefehl;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.listener.PlotBauListener;
import de.lightrixx.plot.paper.listener.PlotMechanikListener;
import de.lightrixx.plot.paper.listener.PlotZutrittListener;
import de.lightrixx.plot.paper.storage.PlotYamlSpeicher;
import de.lightrixx.plot.paper.storage.WettbewerbYamlSpeicher;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

public final class SimplePlots extends JavaPlugin {
    private static final String ALTER_PLUGINNAME = "PlotSystem";

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
            var plots = new PlotDienst(new PlotYamlSpeicher(datenordner.resolve("plots.yml")), PlotKauf.kostenlos());
            var welt = new PlotWelt(getServer(), konfiguration, plots, datenordner.resolve("welt.yml"));
            var wettbewerbe = new WettbewerbDienst(new WettbewerbYamlSpeicher(datenordner.resolve("wettbewerb.yml")), plots);
            var menue = new PlotMenue(this, plots, welt, texte, wettbewerbe);

            pluginManager.registerEvents(new PlotBauListener(welt, plots, wettbewerbe), this);
            pluginManager.registerEvents(new PlotMechanikListener(welt, plots, wettbewerbe), this);
            pluginManager.registerEvents(new PlotZutrittListener(this, welt, plots, texte), this);
            pluginManager.registerEvents(welt, this);
            pluginManager.registerEvents(menue, this);
            welt.vorhandeneWeltLaden();

            var befehl = new PlotBefehl(getServer(), getSLF4JLogger(), plots, welt, konfiguration, texte, menue, wettbewerbe);
            var plotBefehl = Objects.requireNonNull(getCommand("plot"), "/plot fehlt in plugin.yml");
            plotBefehl.setExecutor(befehl);
            plotBefehl.setTabCompleter(befehl);
        } catch (IOException | RuntimeException e) {
            getSLF4JLogger().error("SimplePlots konnte nicht gestartet werden.", e);
            getServer().getPluginManager().disablePlugin(this);
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
