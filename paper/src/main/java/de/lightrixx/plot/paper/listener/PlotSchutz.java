package de.lightrixx.plot.paper.listener;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotRechte;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.world.PlotGenerator;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;

final class PlotSchutz {
    private final PlotWelt welt;
    private final PlotDienst plots;
    private final WettbewerbDienst wettbewerbe;

    PlotSchutz(PlotWelt welt, PlotDienst plots, WettbewerbDienst wettbewerbe) {
        this.welt = welt;
        this.plots = plots;
        this.wettbewerbe = wettbewerbe;
    }

    boolean inPlotwelt(Location ort) {
        return ort != null && welt.istPlotWelt(ort.getWorld());
    }

    boolean inPlotwelt(Block block) {
        return welt.istPlotWelt(block.getWorld());
    }

    Optional<Plot> beanspruchterPlot(Location ort) {
        return welt.plotBei(ort).flatMap(plots::finden);
    }

    boolean darfBenutzen(Player spieler, Location ort) {
        if (!inPlotwelt(ort)) {
            return true;
        }
        if (PlotBerechtigungen.istAdmin(spieler)) {
            return !wettbewerbe.hatGesperrtePlots() || welt.plotBei(ort).filter(wettbewerbe::gesperrt).isEmpty();
        }
        return freierPlotFuer(spieler, ort)
                .filter(plot -> plot.darfBauen(spieler.getUniqueId(), besitzerOnline(spieler, plot))).isPresent();
    }

    boolean darfLagerNutzen(Player spieler, Location ort) {
        return freierPlotFuer(spieler, ort)
                .filter(plot -> plot.darfLagerNutzen(spieler.getUniqueId(), besitzerOnline(spieler, plot))).isPresent();
    }

    boolean hatBesucherRecht(Player spieler, Block block, PlotRechte.BesucherRecht recht) {
        return freierPlotFuer(spieler, block.getLocation())
                .filter(plot -> plot.rechte().besucher().contains(recht)).isPresent();
    }

    private Optional<Plot> freierPlotFuer(Player spieler, Location ort) {
        if (spieler == null || spieler.getGameMode() != GameMode.SURVIVAL) {
            return Optional.empty();
        }
        return beanspruchterPlot(ort).filter(plot -> !wettbewerbe.gesperrt(plot.id()) && plot.darfBetreten(spieler.getUniqueId()));
    }

    private static boolean besitzerOnline(Player spieler, Plot plot) {
        return plot.rechte().bauhelfer().contains(spieler.getUniqueId()) && spieler.getServer().getPlayer(plot.besitzer()) != null;
    }

    boolean darfBlockAendern(Player spieler, Location ort) {
        if (!inPlotwelt(ort)) {
            return true;
        }
        // Öffentliche Wege und unbeanspruchte Flächen behalten ihren festen Boden.
        if (!ueberWeltboden(ort) || (ort.getBlockY() <= PlotGenerator.BODEN_Y && beanspruchterPlot(ort).isEmpty())) {
            return false;
        }
        return darfBenutzen(spieler, ort);
    }

    boolean blockImClaim(Block block) {
        Location ort = block.getLocation();
        return ueberWeltboden(ort) && beanspruchterPlot(ort)
                .filter(plot -> !wettbewerbe.gesperrt(plot.id())).isPresent();
    }

    boolean wettbewerbGesperrt(Block block) {
        return wettbewerbe.hatGesperrtePlots()
                && welt.plotBei(block.getLocation()).filter(wettbewerbe::gesperrt).isPresent();
    }

    boolean selberClaim(Location quelle, Location ziel) {
        return ueberWeltboden(quelle) && ueberWeltboden(ziel) && beanspruchterPlot(quelle)
                .filter(plot -> !wettbewerbe.gesperrt(plot.id()) && welt.plotBei(ziel).filter(plot.id()::equals).isPresent())
                .isPresent();
    }

    private boolean ueberWeltboden(Location ort) {
        return inPlotwelt(ort) && ort.getBlockY() > ort.getWorld().getMinHeight();
    }

    boolean selberClaim(Block quelle, Block ziel) {
        return quelle != null && selberClaim(quelle.getLocation(), ziel.getLocation());
    }
}
