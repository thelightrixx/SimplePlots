package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class PlotWeltenBefehlTest {
    @Test
    void verwendetSpielerweltOderStandardUndErlaubtExpliziteWeltenFuerKonsole() {
        var normal = mock(PlotWelt.class); var creative = mock(PlotWelt.class);
        var normalBefehl = mock(TabExecutor.class); var creativeBefehl = mock(TabExecutor.class);
        var welt = mock(World.class); var spieler = mock(Player.class); var konsole = mock(CommandSender.class);
        when(spieler.getWorld()).thenReturn(welt); when(creative.istPlotWelt(welt)).thenReturn(true);
        when(spieler.hasPermission(anyString())).thenReturn(true); when(konsole.hasPermission(anyString())).thenReturn(true);
        var router = new PlotWeltenBefehl(Map.of("plots", new PlotWeltenBefehl.Kontext(normal, normalBefehl),
                "creative", new PlotWeltenBefehl.Kontext(creative, creativeBefehl)), () -> "plots", mock(PlotNachrichten.class));
        router.onCommand(spieler, null, "plot", new String[]{"claim"});
        verify(creativeBefehl).onCommand(eq(spieler), isNull(), eq("plot"), aryEq(new String[]{"claim"}));
        router.onCommand(konsole, null, "plot", new String[]{"admin", "info"});
        verify(normalBefehl).onCommand(eq(konsole), isNull(), eq("plot"), aryEq(new String[]{"admin", "info"}));
        router.onCommand(konsole, null, "plot", new String[]{"in", "creative", "admin", "remove", "0;0"});
        verify(creativeBefehl).onCommand(eq(konsole), isNull(), eq("plot"), aryEq(new String[]{"admin", "remove", "0;0"}));
        router.onCommand(spieler, null, "plot", new String[]{"world", "plots"});
        verify(normalBefehl).onCommand(eq(spieler), isNull(), eq("plot"), aryEq(new String[]{"world"}));
        assertEquals(List.of("creative"), router.onTabComplete(spieler, null, "plot", new String[]{"world", "cr"}));
    }

    @Test
    void weistUnbekannteWeltUndFehlendesBasisrechtAb() {
        var text = mock(PlotNachrichten.class); var ausfuehren = mock(TabExecutor.class);
        var router = new PlotWeltenBefehl(Map.of("plots", new PlotWeltenBefehl.Kontext(mock(PlotWelt.class), ausfuehren)), () -> "plots", text);
        var sender = mock(CommandSender.class);
        router.onCommand(sender, null, "plot", new String[]{"worlds"});
        verify(text).senden(sender, "fehler.kein-recht"); verifyNoInteractions(ausfuehren);
        when(sender.hasPermission(anyString())).thenReturn(true);
        router.onCommand(sender, null, "plot", new String[]{"world", "unbekannt"});
        verify(text).senden(sender, "welten.unbekannt"); verifyNoInteractions(ausfuehren);
    }
}
