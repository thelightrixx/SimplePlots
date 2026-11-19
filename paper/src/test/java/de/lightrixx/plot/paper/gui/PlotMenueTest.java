package de.lightrixx.plot.paper.gui;

import de.lightrixx.plot.paper.command.PlotAuftraege;
import de.lightrixx.plot.paper.config.PlotMenueKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
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
        menue = new PlotMenue(plugin, mock(PlotDienst.class), mock(PlotWelt.class), texte,
                mock(WettbewerbDienst.class), gui, mock(PlotAuftraege.class));
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

    private InventoryClickEvent klick(ClickType typ, int slot) {
        var event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(ansicht);
        when(event.getWhoClicked()).thenReturn(spieler);
        when(event.getClick()).thenReturn(typ);
        when(event.getRawSlot()).thenReturn(slot);
        return event;
    }
}
