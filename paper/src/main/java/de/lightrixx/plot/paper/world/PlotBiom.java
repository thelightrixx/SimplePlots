package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.service.PlotDienst;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PlotBiom {
    private static final NamespacedKey SPALTEN = new NamespacedKey("simpleplots", "biom-spalten");
    private static final java.util.Set<String> UNBEKANNT = new java.util.HashSet<>();

    private PlotBiom() { }

    @SuppressWarnings("deprecation")
    static void aktualisieren(Chunk chunk, PlotRaster raster, PlotDienst plots) {
        var daten = chunk.getPersistentDataContainer();
        List<String> bisher = daten.get(SPALTEN, PersistentDataType.LIST.strings());
        if (bisher == null || bisher.size() != 16) bisher = java.util.Collections.nCopies(16, "");
        var neu = new ArrayList<>(bisher);
        boolean geaendert = false;
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                int index = x + z / 4;
                int blockX = chunk.getX() * 16 + x, blockZ = chunk.getZ() * 16 + z;
                String schluessel = plotFuerSpalte(plots, raster, blockX, blockZ).filter(plot -> !plot.zensiert())
                        .flatMap(plot -> plot.profil().biom()).orElse("");
                if (schluessel.equals(bisher.get(index))) continue;
                Biome biom = schluessel.isEmpty() ? Biome.PLAINS : RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)
                        .get(NamespacedKey.fromString(schluessel));
                if (biom == null) {
                    if (UNBEKANNT.add(schluessel)) org.slf4j.LoggerFactory.getLogger(PlotBiom.class)
                            .warn("Gespeichertes Plot-Biom {} ist nicht registriert. Biomdaten und Datapacks prüfen.", schluessel);
                    continue;
                }
                for (int y = chunk.getWorld().getMinHeight(); y < chunk.getWorld().getMaxHeight(); y += 4) {
                    chunk.getWorld().setBiome(blockX, y, blockZ, biom);
                }
                neu.set(index, schluessel);
                geaendert = true;
            }
        }
        if (geaendert) {
            daten.set(SPALTEN, PersistentDataType.LIST.strings(), neu);
            chunk.getWorld().refreshChunk(chunk.getX(), chunk.getZ());
        }
    }

    static Optional<Plot> plotFuerSpalte(PlotDienst plots, PlotRaster raster, int x, int z) {
        var plot = plots.plotBei(raster, x, z).flatMap(plots::finden);
        if (plot.isEmpty()) return Optional.empty();
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                if (!plots.plotBei(raster, x + dx, z + dz).filter(plot.get().id()::equals).isPresent()) return Optional.empty();
            }
        }
        return plot;
    }
}
