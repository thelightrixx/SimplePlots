package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.PlotEntfernung;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.command.PlotAuftraege;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.storage.PlotYamlSpeicher;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.storage.SpeicherAuftraege;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotEntfernenTest {
    @TempDir Path ordner;
    private final PlotId id = new PlotId(0, 0);
    private final UUID besitzer = UUID.randomUUID();
    private final World real = mock(World.class);
    private final PlotWelt welt = mock(PlotWelt.class);
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final org.bukkit.command.ConsoleCommandSender sender = mock(org.bukkit.command.ConsoleCommandSender.class);
    private final PlotAuftraege auftraege = mock(PlotAuftraege.class);
    private final ArrayDeque<Runnable> ticks = new ArrayDeque<>();
    private final Map<List<Integer>, Material> bloecke = new HashMap<>();
    private PlotDienst plots;
    private PlotEntfernen entfernen;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void vorbereiten() throws IOException {
        Files.writeString(ordner.resolve("config.yml"), "raster:\n  plotgroesse: 2\n  wegbreite: 1\nclaims:\n  pro-spieler: 3\n");
        var config = new PlotKonfiguration(ordner.resolve("config.yml"), material -> true);
        plots = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        var server = mock(Server.class); var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server); when(plugin.getSLF4JLogger()).thenReturn(mock(Logger.class));
        when(server.getScheduler()).thenReturn(scheduler); when(server.getConsoleSender()).thenReturn(sender);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(aufruf -> { ticks.add(aufruf.getArgument(1)); return null; });
        when(welt.konfiguration()).thenReturn(config); when(welt.raster()).thenReturn(config.raster());
        when(welt.istPlotWelt(real)).thenReturn(true); when(welt.ladenOderErstellen()).thenReturn(real);
        when(real.getMinHeight()).thenReturn(0); when(real.getMaxHeight()).thenReturn(66);
        when(real.getPlayers()).thenReturn(List.of());
        var chunk = mock(Chunk.class); when(chunk.getEntities()).thenReturn(new Entity[0]);
        when(real.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
        when(real.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(aufruf -> {
            List<Integer> key = List.of(aufruf.getArgument(0), aufruf.getArgument(1), aufruf.getArgument(2));
            var block = mock(Block.class);
            when(block.getType()).thenAnswer(ignoriert -> bloecke.getOrDefault(key, Material.DIAMOND_BLOCK));
            doAnswer(aenderung -> { bloecke.put(key, aenderung.getArgument(0)); return null; }).when(block).setType(any(Material.class), eq(false));
            return block;
        });
        doAnswer(aufruf -> {
            var auftrag = (SpeicherAuftraege.Auftrag<Object>) aufruf.getArgument(2);
            ((Consumer<Object>) aufruf.getArgument(3)).accept(auftrag.ausfuehren());
            return null;
        }).when(auftraege).ausfuehren(any(), anyString(), any(), any());
        doAnswer(aufruf -> {
            var auftrag = (SpeicherAuftraege.Auftrag<Object>) aufruf.getArgument(2);
            try { ((Consumer<Object>) aufruf.getArgument(3)).accept(auftrag.ausfuehren()); }
            finally { ((Runnable) aufruf.getArgument(4)).run(); }
            return null;
        }).when(auftraege).ausfuehren(any(), anyString(), any(), any(), any());
        entfernen = neu();
    }

    private PlotEntfernen neu() { return new PlotEntfernen(plugin, welt, plots, auftraege, mock(PlotNachrichten.class), () -> { }); }
    private void abarbeiten() {
        int rest = 100;
        while (!ticks.isEmpty() && rest-- > 0) ticks.removeFirst().run();
        assertTrue(ticks.isEmpty(), "Weltarbeiten müssen beendet sein");
    }

    @Test
    void entferntNachSpeicherungBisZumWeltbodenUndRestauriertOhneBesitzOderBauten() throws IOException {
        plots.claim(id, besitzer, 1);
        entfernen.befehl(sender, id, false);
        assertTrue(plots.finden(id).isEmpty()); assertTrue(bloecke.isEmpty());
        assertEquals(PlotDienst.ClaimErgebnis.BELEGT, plots.claim(id, besitzer, 1));
        abarbeiten();
        assertEquals(PlotEntfernung.Phase.ENTFERNT, plots.entfernung(id).orElseThrow().phase());
        assertEquals(264, bloecke.size()); assertTrue(bloecke.values().stream().allMatch(Material.AIR::equals));
        assertFalse(bloecke.containsKey(List.of(2, 63, 0)), "Öffentlicher Weg bleibt unberührt");
        verify(real).save(true);
        entfernen.befehl(sender, id, true);
        assertEquals(PlotDienst.ClaimErgebnis.BELEGT, plots.claim(id, besitzer, 1));
        abarbeiten();
        assertTrue(plots.entfernungen().isEmpty()); assertTrue(plots.finden(id).isEmpty());
        assertEquals(Material.BEDROCK, bloecke.get(List.of(0, 0, 0)));
        assertEquals(Material.STONE, bloecke.get(List.of(0, 1, 0)));
        assertEquals(Material.DIRT, bloecke.get(List.of(0, 62, 0)));
        assertEquals(Material.GRASS_BLOCK, bloecke.get(List.of(0, 63, 0)));
        assertEquals(Material.AIR, bloecke.get(List.of(0, 64, 0)));
        assertEquals(PlotDienst.ClaimErgebnis.CLAIMED, plots.claim(id, besitzer, 1));
    }

    @Test
    void setztUnterbrocheneWeltarbeitenAusDemGespeichertenAuftragFort() throws IOException {
        entfernen.befehl(sender, id, false);
        ticks.removeFirst().run(); ticks.clear(); entfernen.beenden();
        plots = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        var neustart = neu(); neustart.fortsetzen(); abarbeiten();
        assertEquals(PlotEntfernung.Phase.ENTFERNT, plots.entfernung(id).orElseThrow().phase());
        assertTrue(bloecke.values().stream().allMatch(Material.AIR::equals));
    }

    @Test
    void behaeltBeiWeltSpeicherfehlerDenFortsetzbarenArbeitsstand() throws IOException {
        doThrow(new IllegalStateException("Test-Weltspeicherfehler")).when(real).save(true);
        entfernen.befehl(sender, id, false); abarbeiten();
        assertEquals(PlotEntfernung.Phase.ENTFERNEN, plots.entfernung(id).orElseThrow().phase());
        doNothing().when(real).save(true);
        entfernen.befehl(sender, id, false); abarbeiten();
        assertEquals(PlotEntfernung.Phase.ENTFERNT, plots.entfernung(id).orElseThrow().phase());
    }

    @Test
    void schuetztSpielerVorLeererFlaecheUndErlaubtBerechtigtenFlugFuerRestoreMitTilde() throws IOException {
        entfernen.befehl(sender, id, false); abarbeiten();
        var spieler = mock(Player.class);
        var weg = new Location(real, 0.5, 64, -0.5);
        var ziel = new Location(real, 0.5, 65, 0.5);
        when(welt.wegVor(real, id)).thenReturn(weg);
        var teleport = new org.bukkit.event.player.PlayerTeleportEvent(spieler, weg, ziel);
        entfernen.teleportieren(teleport);
        assertEquals(weg, teleport.getTo());
        when(spieler.hasPermission("simpleplots.admin.restore")).thenReturn(true);
        when(spieler.isFlying()).thenReturn(true);
        teleport = new org.bukkit.event.player.PlayerTeleportEvent(spieler, weg, ziel);
        entfernen.teleportieren(teleport);
        assertEquals(ziel, teleport.getTo());
        entfernen.befehl(sender, id, true);
        teleport = new org.bukkit.event.player.PlayerTeleportEvent(spieler, weg, ziel);
        entfernen.teleportieren(teleport);
        assertEquals(weg, teleport.getTo(), "Während der Wiederherstellung bleibt auch Adminflug außerhalb");
    }

    @Test
    void weistUeberlaufendeKoordinatenVorDemSpeichernUndWelterstellenAb() throws IOException {
        entfernen.befehl(sender, new PlotId(Integer.MAX_VALUE, 0), false);
        assertTrue(plots.entfernungen().isEmpty()); assertTrue(ticks.isEmpty());
        verify(welt, never()).ladenOderErstellen();
    }
}
