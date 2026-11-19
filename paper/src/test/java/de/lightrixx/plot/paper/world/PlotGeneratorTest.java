package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.PlotRaster;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotGeneratorTest {
    @Test
    void erzeugtErdeUndSteinMitGenauEinerBedrockschichtAmWeltboden() {
        for (int minimum : new int[]{-64, 0}) {
            var daten = new TestChunk(minimum);
            new PlotGenerator(new PlotRaster(32, 7)).generateNoise(welt(minimum), new Random(0), 0, 0, daten.api());

            assertEquals(Material.BEDROCK, daten.material(5, minimum, 5));
            assertEquals(Material.STONE, daten.material(5, minimum + 1, 5));
            assertEquals(Material.STONE, daten.material(5, 59, 5));
            for (int y = 60; y < 63; y++) {
                assertEquals(Material.DIRT, daten.material(5, y, 5));
            }
            assertEquals(Material.GRASS_BLOCK, daten.material(5, 63, 5));
            assertEquals(Material.AIR, daten.material(5, 64, 5));
            for (int y = minimum + 1; y <= 63; y++) {
                assertEquals(y < 60 ? Material.STONE : y < 63 ? Material.DIRT : Material.GRASS_BLOCK,
                        daten.material(5, y, 5), "Y=" + y);
            }
        }
    }

    @Test
    void behaeltDieWegoberflaecheAufDerBisherigenHoehe() {
        var daten = new TestChunk(-64);
        new PlotGenerator(new PlotRaster(32, 7)).generateNoise(welt(-64), new Random(0), 2, 0, daten.api());
        assertEquals(Material.STONE_BRICKS, daten.material(0, 63, 5));
        assertEquals(Material.DIRT, daten.material(0, 62, 5));
        assertEquals(Material.STONE, daten.material(0, -63, 5));
        assertEquals(Material.BEDROCK, daten.material(0, -64, 5));
    }

    private static WorldInfo welt(int minimum) {
        return (WorldInfo) Proxy.newProxyInstance(WorldInfo.class.getClassLoader(), new Class<?>[]{WorldInfo.class},
                (proxy, methode, argumente) -> switch (methode.getName()) {
                    case "getMinHeight" -> minimum;
                    case "getMaxHeight" -> 320;
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
    }

    private record Bereich(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Material material) {
        boolean enthaelt(int x, int y, int z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
    }

    private static final class TestChunk {
        private final int minimum;
        private final List<Bereich> bereiche = new ArrayList<>();

        private TestChunk(int minimum) {
            this.minimum = minimum;
        }

        ChunkData api() {
            return (ChunkData) Proxy.newProxyInstance(ChunkData.class.getClassLoader(), new Class<?>[]{ChunkData.class},
                    (proxy, methode, argumente) -> switch (methode.getName()) {
                        case "getMinHeight" -> minimum;
                        case "getMaxHeight" -> 320;
                        case "setRegion" -> {
                            bereiche.add(new Bereich((int) argumente[0], (int) argumente[1], (int) argumente[2],
                                    (int) argumente[3], (int) argumente[4], (int) argumente[5], (Material) argumente[6]));
                            yield null;
                        }
                        case "setBlock" -> {
                            int x = (int) argumente[0];
                            int y = (int) argumente[1];
                            int z = (int) argumente[2];
                            bereiche.add(new Bereich(x, y, z, x + 1, y + 1, z + 1, (Material) argumente[3]));
                            yield null;
                        }
                        default -> throw new UnsupportedOperationException(methode.getName());
                    });
        }

        Material material(int x, int y, int z) {
            return bereiche.reversed().stream().filter(bereich -> bereich.enthaelt(x, y, z))
                    .map(Bereich::material).findFirst().orElse(Material.AIR);
        }
    }
}
