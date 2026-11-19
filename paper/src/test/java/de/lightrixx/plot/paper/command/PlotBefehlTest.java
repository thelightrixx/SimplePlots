package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Set;

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
            mock(PlotKonfiguration.class), texte, menue, mock(WettbewerbDienst.class), auftraege, betrieb);

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

    private static void rechte(CommandSender sender, String... rechte) {
        var erlaubt = Set.of(rechte);
        when(sender.hasPermission(anyString())).thenAnswer(aufruf -> erlaubt.contains(aufruf.getArgument(0)));
    }
}
