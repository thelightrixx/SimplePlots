package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.*;
import de.lightrixx.plot.paper.config.*;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotNavigationTest {
    private final PlotDienst plots = mock(PlotDienst.class);
    private final PlotWelt welt = mock(PlotWelt.class);
    private final PlotKonfiguration config = mock(PlotKonfiguration.class);
    private final PlotNachrichten texte = mock(PlotNachrichten.class);
    private final Player player = mock(Player.class);
    private final PersistentDataContainer data = mock(PersistentDataContainer.class);
    private final World world = mock(World.class);
    private final AtomicReference<String> saved = new AtomicReference<>();
    private final AtomicInteger visits = new AtomicInteger();
    private final Plot plot = Plot.neu(new PlotId(-1, 2), UUID.randomUUID());
    private PlotNavigation navigation;
    @BeforeEach void vorbereiten() throws Exception {
        when(config.weltname()).thenReturn("plots"); when(config.zahl("general.navigation.lesezeichen-limit")).thenReturn(2);
        when(player.hasPermission("simpleplots.bookmark")).thenReturn(true);
        when(player.getPersistentDataContainer()).thenReturn(data); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world); when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(world, 0, 70, 0));
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenAnswer(call -> saved.get());
        doAnswer(call -> { saved.set(call.getArgument(2)); return null; }).when(data).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
        when(welt.istPlotWelt(world)).thenReturn(true); when(welt.plotBei(any())).thenReturn(Optional.of(plot.id()));
        when(welt.ladenOderErstellen()).thenReturn(world); when(plots.finden(plot.id())).thenReturn(Optional.of(plot));
        navigation = new PlotNavigation(plots, welt, config, texte, (p, found) -> visits.incrementAndGet(), LoggerFactory.getLogger(getClass()));
    }
    private void command(String... args) throws IOException { navigation.ausfuehren(player, "bookmark", args); }
    @Test void merkenUndBesuchenPruefenBesitzerZensurUndWeltErneut() throws Exception {
        command("bookmark", "add", "Schönes", "Haus");
        assertEquals("Schönes Haus", PlotLesezeichen.lesen(saved.get()).getFirst().name());
        command("bookmark", "go", "Schönes", "Haus"); assertEquals(1, visits.get());
        when(plots.finden(plot.id())).thenReturn(Optional.of(Plot.neu(plot.id(), UUID.randomUUID())));
        command("bookmark", "go", "Schönes", "Haus"); assertEquals(1, visits.get());
        when(plots.finden(plot.id())).thenReturn(Optional.of(plot.mitZensur(true)));
        command("bookmark", "go", "Schönes", "Haus"); assertEquals(1, visits.get());
        when(plots.finden(plot.id())).thenReturn(Optional.of(plot)); when(world.getUID()).thenReturn(UUID.randomUUID());
        command("bookmark", "go", "Schönes", "Haus"); assertEquals(1, visits.get());
    }
    @Test void rechteLimitUndDefekteSpielerdatenVerhindernUnbeabsichtigtesUeberschreiben() throws Exception {
        when(player.hasPermission("simpleplots.bookmark")).thenReturn(false);
        command("bookmark", "add", "Eins"); assertNull(saved.get()); verifyNoInteractions(plots);
        when(player.hasPermission("simpleplots.bookmark")).thenReturn(true);
        command("bookmark", "add", "Eins"); command("bookmark", "add", "Zwei"); command("bookmark", "add", "Drei");
        assertEquals(2, PlotLesezeichen.lesen(saved.get()).size());
        command("bookmark", "remove", "Eins"); assertEquals(1, PlotLesezeichen.lesen(saved.get()).size());
        saved.set("kaputt"); assertThrows(IOException.class, () -> command("bookmark", "add", "Drei")); assertEquals("kaputt", saved.get());
    }
}
