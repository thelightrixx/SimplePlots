package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.economy.Wirtschaft;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.Server;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotBefehlTest {
    private final PlotDienst plots = mock(PlotDienst.class);
    private final PlotWelt welt = mock(PlotWelt.class);
    private final PlotNachrichten texte = mock(PlotNachrichten.class);
    private final PlotMenue menue = mock(PlotMenue.class);
    private final PlotAuftraege auftraege = mock(PlotAuftraege.class);
    private final PlotBetrieb betrieb = mock(PlotBetrieb.class);
    private final PlotBefehl befehl = new PlotBefehl(mock(Server.class), mock(Logger.class), plots, welt,
            mock(PlotKonfiguration.class), texte, menue, mock(WettbewerbDienst.class), auftraege, betrieb,
            mock(Wirtschaft.class));

    @Test
    void verweigertBefehleOhneBasisrechtBevorSieZustandAnfassen() {
        var spieler = mock(Player.class);
        assertTrue(befehl.onCommand(spieler, null, "plot", new String[]{"claim"}));
        verify(texte).senden(spieler, "fehler.kein-recht");
        verifyNoInteractions(plots, welt, auftraege, menue);
        assertTrue(befehl.onTabComplete(spieler, null, "plot", new String[]{""}).isEmpty());
    }

    @Test
    void verweigertClaimsOhneClaimRechtUndLehntUeberzaehligeArgumenteAb() {
        var spieler = mock(Player.class);
        rechte(spieler, PlotBerechtigungen.USE);
        befehl.onCommand(spieler, null, "plot", new String[]{"claim"});
        verify(texte).senden(spieler, "fehler.kein-recht");
        rechte(spieler, PlotBerechtigungen.USE, PlotBerechtigungen.CLAIM);
        befehl.onCommand(spieler, null, "plot", new String[]{"claim", "versehentlich"});
        verify(texte).senden(eq(spieler), eq("fehler.syntax"), any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.class));
        verifyNoInteractions(plots, welt, auftraege);
    }

    @Test
    void zeigtDieVersionMitBasisrechtAuchInDerKonsoleUndNutztDenSpielerRueckkehrort() {
        var konsole = mock(CommandSender.class);
        rechte(konsole, PlotBerechtigungen.USE);
        befehl.onCommand(konsole, null, "plot", new String[]{"version"});
        verify(betrieb).version(konsole);
        var spieler = mock(Player.class);
        var plotwelt = mock(org.bukkit.World.class);
        var ziel = new org.bukkit.Location(mock(org.bukkit.World.class), 123, 75, -45, 80, 20);
        rechte(spieler, PlotBerechtigungen.USE);
        when(spieler.getWorld()).thenReturn(plotwelt);
        when(welt.istPlotWelt(plotwelt)).thenReturn(true);
        when(welt.rueckkehr(spieler)).thenReturn(ziel);
        befehl.onCommand(spieler, null, "plot", new String[]{"back"});
        verify(spieler).teleport(ziel);
        verify(welt, never()).rueckkehr();
    }

    @Test
    void bietetDiagnoseMitEinzelnemRechtAnUndGibtKeineWeiterenAdminAktionenFrei() {
        var sender = mock(CommandSender.class);
        rechte(sender, PlotBerechtigungen.USE, PlotBerechtigungen.DIAGNOSE);
        assertTrue(befehl.onTabComplete(sender, null, "plot", new String[]{""}).contains("admin"));
        assertEquals(List.of("diagnostics"), befehl.onTabComplete(sender, null, "plot", new String[]{"admin", ""}));
        befehl.onCommand(sender, null, "plot", new String[]{"admin", "diagnostics"});
        verify(betrieb).diagnose(sender);
        befehl.onCommand(sender, null, "plot", new String[]{"admin", "create"});
        verify(texte).senden(sender, "fehler.kein-recht");
        verifyNoInteractions(welt);
    }

    @Test
    void behandeltWeltLadefehlerAlsFehlgeschlagenenBefehl() throws IOException {
        var sender = mock(CommandSender.class);
        rechte(sender, PlotBerechtigungen.USE, PlotBerechtigungen.ADMIN);
        when(welt.ladenOderErstellen()).thenThrow(new IOException("Welt gesperrt"));
        assertTrue(befehl.onCommand(sender, null, "plot", new String[]{"admin", "create"}));
        verify(texte).senden(sender, "fehler.verarbeitung");
        verify(texte, never()).senden(sender, "admin.bereit");
    }

    @Test
    void bietetAlleAdminModiAnUndSetztSieNurMitAdminrecht() {
        var spieler = mock(Player.class);
        var plotwelt = mock(org.bukkit.World.class);
        var pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        var modus = new AtomicReference<>(GameMode.SURVIVAL);
        rechte(spieler, PlotBerechtigungen.USE, PlotBerechtigungen.ADMIN);
        assertTrue(befehl.onTabComplete(spieler, null, "plot", new String[]{""}).contains("fly"));
        assertEquals(List.of("off", "on"), befehl.onTabComplete(spieler, null, "plot", new String[]{"fly", ""}));
        when(spieler.getWorld()).thenReturn(plotwelt);
        when(welt.istPlotWelt(plotwelt)).thenReturn(true);
        when(spieler.getPersistentDataContainer()).thenReturn(pdc);
        when(spieler.getGameMode()).thenAnswer(aufruf -> modus.get());
        doAnswer(aufruf -> { modus.set(aufruf.getArgument(0)); return null; }).when(spieler).setGameMode(any());
        assertEquals(Set.of("survival", "creative", "adventure", "spectator"),
                Set.copyOf(befehl.onTabComplete(spieler, null, "plot", new String[]{"admin", "gamemode", ""})));
        for (GameMode wert : GameMode.values()) {
            befehl.onCommand(spieler, null, "plot", new String[]{"admin", "gamemode", wert.name().toLowerCase(java.util.Locale.ROOT)});
            assertEquals(wert, modus.get());
        }
        rechte(spieler, PlotBerechtigungen.USE);
        befehl.onCommand(spieler, null, "plot", new String[]{"admin", "gamemode", "spectator"});
        verify(texte).senden(spieler, "fehler.kein-recht");
        verify(spieler, times(GameMode.values().length)).setGameMode(any());
    }

    private static void rechte(CommandSender sender, String... rechte) {
        var erlaubt = Set.of(rechte);
        when(sender.hasPermission(anyString())).thenAnswer(aufruf -> erlaubt.contains(aufruf.getArgument(0)));
    }
}
