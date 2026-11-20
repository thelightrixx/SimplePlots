package de.lightrixx.plot.paper.listener;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotZutrittListenerTest {
    private final World plotwelt = mock(World.class);
    private final World hauptwelt = mock(World.class);
    private final PlotWelt welt = mock(PlotWelt.class);
    private final PlotDienst plots = mock(PlotDienst.class);
    private final PlotNachrichten texte = mock(PlotNachrichten.class);
    private final Player spieler = mock(Player.class);
    private final PlotId id = new PlotId(0, 0);
    private final Location aufPlot = new Location(plotwelt, 5, 65, 5);
    private final Location aufWeg = new Location(plotwelt, 35, 65, 5);
    private final Location draussen = new Location(hauptwelt, 5, 65, 5);
    private final PlotZutrittListener listener = new PlotZutrittListener(mock(Plugin.class), welt, plots, texte);

    @BeforeEach
    void vorbereiten() {
        UUID gast = UUID.randomUUID();
        var config = mock(de.lightrixx.plot.paper.config.PlotKonfiguration.class);
        when(config.aktiv(anyString())).thenReturn(true);
        when(config.zahl(anyString())).thenReturn(250);
        when(welt.konfiguration()).thenReturn(config);
        when(spieler.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(spieler.getUniqueId()).thenReturn(gast);
        when(spieler.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(spieler.getWorld()).thenReturn(plotwelt);
        when(welt.istPlotWelt(any())).thenAnswer(aufruf -> aufruf.getArgument(0) == plotwelt);
        when(welt.plotBei(any())).thenAnswer(aufruf -> {
            Location ort = aufruf.getArgument(0);
            return ort != null && ort.getWorld() == plotwelt && ort.getBlockX() < 32 ? Optional.of(id) : Optional.empty();
        });
        when(plots.finden(id)).thenReturn(Optional.of(Plot.neu(id, UUID.randomUUID()).mitRolle(gast, Plot.RollenAktion.DENY)));
        when(welt.wegVor(plotwelt, id)).thenReturn(aufWeg);
        when(texte.komponente(anyString(), any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class)))
                .thenReturn(Component.empty());
    }

    @Test
    void verhindertBetretenUndTeleportAufEinenGebanntenPlot() {
        var bewegen = new PlayerMoveEvent(spieler, aufWeg, aufPlot);
        listener.bewegen(bewegen);
        assertTrue(bewegen.isCancelled());
        var teleport = new PlayerTeleportEvent(spieler, draussen, aufPlot);
        listener.teleportieren(teleport);
        assertTrue(teleport.isCancelled());
    }

    @Test
    void leitetBereitsGebannteSpielerAufDenWegUndLaesstAdminsPassieren() {
        var bewegen = new PlayerMoveEvent(spieler, aufPlot, aufPlot.clone().add(1, 0, 0));
        listener.bewegen(bewegen);
        assertEquals(aufWeg, bewegen.getTo());
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        when(spieler.hasPermission("simpleplots.admin.bypass")).thenReturn(true);
        var teleport = new PlayerTeleportEvent(spieler, draussen, aufPlot);
        listener.teleportieren(teleport);
        assertFalse(teleport.isCancelled());
    }

    @Test
    void verhindertUnerlaubtenKreativmodusUndLaesstDieHauptweltUnberuehrt() {
        when(spieler.getGameMode()).thenReturn(GameMode.CREATIVE);
        var hinein = new PlayerTeleportEvent(spieler, draussen, aufPlot);
        listener.teleportieren(hinein);
        assertTrue(hinein.isCancelled());
        var hinaus = new PlayerTeleportEvent(spieler, aufPlot, draussen);
        listener.teleportieren(hinaus);
        assertFalse(hinaus.isCancelled());
        var modus = new PlayerGameModeChangeEvent(spieler, GameMode.CREATIVE,
                PlayerGameModeChangeEvent.Cause.PLUGIN, Component.empty());
        listener.modusWechseln(modus);
        assertTrue(modus.isCancelled());
    }

    @Test
    void laesstAdminsInAllenModiWechselnUndInDiePlotweltTeleportieren() {
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        for (GameMode modus : GameMode.values()) {
            when(spieler.getGameMode()).thenReturn(modus);
            var teleport = new PlayerTeleportEvent(spieler, draussen, aufWeg);
            listener.teleportieren(teleport);
            assertFalse(teleport.isCancelled());
            var wechsel = new PlayerGameModeChangeEvent(spieler, modus,
                    PlayerGameModeChangeEvent.Cause.PLUGIN, Component.empty());
            listener.modusWechseln(wechsel);
            assertFalse(wechsel.isCancelled());
        }
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(false);
        for (GameMode modus : new GameMode[]{GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR}) {
            var wechsel = new PlayerGameModeChangeEvent(spieler, modus,
                    PlayerGameModeChangeEvent.Cause.PLUGIN, Component.empty());
            listener.modusWechseln(wechsel);
            assertTrue(wechsel.isCancelled());
        }
    }

    @Test
    void respektiertEinAusdruecklichEntzogenesBypassRechtAuchBeiAdmins() {
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        when(spieler.hasPermission("simpleplots.admin.bypass")).thenReturn(false);
        var teleport = new PlayerTeleportEvent(spieler, draussen, aufPlot);
        listener.teleportieren(teleport);
        assertTrue(teleport.isCancelled());
    }

    @Test
    void verhindertPortalreisenMitPlotweltBeteiligung() {
        var portal = mock(PlayerPortalEvent.class);
        when(portal.getFrom()).thenReturn(aufPlot);
        when(portal.getTo()).thenReturn(null);
        listener.portal(portal);
        verify(portal).setCancelled(true);
        var normal = mock(PlayerPortalEvent.class);
        when(normal.getFrom()).thenReturn(draussen);
        when(normal.getTo()).thenReturn(draussen);
        listener.portal(normal);
        verify(normal, never()).setCancelled(true);
    }

    @Test
    void ersetztEinenGebanntenRespawnDurchDenOeffentlichenWeg() {
        var event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(spieler);
        when(event.getRespawnLocation()).thenReturn(aufPlot);
        listener.respawn(event);
        verify(event).setRespawnLocation(aufWeg);
    }

    @Test
    void behandeltBewegungenUndTeleportsOhneZielOhneFehler() {
        var bewegen = new PlayerMoveEvent(spieler, aufPlot, null);
        assertDoesNotThrow(() -> listener.bewegen(bewegen));
        assertDoesNotThrow(() -> listener.bewegungAnzeigen(bewegen));
        var teleport = new PlayerTeleportEvent(spieler, draussen, null);
        assertDoesNotThrow(() -> listener.teleportieren(teleport));
        assertDoesNotThrow(() -> listener.teleportAnzeigen(teleport));
        verifyNoInteractions(texte);
    }
}
