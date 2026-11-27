package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotGeneratorTest {
    @Test
    void erzeugtEntfernteZellenAuchInNeuenChunksAlsLeereFlaechen(@TempDir Path ordner) throws IOException {
        var plots = new de.lightrixx.plot.service.PlotDienst(new de.lightrixx.plot.paper.storage.PlotYamlSpeicher(ordner.resolve("plots.yml")),
                de.lightrixx.plot.economy.PlotKauf.kostenlos());
        var id = new de.lightrixx.plot.model.PlotId(0, 0);
        plots.entfernen(id, java.util.Optional.empty(), ziel -> false);
        var daten = new TestChunk(-64);
        var generator = new PlotGenerator(new PlotRaster(2, 1), null, plots);
        generator.generateNoise(welt(-64), new Random(0), 0, 0, daten.api());
        assertEquals(Material.AIR, daten.material(0, -64, 0));
        assertEquals(Material.AIR, daten.material(0, 63, 0));
        assertEquals(Material.BEDROCK, daten.material(2, -64, 0));
        assertEquals(Material.STONE_BRICKS, daten.material(2, 63, 0));
        assertEquals(Material.GRASS_BLOCK, daten.material(3, 63, 0));
    }
    @Test
    void verwendetDieKonfiguriertenMaterialienUndErdlagen(@TempDir Path ordner) throws IOException {
        Path alt = ordner.resolve("config.yml");
        Files.writeString(alt, "raster:\n  plotgroesse: 32\n  wegbreite: 7\nclaims:\n  pro-spieler: 3\n");
        var config = new PlotKonfiguration(alt, material -> true);
        Path world = ordner.resolve("config/world.yml");
        Files.writeString(world, Files.readString(world).replace("GRASS_BLOCK", "MOSS_BLOCK").replace("STONE_BRICKS", "POLISHED_ANDESITE")
                .replace("untergrund: STONE", "untergrund: DIORITE").replace("erde: DIRT", "erde: COARSE_DIRT").replace("erdschichten: 3", "erdschichten: 5"));
        config.uebernehmen(config.vorbereiten());
        var boden = new TestChunk(-64);
        var generator = new PlotGenerator(config.raster(), config);
        generator.generateNoise(welt(-64), new Random(0), 0, 0, boden.api());
        assertEquals(Material.BEDROCK, boden.material(5, -64, 5));
        assertEquals(Material.DIORITE, boden.material(5, 57, 5));
        assertEquals(Material.COARSE_DIRT, boden.material(5, 58, 5));
        assertEquals(Material.COARSE_DIRT, boden.material(5, 62, 5));
        assertEquals(Material.MOSS_BLOCK, boden.material(5, 63, 5));
        var weg = new TestChunk(-64);
        generator.generateNoise(welt(-64), new Random(0), 2, 0, weg.api());
        assertEquals(Material.POLISHED_ANDESITE, weg.material(0, 63, 5));
    }

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
