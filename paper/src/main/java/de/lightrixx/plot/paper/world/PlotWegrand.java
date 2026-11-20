package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlotWegrand {
    private static final NamespacedKey RAND = new NamespacedKey("simpleplots", "wegrand");
    private static final NamespacedKey RAND_TYP = new NamespacedKey("simpleplots", "wegrand-material");
    private static final Material RAND_MATERIAL = Material.STONE_BRICK_SLAB;
    private static final int HOEHE = PlotGenerator.BODEN_Y + 1;
    private static final byte KEIN_RAND = 0;
    private static final byte VORGESEHEN = 1;
    private static final byte GESETZT = 2;
    private static final int CHUNKS_PRO_TICK = 8;
    private final PlotDienst plots;
    private final PlotKonfiguration konfiguration;
    private final Map<Long, RandAuftrag> ausstehend = new LinkedHashMap<>();
    private boolean geplant;

    PlotWegrand(PlotDienst plots) {
        this(plots, null);
    }

    PlotWegrand(PlotDienst plots, PlotKonfiguration konfiguration) {
        this.plots = plots;
        this.konfiguration = konfiguration;
    }

    void vormerken(Plugin plugin, List<Chunk> chunks, PlotRaster raster) {
        for (Chunk chunk : chunks) {
            long key = ((long) chunk.getX() << 32) | (chunk.getZ() & 0xffffffffL);
            ausstehend.put(key, new RandAuftrag(chunk, raster));
        }
        if (!geplant && !ausstehend.isEmpty()) {
            geplant = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> abarbeiten(plugin));
        }
    }

    private void abarbeiten(Plugin plugin) {
        var iterator = ausstehend.entrySet().iterator();
        int budget = konfiguration == null ? CHUNKS_PRO_TICK : konfiguration.zahl("world.rand-chunks-pro-tick");
        for (int i = 0; i < budget && iterator.hasNext(); i++) {
            var auftrag = iterator.next().getValue();
            iterator.remove();
            if (auftrag.chunk().isLoaded()) {
                aktualisieren(auftrag.chunk(), auftrag.raster());
            }
        }
        geplant = !ausstehend.isEmpty();
        if (geplant) {
            plugin.getServer().getScheduler().runTask(plugin, () -> abarbeiten(plugin));
        }
    }

    void aktualisieren(Chunk chunk, PlotRaster raster) {
        var daten = chunk.getPersistentDataContainer();
        byte[] rand = daten.get(RAND, PersistentDataType.BYTE_ARRAY);
        if (rand == null || rand.length != 256) {
            rand = new byte[256];
        }
        boolean geaendert = false;
        Material material = konfiguration == null ? RAND_MATERIAL : konfiguration.material("world.material.rand");
        Material weg = konfiguration == null ? Material.STONE_BRICKS : konfiguration.material("world.material.weg");
        String gespeichert = daten.get(RAND_TYP, PersistentDataType.STRING);
        Material bisher = gespeichert == null ? RAND_MATERIAL : Material.matchMaterial(gespeichert);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int index = x * 16 + z;
                byte alt = rand[index];
                boolean wegrand = plots.istWegrand(raster, chunk.getX() * 16 + x, chunk.getZ() * 16 + z);
                if (wegrand == (alt != KEIN_RAND) && (bisher == material || alt != GESETZT)) {
                    continue;
                }
                Block block = chunk.getBlock(x, HOEHE, z);
                if (wegrand) {
                    if (alt == GESETZT && block.getType() == bisher && block.getBlockData() instanceof Slab slab
                            && slab.getType() == Slab.Type.BOTTOM && !slab.isWaterlogged()) {
                        block.setType(material, false);
                        geaendert = true;
                        continue;
                    }
                    rand[index] = VORGESEHEN;
                    if (block.getType().isAir() && chunk.getBlock(x, PlotGenerator.BODEN_Y, z).getType() == weg) {
                        block.setType(material, false);
                        rand[index] = GESETZT;
                    }
                } else {
                    // Beim Zusammenlegen nur unveränderte, vom Plugin gesetzte Slabs entfernen.
                    if (alt == GESETZT && block.getType() == bisher && block.getBlockData() instanceof Slab slab
                            && slab.getType() == Slab.Type.BOTTOM && !slab.isWaterlogged()) {
                        block.setType(Material.AIR, false);
                    }
                    rand[index] = KEIN_RAND;
                }
                geaendert = true;
            }
        }
        if (geaendert) {
            daten.set(RAND, PersistentDataType.BYTE_ARRAY, rand);
        }
        if (bisher != material || gespeichert == null) daten.set(RAND_TYP, PersistentDataType.STRING, material.name());
    }

    void markierungEntfernen(Block block) {
        var daten = block.getChunk().getPersistentDataContainer();
        byte[] rand = daten.get(RAND, PersistentDataType.BYTE_ARRAY);
        int index = (block.getX() & 15) * 16 + (block.getZ() & 15);
        if (rand != null && rand.length == 256 && rand[index] == GESETZT) {
            rand[index] = VORGESEHEN;
            daten.set(RAND, PersistentDataType.BYTE_ARRAY, rand);
        }
    }

    private record RandAuftrag(Chunk chunk, PlotRaster raster) {
    }
}
