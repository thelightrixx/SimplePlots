package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.persistence.PersistentDataType;

final class PlotWegrand {
    private static final NamespacedKey RAND = new NamespacedKey("simpleplots", "wegrand");
    private static final Material RAND_MATERIAL = Material.STONE_BRICK_SLAB;
    private static final int HOEHE = PlotGenerator.BODEN_Y + 1;
    private static final byte KEIN_RAND = 0;
    private static final byte VORGESEHEN = 1;
    private static final byte GESETZT = 2;
    private final PlotDienst plots;

    PlotWegrand(PlotDienst plots) {
        this.plots = plots;
    }

    void aktualisieren(Chunk chunk, PlotRaster raster) {
        var daten = chunk.getPersistentDataContainer();
        byte[] rand = daten.get(RAND, PersistentDataType.BYTE_ARRAY);
        if (rand == null || rand.length != 256) {
            rand = new byte[256];
        }
        boolean geaendert = false;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int index = x * 16 + z;
                byte alt = rand[index];
                boolean wegrand = plots.istWegrand(raster, chunk.getX() * 16 + x, chunk.getZ() * 16 + z);
                if (wegrand == (alt != KEIN_RAND)) {
                    continue;
                }
                Block block = chunk.getBlock(x, HOEHE, z);
                if (wegrand) {
                    rand[index] = VORGESEHEN;
                    if (block.getType().isAir() && chunk.getBlock(x, PlotGenerator.BODEN_Y, z).getType() == Material.STONE_BRICKS) {
                        block.setType(RAND_MATERIAL, false);
                        rand[index] = GESETZT;
                    }
                } else {
                    // Beim Zusammenlegen nur unveränderte, vom Plugin gesetzte Slabs entfernen.
                    if (alt == GESETZT && block.getType() == RAND_MATERIAL && block.getBlockData() instanceof Slab slab
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
}
