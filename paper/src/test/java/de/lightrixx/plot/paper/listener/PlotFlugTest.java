package de.lightrixx.plot.paper.listener;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.storage.PlotSpeicher;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotFlugTest {
    private final UUID id = UUID.randomUUID();
    private final PlotId plotId = new PlotId(0, 0);
    private final Player spieler = mock(Player.class);
    private final World plotwelt = mock(World.class);
    private final World hauptwelt = mock(World.class);
    private final PlotWelt welt = mock(PlotWelt.class);
    private final PlotKonfiguration config = mock(PlotKonfiguration.class);
    private final Map<NamespacedKey, Object> daten = new HashMap<>();
    private final AtomicBoolean darfFliegen = new AtomicBoolean();
    private final AtomicBoolean fliegt = new AtomicBoolean();
    private final AtomicReference<Float> tempo = new AtomicReference<>(0.2f);
    private final AtomicReference<Location> ort = new AtomicReference<>();
    private PlotDienst plots;
    private PlotFlug flug;

    @BeforeEach
    void vorbereiten() throws IOException {
        var speicher = mock(PlotSpeicher.class);
        when(speicher.laden()).thenReturn(List.of(Plot.neu(plotId, id)));
        plots = new PlotDienst(speicher, PlotKauf.kostenlos());
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        when(welt.konfiguration()).thenReturn(config);
        when(config.aktiv(anyString())).thenReturn(true);
        when(config.zahl("fly.eigener-plot.hoehe")).thenReturn(32);
        when(config.dezimal(anyString())).thenReturn(0.1);
        when(welt.istPlotWelt(any())).thenAnswer(aufruf -> aufruf.getArgument(0) == plotwelt);
        when(welt.plotBei(any())).thenAnswer(aufruf -> {
            Location ziel = aufruf.getArgument(0);
            return plots.plotBei(new PlotRaster(32, 7), ziel.getBlockX(), ziel.getBlockZ());
        });
        when(welt.wegVor(eq(plotwelt), any())).thenReturn(new Location(plotwelt, 15.5, 64, -1.5));
        when(spieler.getUniqueId()).thenReturn(id);
        when(spieler.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(spieler.getWorld()).thenReturn(plotwelt);
        when(spieler.getLocation()).thenAnswer(aufruf -> ort.get());
        when(spieler.hasPermission(anyString())).thenReturn(true);
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(false);
        when(spieler.getAllowFlight()).thenAnswer(aufruf -> darfFliegen.get());
        doAnswer(aufruf -> { darfFliegen.set(aufruf.getArgument(0)); return null; }).when(spieler).setAllowFlight(anyBoolean());
        when(spieler.isFlying()).thenAnswer(aufruf -> fliegt.get());
        doAnswer(aufruf -> { fliegt.set(aufruf.getArgument(0)); return null; }).when(spieler).setFlying(anyBoolean());
        when(spieler.getFlySpeed()).thenAnswer(aufruf -> tempo.get());
        doAnswer(aufruf -> { tempo.set(aufruf.getArgument(0)); return null; }).when(spieler).setFlySpeed(anyFloat());
        var pdc = mock(PersistentDataContainer.class);
        when(spieler.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(), any())).thenAnswer(aufruf -> daten.get(aufruf.getArgument(0)));
        when(pdc.has(any(), any())).thenAnswer(aufruf -> daten.containsKey(aufruf.getArgument(0)));
        doAnswer(aufruf -> { daten.put(aufruf.getArgument(0), aufruf.getArgument(2)); return null; }).when(pdc).set(any(), any(), any());
        doAnswer(aufruf -> { daten.remove(aufruf.getArgument(0)); return null; }).when(pdc).remove(any());
        ort.set(new Location(plotwelt, 5, 64, 35));
        flug = new PlotFlug(plugin, welt, plots, mock(PlotNachrichten.class));
    }

    @Test
    void erlaubtAdminFlugUeberFremdenFreienUndZensiertenPlotsOhneHoehenlimit() throws IOException {
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        when(spieler.hasPermission(PlotBerechtigungen.FLY)).thenReturn(false);
        when(config.aktiv(anyString())).thenReturn(false);
        var fremd = new PlotId(1, 0);
        plots.claim(fremd, UUID.randomUUID(), 3);
        plots.zensieren(fremd, true);
        ort.set(new Location(plotwelt, 44, 310, 5));
        for (GameMode modus : new GameMode[]{GameMode.SURVIVAL, GameMode.ADVENTURE}) {
            when(spieler.getGameMode()).thenReturn(modus);
            flug.befehl(spieler, new String[]{"fly", "on"});
            assertTrue(darfFliegen.get());
            assertTrue(fliegt.get());
            assertTrue(flug.erlaubt(spieler, new Location(plotwelt, 83, 310, 5)));
            assertTrue(flug.erlaubt(spieler, new Location(plotwelt, 5, 310, 35)));
            var bewegen = new PlayerMoveEvent(spieler, ort.get(), new Location(plotwelt, 83, 310, 5));
            flug.bewegen(bewegen);
            assertEquals(83, bewegen.getTo().getBlockX());
            assertTrue(fliegt.get());
            flug.befehl(spieler, new String[]{"fly", "off"});
            assertFalse(darfFliegen.get());
        }
    }

    @Test
    void beendetAdminFlugNachRechteverlustUndBeimWeltwechsel() {
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        ort.set(new Location(plotwelt, 44, 310, 5));
        flug.befehl(spieler, new String[]{"fly", "on"});
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(false);
        flug.aktualisieren(spieler);
        assertFalse(darfFliegen.get());
        assertFalse(fliegt.get());
        assertEquals(0.2f, tempo.get());
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        flug.aktualisieren(spieler);
        assertTrue(darfFliegen.get());
        ort.set(new Location(hauptwelt, 44, 310, 5));
        flug.aktualisieren(spieler);
        assertFalse(darfFliegen.get());
        assertEquals(0.2f, tempo.get());
    }

    @Test
    void laesstNatuerlichenFlugBeimWechselInCreativeUndSpectatorUnberuehrt() {
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        ort.set(new Location(plotwelt, 44, 310, 5));
        for (GameMode modus : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            when(spieler.getGameMode()).thenReturn(GameMode.SURVIVAL);
            darfFliegen.set(false);
            flug.befehl(spieler, new String[]{"fly", "on"});
            when(spieler.getGameMode()).thenReturn(modus);
            var bewegen = new PlayerMoveEvent(spieler, ort.get(), new Location(plotwelt, 83, 310, 5));
            flug.bewegen(bewegen);
            assertEquals(83, bewegen.getTo().getBlockX());
            var starten = new PlayerToggleFlightEvent(spieler, true);
            flug.starten(starten);
            assertFalse(starten.isCancelled());
            flug.aktualisieren(spieler);
            assertTrue(darfFliegen.get());
            assertTrue(fliegt.get());
            assertEquals(0.2f, tempo.get());
        }
    }

    @Test
    void stelltWegeFlyOhnePermissionUndOhneHoehenlimitBereit() {
        when(spieler.hasPermission(anyString())).thenReturn(false);
        ort.set(new Location(plotwelt, 5, 310, 35));
        flug.aktualisieren(spieler);
        assertTrue(darfFliegen.get());
        assertFalse(fliegt.get());
        assertEquals(0.1f, tempo.get());
        ort.set(new Location(hauptwelt, 5, 64, 35));
        flug.aktualisieren(spieler);
        assertFalse(darfFliegen.get());
        assertEquals(0.2f, tempo.get());
    }

    @Test
    void begrenztEigenesFlyAufEigentumHoeheUndAktuelleBerechtigung() throws IOException {
        ort.set(new Location(plotwelt, 5, 64, 5));
        flug.befehl(spieler, new String[]{"fly", "on"});
        assertTrue(darfFliegen.get());
        assertTrue(fliegt.get());
        assertFalse(flug.erlaubt(spieler, new Location(plotwelt, 5, 97, 5)));
        assertTrue(flug.erlaubt(spieler, new Location(plotwelt, 5, 96, 5)));
        assertFalse(flug.erlaubt(spieler, new Location(plotwelt, 44, 64, 5)));
        plots.zensieren(plotId, true);
        flug.aktualisieren(spieler);
        assertFalse(darfFliegen.get());
        assertFalse(fliegt.get());
        plots.zensieren(plotId, false);
        when(spieler.hasPermission(anyString())).thenReturn(false);
        flug.aktualisieren(spieler);
        assertFalse(darfFliegen.get());
    }

    @Test
    void behandeltInterneMergeWegeAlsEigenenPlotUndLandetBeimVerlassenSicher() throws IOException {
        plots.claim(new PlotId(1, 0), id, 2);
        plots.zusammenlegen(plotId, new PlotId(1, 0), id, ziel -> false, true);
        ort.set(new Location(plotwelt, 35, 64, 5));
        flug.aktualisieren(spieler);
        assertFalse(darfFliegen.get());
        flug.befehl(spieler, new String[]{"fly", "on"});
        assertTrue(darfFliegen.get());
        assertFalse(flug.erlaubt(spieler, new Location(plotwelt, 35, 97, 5)));
        var event = new PlayerMoveEvent(spieler, ort.get(), new Location(plotwelt, 35, 97, 5));
        flug.bewegen(event);
        assertEquals(new Location(plotwelt, 15.5, 64, -1.5), event.getTo());
        assertFalse(fliegt.get());
    }

    @Test
    void uebernimmtKeinBereitsVorhandenesFlyUndBeendetEigeneBerechtigungenSauber() {
        darfFliegen.set(true);
        flug.aktualisieren(spieler);
        assertEquals(0.2f, tempo.get());
        assertTrue(daten.isEmpty());
        ort.set(new Location(hauptwelt, 5, 64, 5));
        flug.aktualisieren(spieler);
        assertTrue(darfFliegen.get());
        darfFliegen.set(false);
        ort.set(new Location(plotwelt, 5, 64, 35));
        flug.aktualisieren(spieler);
        when(spieler.getGameMode()).thenReturn(GameMode.ADVENTURE);
        flug.aktualisieren(spieler);
        assertFalse(darfFliegen.get());
        assertEquals(0.2f, tempo.get());
        assertTrue(daten.isEmpty());
    }
}
