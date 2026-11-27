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
import static org.mockito.Mockito.*;

class PlotWeltTest {
    @TempDir
    Path ordner;

    @Test
    void registriertMehrereWeltenMitGetrenntenSchluesselnUndClaims() throws IOException {
        var basis = new PlotKonfiguration(ordner.resolve("config.yml"), material -> true);
        var configA = basis.weltenLaden().getFirst();
        Files.writeString(ordner.resolve("worlds/creative.yml"), "world:\n  raster:\n    plotgroesse: 48\n");
        var configB = basis.fuerWelt("creative");
        var hauptwelt = welt(NamespacedKey.minecraft("overworld"), 0, 64, 0);
        var server = mock(Server.class); when(server.getWorld(NamespacedKey.minecraft("overworld"))).thenReturn(hauptwelt);
        var plugin = mock(Plugin.class); when(plugin.getServer()).thenReturn(server);
        var plotsA = new PlotDienst(new PlotYamlSpeicher(configA.datenordner().resolve("plots.yml")), PlotKauf.kostenlos());
        var plotsB = new PlotDienst(new PlotYamlSpeicher(configB.datenordner().resolve("plots.yml")), PlotKauf.kostenlos());
        var a = new PlotWelt(plugin, configA, plotsA, configA.datenordner().resolve("welt.yml"));
        var b = new PlotWelt(plugin, configB, plotsB, configB.datenordner().resolve("welt.yml"));
        assertEquals(new NamespacedKey("simpleplots", "plots"), a.schluessel());
        assertEquals(new NamespacedKey("simpleplots", "creative"), b.schluessel());
        World creative = welt(b.schluessel(), 0, 64, 0);
        assertFalse(a.istPlotWelt(creative)); assertTrue(b.istPlotWelt(creative));
        var id = new PlotId(0, 0); UUID besitzer = UUID.randomUUID();
        plotsA.claim(id, besitzer, 1);
        assertTrue(plotsB.finden(id).isEmpty()); assertEquals(48, b.raster().groesse());
    }

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
    void behaeltDenLetztenHauptweltStandortMitBlickrichtungProSpielerUndNachNeustart() throws IOException {
        World hauptwelt = mock(World.class);
        when(hauptwelt.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
        when(hauptwelt.getMinHeight()).thenReturn(-64);
        when(hauptwelt.getMaxHeight()).thenReturn(320);
        when(hauptwelt.getSpawnLocation()).thenReturn(new Location(hauptwelt, 0, 80, 0));
        var grenze = mock(org.bukkit.WorldBorder.class);
        when(hauptwelt.getWorldBorder()).thenReturn(grenze);
        when(grenze.isInside(any())).thenReturn(true);
        World plotwelt = welt(new NamespacedKey("simpleplots", "plots"), -4, 65, -4);
        var spieler = mock(org.bukkit.entity.Player.class);
        var daten = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(spieler.getPersistentDataContainer()).thenReturn(daten);
        var gespeichert = new java.util.concurrent.atomic.AtomicReference<List<Double>>();
        doAnswer(aufruf -> { gespeichert.set(aufruf.getArgument(2)); return null; }).when(daten)
                .set(any(), eq(org.bukkit.persistence.PersistentDataType.LIST.doubles()), anyList());
        when(daten.get(any(), eq(org.bukkit.persistence.PersistentDataType.LIST.doubles()))).thenAnswer(aufruf -> gespeichert.get());
        var verwaltung = verwaltung(List.of(hauptwelt, plotwelt));
        assertEquals(hauptwelt.getSpawnLocation(), verwaltung.rueckkehr(spieler));
        var vorher = new Location(hauptwelt, 123.25, 72.5, -456.75, 87.5f, -23.5f);
        verwaltung.rueckkehrMerken(spieler, vorher, new Location(plotwelt, 0, 64, 0));
        vorher.add(20, 0, 0);
        var ziel = verwaltung(List.of(hauptwelt, plotwelt)).rueckkehr(spieler);
        assertEquals(123.25, ziel.getX());
        assertEquals(72.5, ziel.getY());
        assertEquals(-456.75, ziel.getZ());
        assertEquals(87.5f, ziel.getYaw());
        assertEquals(-23.5f, ziel.getPitch());
        verwaltung.rueckkehrMerken(spieler, new Location(plotwelt, 0, 64, 0), vorher);
        assertEquals(ziel, verwaltung.rueckkehr(spieler));
        gespeichert.set(List.of(Double.NaN, 64.0, 0.0, 0.0, 0.0));
        assertEquals(hauptwelt.getSpawnLocation(), verwaltung.rueckkehr(spieler));
        gespeichert.set(List.of(100.0, 64.0, 0.0, 0.0, 0.0));
        when(grenze.isInside(any())).thenReturn(false);
        assertEquals(hauptwelt.getSpawnLocation(), verwaltung.rueckkehr(spieler));
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
        return new PlotWelt(plugin, new PlotKonfiguration(config, material -> true), plots, ordner.resolve("welt.yml"));
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
