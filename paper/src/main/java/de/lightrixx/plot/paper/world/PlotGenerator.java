package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
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
    private static final int ERDSCHICHTEN = 3;
    private final PlotRaster raster;
    private final PlotKonfiguration konfiguration;

    public PlotGenerator(PlotRaster raster) {
        this(raster, null);
    }

    public PlotGenerator(PlotRaster raster, PlotKonfiguration konfiguration) {
        this.raster = raster;
        this.konfiguration = konfiguration;
    }

    @Override
    public void generateNoise(WorldInfo welt, Random random, int chunkX, int chunkZ, ChunkData daten) {
        int minimum = daten.getMinHeight();
        var werte = konfiguration == null ? null : konfiguration.werte();
        int schichten = werte == null ? ERDSCHICHTEN : werte.zahl("world.erdschichten");
        Material untergrund = material(werte, "untergrund", Material.STONE);
        Material erde = material(werte, "erde", Material.DIRT);
        Material wegMaterial = material(werte, "weg", Material.STONE_BRICKS);
        Material oberflaeche = material(werte, "oberflaeche", Material.GRASS_BLOCK);
        daten.setRegion(0, minimum, 0, 16, minimum + 1, 16, Material.BEDROCK);
        daten.setRegion(0, minimum + 1, 0, 16, BODEN_Y - schichten, 16, untergrund);
        daten.setRegion(0, BODEN_Y - schichten, 0, 16, BODEN_Y, 16, erde);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                boolean weg = raster.istWeg(chunkX * 16 + x, chunkZ * 16 + z);
                daten.setBlock(x, BODEN_Y, z, weg ? wegMaterial : oberflaeche);
            }
        }
    }

    private static Material material(PlotKonfiguration.Werte werte, String key, Material normal) {
        return werte == null ? normal : Material.matchMaterial((String) werte.optionen().get("world.material." + key));
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
