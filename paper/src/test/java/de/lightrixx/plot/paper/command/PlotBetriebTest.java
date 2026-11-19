package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotMenueKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import de.lightrixx.plot.storage.SpeicherAuftraege;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(10)
class PlotBetriebTest {
    @TempDir Path ordner;
    private final LinkedBlockingQueue<Runnable> serverthread = new LinkedBlockingQueue<>();
    private final PlotMenueKonfiguration gui = mock(PlotMenueKonfiguration.class);
    private final PlotMenue menue = mock(PlotMenue.class);
    private final PlotWelt welt = mock(PlotWelt.class);
    private final CommandSender sender = mock(CommandSender.class);
    private PlotKonfiguration konfiguration;
    private PlotNachrichten texte;
    private PlotAuftraege auftraege;
    private PlotBetrieb betrieb;

    @BeforeEach
    void vorbereiten() throws IOException {
        schreiben(32, 3);
        konfiguration = new PlotKonfiguration(ordner.resolve("config.yml"));
        texte = new PlotNachrichten(ordner.resolve("messages.yml"), getClass().getResourceAsStream("/config/messages.yml"));
        auftraege = new PlotAuftraege(new SpeicherAuftraege(4), serverthread::add, () -> true, mock(Logger.class), texte);
        when(gui.vorbereiten()).thenReturn(new PlotMenueKonfiguration.Daten(27, 27, 45, Map.of(), Map.of(), Map.of(), Map.of()));
        betrieb = new PlotBetrieb(mock(JavaPlugin.class), konfiguration, texte, gui, menue, welt,
                mock(PlotDienst.class), mock(WettbewerbDienst.class), auftraege);
    }

    @AfterEach
    void beenden() {
        assertTrue(auftraege.beenden(Duration.ofSeconds(5)));
    }

    @Test
    void uebernimmtAlleDateienErstNachErfolgreicherPruefungUndSchliesstAlteMenues() throws Exception {
        schreiben(32, 8);
        betrieb.neuladen(sender);
        assertTrue(betrieb.reloadLaeuft());
        assertEquals(3, konfiguration.claimLimit());
        naechsterTick();
        assertEquals(8, konfiguration.claimLimit());
        assertFalse(betrieb.reloadLaeuft());
        verify(gui).uebernehmen(any());
        verify(menue).schliessen();
    }

    @Test
    void behaeltBeiFehlerInDerLetztenDateiDenGesamtenAktivenStand() throws Exception {
        schreiben(32, 8);
        when(gui.vorbereiten()).thenThrow(new IOException("Ungültiger Menüslot"));
        var vorher = texte.komponente("admin.reload-erfolg");
        Files.writeString(ordner.resolve("messages.yml"), "admin:\n  reload-erfolg: 'Geänderter Text'\n");
        betrieb.neuladen(sender);
        naechsterTick();
        assertEquals(3, konfiguration.claimLimit());
        assertEquals(vorher, texte.komponente("admin.reload-erfolg"));
        assertFalse(betrieb.reloadLaeuft());
        verify(gui, never()).uebernehmen(any());
        verify(menue, never()).schliessen();
    }

    @Test
    void lehntEinGeaendertesRasterNachDerWeltgenerierungAb() throws Exception {
        when(welt.rasterFestgelegt()).thenReturn(true);
        when(welt.raster()).thenReturn(new PlotRaster(32, 7));
        schreiben(48, 8);
        betrieb.neuladen(sender);
        naechsterTick();
        assertEquals(new PlotRaster(32, 7), konfiguration.raster());
        assertEquals(3, konfiguration.claimLimit());
        verify(gui, never()).uebernehmen(any());
        verify(menue, never()).schliessen();
        assertFalse(betrieb.reloadLaeuft());
    }

    private void naechsterTick() throws InterruptedException {
        Runnable auftrag = serverthread.poll(5, TimeUnit.SECONDS);
        assertNotNull(auftrag);
        auftrag.run();
    }

    private void schreiben(int groesse, int limit) throws IOException {
        Files.writeString(ordner.resolve("config.yml"), "raster:\n  plotgroesse: " + groesse
                + "\n  wegbreite: 7\nclaims:\n  pro-spieler: " + limit + "\n");
    }
}
