package de.lightrixx.plot.paper.gui;

import de.lightrixx.plot.model.BauWettbewerb;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.command.PlotAuftraege;
import de.lightrixx.plot.paper.config.PlotMenueKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotMenueTest {
    private final Server server = mock(Server.class);
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Player spieler = mock(Player.class);
    private final Inventory inventar = mock(Inventory.class);
    private final InventoryView ansicht = mock(InventoryView.class);
    private final ArrayDeque<Runnable> naechsteTicks = new ArrayDeque<>();
    private PlotMenue menue;
    private final PlotDienst plots = mock(PlotDienst.class);
    private final WettbewerbDienst wettbewerbe = mock(WettbewerbDienst.class);

    @BeforeEach
    void vorbereiten() {
        var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(aufruf -> {
            naechsteTicks.add(aufruf.getArgument(1));
            return null;
        });
        var holder = new AtomicReference<InventoryHolder>();
        when(server.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class))).thenAnswer(aufruf -> {
            holder.set(aufruf.getArgument(0));
            return inventar;
        });
        when(inventar.getHolder(false)).thenAnswer(aufruf -> holder.get());
        when(ansicht.getTopInventory()).thenReturn(inventar);
        when(spieler.getOpenInventory()).thenReturn(ansicht);
        when(spieler.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spieler.isOnline()).thenReturn(true);
        when(spieler.hasPermission("simpleplots.use")).thenReturn(true);
        var gui = mock(PlotMenueKonfiguration.class);
        when(gui.daten()).thenReturn(new PlotMenueKonfiguration.Daten(27, 27, 45,
                Map.of("eigene", 10, "galerie", 12, "karte", 14, "wettbewerb", 16, "plotwelt", 18,
                        "hauptwelt", 20, "claim", 24, "rechte", 26), Map.of(),
                Map.of("vorherige", 0, "filter", 2, "zurueck", 4, "extra", 6, "naechste", 8), Map.of()));
        when(gui.symbol(anyString(), any(Material.class))).thenAnswer(aufruf -> aufruf.getArgument(1));
        var texte = mock(PlotNachrichten.class);
        when(texte.komponente(anyString())).thenReturn(Component.text("Menü"));
        when(texte.komponente(anyString(), any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class)))
                .thenReturn(Component.text("Menü"));
        var welt = mock(PlotWelt.class);
        var config = mock(de.lightrixx.plot.paper.config.PlotKonfiguration.class);
        when(config.aktiv(anyString())).thenReturn(true);
        when(welt.konfiguration()).thenReturn(config);
        menue = new PlotMenue(plugin, plots, welt, texte,
                wettbewerbe, gui, mock(PlotAuftraege.class));
    }

    @Test
    void fuehrtKlicksEinmalImFolgetickAusUndVerhindertInventarTransfers() {
        try (var items = mockConstruction(ItemStack.class)) {
            menue.oeffnen(spieler);
            var event = klick(ClickType.LEFT, 10);
            menue.klicken(event);
            menue.klicken(event);
            verify(event, times(2)).setCancelled(true);
            assertEquals(1, naechsteTicks.size());
            verify(server, times(1)).createInventory(any(), anyInt(), any(Component.class));
            naechsteTicks.remove().run();
            verify(server, times(2)).createInventory(any(), anyInt(), any(Component.class));
            var ziehen = mock(InventoryDragEvent.class);
            when(ziehen.getView()).thenReturn(ansicht);
            menue.ziehen(ziehen);
            verify(ziehen).setCancelled(true);
        }
    }

    @Test
    void ignoriertKlicksWennDasMenueInzwischenGeschlossenOderDieBerechtigungEntzogenWurde() {
        try (var items = mockConstruction(ItemStack.class)) {
            menue.oeffnen(spieler);
            menue.klicken(klick(ClickType.LEFT, 10));
            when(ansicht.getTopInventory()).thenReturn(mock(Inventory.class));
            naechsteTicks.remove().run();
            verify(server, times(1)).createInventory(any(), anyInt(), any(Component.class));
            when(ansicht.getTopInventory()).thenReturn(inventar);
            menue.klicken(klick(ClickType.LEFT, 10));
            when(spieler.hasPermission("simpleplots.use")).thenReturn(false);
            naechsteTicks.remove().run();
            verify(server, times(1)).createInventory(any(), anyInt(), any(Component.class));
        }
    }

    @Test
    void ignoriertShiftKlicksUndSlotsAusDemSpielerinventar() {
        try (var items = mockConstruction(ItemStack.class)) {
            menue.oeffnen(spieler);
            menue.klicken(klick(ClickType.SHIFT_LEFT, 10));
            menue.klicken(klick(ClickType.LEFT, 60));
            assertTrue(naechsteTicks.isEmpty());
        }
    }

    @Test
    void behaeltDenGelesenenBeitragWennWaehrendDesMenueaufbausZusammengelegtWird() {
        var id = new PlotId(0, 0);
        UUID besitzer = UUID.randomUUID();
        var plot = Plot.neu(id, besitzer);
        when(wettbewerbe.aktuell()).thenReturn(Optional.of(new BauWettbewerb("Hafen", BauWettbewerb.Phase.BEENDET,
                Map.of(id, besitzer), Map.of())));
        when(plots.finden(id)).thenReturn(Optional.of(plot), Optional.empty());
        var offline = mock(OfflinePlayer.class);
        when(offline.getName()).thenReturn("Besitzer");
        when(server.getOfflinePlayer(besitzer)).thenReturn(offline);
        try (var items = mockConstruction(ItemStack.class)) {
            assertDoesNotThrow(() -> menue.wettbewerb(spieler));
            verify(plots, times(1)).finden(id);
            verify(spieler).openInventory(inventar);
        }
    }

    private InventoryClickEvent klick(ClickType typ, int slot) {
        var event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(ansicht);
        when(event.getWhoClicked()).thenReturn(spieler);
        when(event.getClick()).thenReturn(typ);
        when(event.getRawSlot()).thenReturn(slot);
        return event;
    }
}
