package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.storage.PlotYamlSpeicher;
import de.lightrixx.plot.paper.update.PlotUpdater;
import de.lightrixx.plot.paper.world.PlotEntfernen;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.paper.world.PlotZensur;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class PlotModerationZielTest {
    @TempDir Path ordner;

    @Test
    void waehltMitTildeDieMergeHauptIdUndLehntKonsoleWegeUndFremdeWeltenAb() throws IOException {
        var plots = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        var welt = mock(PlotWelt.class); var real = mock(World.class); var spieler = mock(Player.class);
        var zensur = mock(PlotZensur.class); var entfernen = mock(PlotEntfernen.class); var texte = mock(PlotNachrichten.class);
        var befehl = new PlotModerationBefehl(plots, welt, mock(WettbewerbDienst.class), mock(PlotAuftraege.class),
                texte, mock(PlotMenue.class), mock(PlotUpdater.class), zensur);
        befehl.entfernen(entfernen);
        var haupt = new PlotId(-2, 3); var zelle = new PlotId(-1, 3);
        var besitzer = UUID.randomUUID(); plots.claim(haupt, besitzer, 2); plots.claim(zelle, besitzer, 2);
        plots.zusammenlegen(haupt, zelle, besitzer, id -> false, true);
        var ort = new Location(real, -35, 65, 120);
        when(spieler.getWorld()).thenReturn(real); when(spieler.getLocation()).thenReturn(ort);
        when(welt.istPlotWelt(real)).thenReturn(true); when(welt.raster()).thenReturn(new PlotRaster(32, 7));
        when(welt.plotBei(ort)).thenReturn(Optional.of(haupt));
        befehl.ausfuehren(spieler, "censor", new String[]{"admin", "censor", "~"});
        verify(zensur).befehl(spieler, haupt, true);
        befehl.ausfuehren(spieler, "uncensor", new String[]{"admin", "uncensor", "~"});
        verify(zensur).befehl(spieler, haupt, false);
        befehl.ausfuehren(spieler, "remove", new String[]{"admin", "remove", "~"});
        verify(entfernen).befehl(spieler, haupt, false);
        var konsole = mock(CommandSender.class);
        befehl.ausfuehren(konsole, "censor", new String[]{"admin", "censor", "~"});
        verify(texte).senden(konsole, "fehler.nur-spieler");
        when(welt.plotBei(ort)).thenReturn(Optional.empty());
        befehl.ausfuehren(spieler, "censor", new String[]{"admin", "censor", "~"});
        verify(texte).senden(spieler, "fehler.auf-weg");
        when(welt.istPlotWelt(real)).thenReturn(false);
        befehl.ausfuehren(spieler, "remove", new String[]{"admin", "remove", "~"});
        verify(texte).senden(spieler, "fehler.keine-plotwelt");
    }

    @Test
    void waehltBeimRestoreMitTildeAuchDieEntfernteFlaeche() throws IOException {
        var plots = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        var id = new PlotId(-1, -1); plots.entfernen(id, Optional.empty(), ziel -> false);
        var welt = mock(PlotWelt.class); var real = mock(World.class); var spieler = mock(Player.class);
        when(spieler.getWorld()).thenReturn(real); when(spieler.getLocation()).thenReturn(new Location(real, -35, 70, -35));
        when(welt.istPlotWelt(real)).thenReturn(true); when(welt.raster()).thenReturn(new PlotRaster(32, 7));
        when(welt.plotBei(any())).thenReturn(Optional.empty());
        var entfernen = mock(PlotEntfernen.class);
        var befehl = new PlotModerationBefehl(plots, welt, mock(WettbewerbDienst.class), mock(PlotAuftraege.class),
                mock(PlotNachrichten.class), mock(PlotMenue.class), mock(PlotUpdater.class), mock(PlotZensur.class));
        befehl.entfernen(entfernen);
        befehl.ausfuehren(spieler, "restore", new String[]{"admin", "restore", "~"});
        verify(entfernen).befehl(spieler, id, true);
    }
}
