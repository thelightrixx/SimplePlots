package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.PlotRaster;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

public final class PlotGenerator extends ChunkGenerator {
    public static final int BODEN_Y = 63;
    private final PlotRaster raster;

    public PlotGenerator(PlotRaster raster) {
        this.raster = raster;
    }

    @Override
    public void generateNoise(WorldInfo welt, Random random, int chunkX, int chunkZ, ChunkData daten) {
        daten.setRegion(0, daten.getMinHeight(), 0, 16, BODEN_Y, 16, Material.BEDROCK);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                boolean weg = raster.istWeg(chunkX * 16 + x, chunkZ * 16 + z);
                daten.setBlock(x, BODEN_Y, z, weg ? Material.STONE_BRICKS : Material.GRASS_BLOCK);
            }
        }
    }

    @Override
    public int getBaseHeight(WorldInfo welt, Random random, int x, int z, HeightMap heightMap) {
        return BODEN_Y + 1;
    }

    @Override
    public Location getFixedSpawnLocation(World welt, Random random) {
        return oberflaeche(welt, raster.spawnAchse(), raster.spawnAchse()).add(0, 0.5, 0);
    }

    static Location oberflaeche(World welt, int x, int z) {
        return new Location(welt, x + 0.5, BODEN_Y + 1, z + 0.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo welt) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.PLAINS);
            }
        };
    }
}
