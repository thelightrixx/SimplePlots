package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.storage.PlotYamlSpeicher;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlotWeltTest {
    @TempDir
    Path ordner;

    @Test
    void kehrtAuchBeiVorangestellterPlotweltZumSpawnDerHauptweltZurueck() throws IOException {
        World hauptwelt = welt(NamespacedKey.minecraft("overworld"), 120, 75, -34);
        World nether = welt(NamespacedKey.minecraft("the_nether"), 0, 64, 0);
        for (String namespace : List.of("simpleplots", "plotsystem")) {
            World plotwelt = welt(new NamespacedKey(namespace, "plots"), -4, 65, -4);
            int format = namespace.equals("plotsystem") ? 1 : 2;
            Files.writeString(ordner.resolve("welt.yml"), "format: " + format + "\nplotgroesse: 32\nwegbreite: 7\n");
            var verwaltung = verwaltung(List.of(plotwelt, nether, hauptwelt));

            assertTrue(verwaltung.istPlotWelt(plotwelt));
            Location ziel = verwaltung.rueckkehr();
            assertSame(hauptwelt, ziel.getWorld());
            assertEquals(120, ziel.getBlockX());
            assertEquals(75, ziel.getBlockY());
            assertEquals(-34, ziel.getBlockZ());
        }
    }

    @Test
    void lehntEinenStartOhneDieHauptweltAb() {
        World plotwelt = welt(new NamespacedKey("simpleplots", "plots"), -4, 65, -4);
        var fehler = assertThrows(IOException.class, () -> verwaltung(List.of(plotwelt)));
        assertTrue(fehler.getMessage().contains("minecraft:overworld"));
    }

    @Test
    void lehntGespeichertePlotzellenMitUeberlaufendenBlockkoordinatenAb() throws IOException {
        var plot = Plot.neu(new PlotId(Integer.MAX_VALUE, 0),
                UUID.randomUUID());
        new PlotYamlSpeicher(ordner.resolve("plots.yml")).speichern(List.of(plot));
        Files.writeString(ordner.resolve("welt.yml"), "format: 2\nplotgroesse: 32\nwegbreite: 7\n");
        var hauptwelt = welt(NamespacedKey.minecraft("overworld"), 0, 64, 0);
        var fehler = assertThrows(IOException.class, () -> verwaltung(List.of(hauptwelt)));
        assertTrue(fehler.getMessage().contains("Blockrasters"));
    }

    @Test
    void lehntClaimsOhneWeltmetadatenAbStattDasRasterNeuFestzulegen() throws IOException {
        new PlotYamlSpeicher(ordner.resolve("plots.yml")).speichern(List.of(Plot.neu(new PlotId(0, 0), UUID.randomUUID())));
        var hauptwelt = welt(NamespacedKey.minecraft("overworld"), 0, 64, 0);
        var fehler = assertThrows(IOException.class, () -> verwaltung(List.of(hauptwelt)));
        assertTrue(fehler.getMessage().contains("ohne welt.yml"));
    }

    @Test
    void erzeugtBeiVorhandenenClaimsKeineErsatzweltFuerFehlendeWeltdateien() throws IOException {
        new PlotYamlSpeicher(ordner.resolve("plots.yml")).speichern(List.of(Plot.neu(new PlotId(0, 0), UUID.randomUUID())));
        Files.writeString(ordner.resolve("welt.yml"), "format: 2\nplotgroesse: 32\nwegbreite: 7\n");
        var hauptwelt = mock(World.class);
        when(hauptwelt.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
        when(hauptwelt.getWorldPath()).thenReturn(ordner.resolve("world/dimensions/minecraft/overworld"));
        var verwaltung = verwaltung(List.of(hauptwelt));
        var fehler = assertThrows(IOException.class, verwaltung::ladenOderErstellen);
        assertTrue(fehler.getMessage().contains("Weltdateien"));
    }

    @Test
    void verlangtFuerBesuchspunkteEineTragendeOberseiteUnterDerBlockmitte() {
        var block = mock(Block.class);
        var form = mock(VoxelShape.class);
        when(block.getCollisionShape()).thenReturn(form);
        when(form.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, 1, 1)));
        assertTrue(PlotWelt.hatStandflaeche(block));
        when(form.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0.5, 0, 1, 1, 1)));
        assertTrue(PlotWelt.hatStandflaeche(block));
        when(form.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, 0.5, 1)));
        assertFalse(PlotWelt.hatStandflaeche(block));
        when(form.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 0.125, 1, 1),
                new BoundingBox(0.875, 0, 0, 1, 1, 1), new BoundingBox(0, 0, 0, 1, 1, 0.125),
                new BoundingBox(0, 0, 0.875, 1, 1, 1)));
        assertFalse(PlotWelt.hatStandflaeche(block));
        when(form.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0.375, 0, 0.375, 0.625, 1.5, 0.625)));
        assertFalse(PlotWelt.hatStandflaeche(block));
        when(form.getBoundingBoxes()).thenReturn(List.of());
        assertFalse(PlotWelt.hatStandflaeche(block));
    }

    private PlotWelt verwaltung(List<World> welten) throws IOException {
        var server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, methode, argumente) -> switch (methode.getName()) {
                    case "getWorlds" -> welten;
                    case "getWorld" -> welten.stream().filter(welt -> welt.getKey().equals(argumente[0])).findFirst().orElse(null);
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
        Path config = ordner.resolve("config.yml");
        Files.writeString(config, "raster:\n  plotgroesse: 32\n  wegbreite: 7\nclaims:\n  pro-spieler: 3\n");
        var plots = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        var plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        return new PlotWelt(plugin, new PlotKonfiguration(config), plots, ordner.resolve("welt.yml"));
    }

    private static World welt(NamespacedKey key, int x, int y, int z) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, methode, argumente) -> switch (methode.getName()) {
                    case "getKey" -> key;
                    case "getSpawnLocation" -> new Location((World) proxy, x, y, z);
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
    }
}
