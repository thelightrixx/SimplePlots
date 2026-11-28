package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.storage.YamlDatei;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.paper.PaperPlattform;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class PlotWelt implements Listener {
    // Welten mit Rasterformat 1 behalten ihre Identität für gespeicherte Spielerpositionen.
    private static final NamespacedKey ALTER_WELT_KEY = new NamespacedKey("plotsystem", "plots");
    private static final NamespacedKey RUECKKEHR = new NamespacedKey("simpleplots", "rueckkehr");
    private final Server server;
    private final Plugin plugin;
    private final World spielwelt;
    private final PlotKonfiguration konfiguration;
    private final PlotDienst plots;
    private final PlotWegrand wegrand;
    private final YamlDatei metadaten;
    private final NamespacedKey weltKey;
    private final String name;
    private Optional<PlotRaster> festesRaster;

    public PlotWelt(Plugin plugin, PlotKonfiguration konfiguration, PlotDienst plots, Path pfad) throws IOException {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.spielwelt = server.getWorld(NamespacedKey.minecraft("overworld"));
        if (spielwelt == null) {
            throw new IOException("Die Hauptwelt minecraft:overworld ist nicht geladen.");
        }
        this.konfiguration = konfiguration;
        this.name = konfiguration.weltname();
        this.plots = plots;
        this.wegrand = new PlotWegrand(plots, konfiguration);
        this.metadaten = new YamlDatei(pfad);
        var yaml = metadaten.laden();
        festesRaster = yaml.isPresent() ? Optional.of(rasterLesen(yaml.get())) : Optional.empty();
        if (festesRaster.isEmpty() && (plots.anzahlPlots() > 0 || !plots.entfernungen().isEmpty())) {
            throw new IOException("Gespeicherte Plots ohne welt.yml gefunden. Weltmetadaten und Plotwelt gemeinsam wiederherstellen.");
        }
        weltKey = name.equals("plots") && yaml.filter(daten -> daten.getInt("format") == 1).isPresent()
                ? ALTER_WELT_KEY : new NamespacedKey("simpleplots", name);
        for (Plot plot : plots.alle()) {
            for (PlotId zelle : plot.zellen()) {
                try {
                    raster().mitteX(zelle);
                    raster().mitteZ(zelle);
                    raster().nordwegZ(zelle);
                    Math.toIntExact((long) zelle.x() * raster().rasterweite() + raster().groesse() - 1);
                    Math.toIntExact((long) zelle.z() * raster().rasterweite() + raster().groesse() - 1);
                } catch (ArithmeticException e) {
                    throw new IOException("Plotzelle " + zelle + " liegt außerhalb des darstellbaren Blockrasters.", e);
                }
            }
        }
    }

    public boolean rasterFestgelegt() {
        return festesRaster.isPresent();
    }

    public NamespacedKey schluessel() {
        return weltKey;
    }

    public boolean istGeladen() {
        return server.getWorld(weltKey) != null;
    }

    public PlotRaster raster() {
        return festesRaster.orElseGet(konfiguration::raster);
    }

    public PlotKonfiguration konfiguration() {
        return konfiguration;
    }

    public void vorhandeneWeltLaden() throws IOException {
        if (rasterFestgelegt()) {
            wegrand.vormerken(plugin, Arrays.asList(ladenOderErstellen().getLoadedChunks()), raster());
        } else {
            sicherFreierWeltname();
        }
    }

    public World ladenOderErstellen() throws IOException {
        World geladen = server.getWorld(weltKey);
        if (geladen != null) {
            if (rasterFestgelegt() && geladen.getGenerator() instanceof PlotGenerator) {
                return geladen;
            }
            throw new IOException("Welt " + weltKey + " wurde bereits mit einem anderen Generator geladen.");
        }
        if (!rasterFestgelegt()) {
            sicherFreierWeltname();
            PlotRaster aktuell = konfiguration.raster();
            var yaml = new YamlConfiguration();
            yaml.set("format", 2);
            yaml.set("plotgroesse", aktuell.groesse());
            yaml.set("wegbreite", aktuell.wegbreite());
            metadaten.speichern(yaml);
            festesRaster = Optional.of(aktuell);
        } else if ((plots.anzahlPlots() > 0 || !plots.entfernungen().isEmpty()) && !Files.isDirectory(weltOrdner(weltKey))) {
            throw new IOException("Die Weltdateien für gespeicherte Plots fehlen: " + weltOrdner(weltKey)
                    + ". Plotwelt aus der gemeinsamen Sicherung wiederherstellen.");
        }

        World welt;
        try {
            welt = WorldCreator.ofKey(weltKey)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false)
                    .generator(new PlotGenerator(raster(), konfiguration, plots))
                    .createWorld();
        } catch (RuntimeException e) {
            throw new IOException("Plotwelt " + weltKey + " konnte nicht geladen werden.", e);
        }
        if (welt == null || !istPlotWelt(welt)) {
            throw new IOException("Paper konnte die Plotwelt " + weltKey + " nicht laden.");
        }
        PaperPlattform.konfiguriere(welt);
        return welt;
    }

    public boolean istPlotWelt(World welt) {
        return welt != null && weltKey.equals(welt.getKey());
    }

    @EventHandler
    public void chunkLaden(ChunkLoadEvent event) {
        if (istPlotWelt(event.getWorld())) {
            wegrand.aktualisieren(event.getChunk(), raster());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void wegrandAbbauen(BlockBreakEvent event) {
        var block = event.getBlock();
        if (block.getY() == PlotGenerator.BODEN_Y + 1 && istPlotWelt(block.getWorld())) {
            wegrand.markierungEntfernen(block);
        }
    }

    public void wegrandAktualisieren(World welt, Plot plot) {
        int weite = raster().rasterweite();
        long minX = ((long) plot.zellen().stream().mapToInt(PlotId::x).min().orElseThrow() * weite - 1) >> 4;
        long maxX = (((long) plot.zellen().stream().mapToInt(PlotId::x).max().orElseThrow() + 1) * weite) >> 4;
        long minZ = ((long) plot.zellen().stream().mapToInt(PlotId::z).min().orElseThrow() * weite - 1) >> 4;
        long maxZ = (((long) plot.zellen().stream().mapToInt(PlotId::z).max().orElseThrow() + 1) * weite) >> 4;
        var chunks = Arrays.stream(welt.getLoadedChunks()).filter(chunk -> chunk.getX() >= minX && chunk.getX() <= maxX
                && chunk.getZ() >= minZ && chunk.getZ() <= maxZ).toList();
        wegrand.vormerken(plugin, chunks, raster());
    }

    public void konfigurationAktualisieren() {
        World geladen = server.getWorld(weltKey);
        if (geladen != null) wegrand.vormerken(plugin, Arrays.asList(geladen.getLoadedChunks()), raster());
    }

    public Optional<PlotId> plotBei(Location ort) {
        return ort != null && istPlotWelt(ort.getWorld())
                ? plots.plotBei(raster(), ort.getBlockX(), ort.getBlockZ()) : Optional.empty();
    }

    public Location spawn(World welt) {
        int achse = raster().spawnAchse();
        Location ort = wegOberflaeche(welt, achse, achse);
        return plotBei(ort).map(id -> wegVor(welt, id)).orElse(ort);
    }

    public Location wegVor(World welt, PlotId id) {
        PlotId rand = plots.finden(id).map(Plot::nordrand).orElse(id);
        return wegOberflaeche(welt, raster().mitteX(rand), raster().nordwegZ(rand));
    }

    private Location wegOberflaeche(World welt, int x, int z) {
        Location ort = PlotGenerator.oberflaeche(welt, x, z);
        if (plots.istWegrand(raster(), x, z)) {
            ort.add(0, 0.5, 0);
        }
        return ort;
    }

    public Optional<Location> besuchspunkt(World welt, PlotId id, PlotProfil.Besuchspunkt punkt) {
        Location ziel = new Location(welt, punkt.x() + 0.5, punkt.y(), punkt.z() + 0.5, punkt.yaw(), 0);
        if (!plotBei(ziel).filter(id::equals).isPresent() || !welt.getWorldBorder().isInside(ziel)
                || punkt.y() <= welt.getMinHeight() || punkt.y() >= welt.getMaxHeight() - 1) {
            return Optional.empty();
        }
        var fuss = welt.getBlockAt(punkt.x(), punkt.y(), punkt.z());
        var untergrund = fuss.getRelative(0, -1, 0);
        Material boden = untergrund.getType();
        if (!fuss.getType().isAir() || !fuss.getRelative(0, 1, 0).getType().isAir() || !boden.isSolid()
                || boden == Material.MAGMA_BLOCK || boden == Material.CACTUS || Tag.CAMPFIRES.isTagged(boden)
                || Tag.WALLS.isTagged(boden) || Tag.FENCES.isTagged(boden) || Tag.FENCE_GATES.isTagged(boden)
                || !hatStandflaeche(untergrund)) {
            return Optional.empty();
        }
        return Optional.of(ziel);
    }

    static boolean hatStandflaeche(org.bukkit.block.Block untergrund) {
        return untergrund.getCollisionShape().getBoundingBoxes().stream()
                .anyMatch(box -> box.getMinX() <= 0.5 && box.getMaxX() >= 0.5 && box.getMinZ() <= 0.5 && box.getMaxZ() >= 0.5
                        && box.getMaxY() == 1);
    }

    public Location mitte(World welt, PlotId id) {
        return PlotGenerator.oberflaeche(welt, raster().mitteX(id), raster().mitteZ(id));
    }

    public Location rueckkehr() {
        return spielwelt.getSpawnLocation();
    }

    public void rueckkehrMerken(Player spieler, Location von, Location nach) {
        if (nach == null || von.getWorld() != spielwelt || nach.getWorld() == spielwelt) return;
        spieler.getPersistentDataContainer().set(RUECKKEHR, PersistentDataType.LIST.doubles(),
                List.of(von.getX(), von.getY(), von.getZ(), (double) von.getYaw(), (double) von.getPitch()));
    }

    public Location rueckkehr(Player spieler) {
        var ort = spieler.getPersistentDataContainer().get(RUECKKEHR, PersistentDataType.LIST.doubles());
        if (ort == null || ort.size() != 5 || ort.stream().anyMatch(wert -> !Double.isFinite(wert))
                || ort.get(1) < spielwelt.getMinHeight() || ort.get(1) >= spielwelt.getMaxHeight()
                || Math.abs(ort.get(3)) > Float.MAX_VALUE || Math.abs(ort.get(4)) > 90) return rueckkehr();
        var ziel = new Location(spielwelt, ort.get(0), ort.get(1), ort.get(2), ort.get(3).floatValue(), ort.get(4).floatValue());
        return spielwelt.getWorldBorder().isInside(ziel) ? ziel : rueckkehr();
    }

    private void sicherFreierWeltname() throws IOException {
        if (server.getWorld(name) != null) {
            throw new IOException("Vorhandene Welt " + name + " ohne registriertes SimplePlots-Raster.");
        }
        var keys = name.equals("plots") ? List.of(new NamespacedKey("simpleplots", name), ALTER_WELT_KEY)
                : List.of(new NamespacedKey("simpleplots", name));
        for (var key : keys) {
            if (server.getWorld(key) != null || Files.exists(weltOrdner(key))) {
                throw new IOException("Vorhandene Welt " + key + " ohne registriertes SimplePlots-Raster.");
            }
        }
    }

    private Path weltOrdner(NamespacedKey key) {
        return spielwelt.getWorldPath().resolve("../..").resolve(key.getNamespace()).resolve(key.getKey()).normalize();
    }

    private static PlotRaster rasterLesen(YamlConfiguration yaml) throws IOException {
        int format = yaml.getInt("format");
        if (!yaml.isInt("format") || (format != 1 && format != 2)
                || !yaml.isInt("plotgroesse") || !yaml.isInt("wegbreite")) {
            throw new IOException("welt.yml hat kein gültiges Rasterformat.");
        }
        try {
            return new PlotRaster(yaml.getInt("plotgroesse"), yaml.getInt("wegbreite"));
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültiges gespeichertes Raster in welt.yml.", e);
        }
    }
}
