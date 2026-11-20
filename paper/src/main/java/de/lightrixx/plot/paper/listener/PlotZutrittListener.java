package de.lightrixx.plot.paper.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

public final class PlotZutrittListener implements Listener {
    private static final Title.Times TITELZEITEN = Title.Times.times(
            Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500));
    private final Plugin plugin;
    private final PlotWelt welt;
    private final PlotDienst plots;
    private final PlotNachrichten texte;

    public PlotZutrittListener(Plugin plugin, PlotWelt welt, PlotDienst plots, PlotNachrichten texte) {
        this.plugin = plugin;
        this.welt = welt;
        this.plots = plots;
        this.texte = texte;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bewegen(PlayerMoveEvent event) {
        if (event.getTo() != null && event.hasChangedBlock() && welt.istPlotWelt(event.getTo().getWorld())) {
            zutrittPruefen(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bewegungAnzeigen(PlayerMoveEvent event) {
        if (event.getTo() != null && event.hasChangedBlock()) {
            plotWechselAnzeigen(event.getPlayer(), event.getFrom(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleportieren(PlayerTeleportEvent event) {
        if (event.getTo() != null && welt.istPlotWelt(event.getTo().getWorld())) {
            if (!PlotBerechtigungen.modusErlaubt(event.getPlayer(), event.getPlayer().getGameMode())) {
                event.setCancelled(true);
                texte.senden(event.getPlayer(), "zugang.survival");
            } else {
                zutrittPruefen(event);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleportAnzeigen(PlayerTeleportEvent event) {
        var id = welt.plotBei(event.getTo());
        if (id.isEmpty() || id.equals(welt.plotBei(event.getFrom()))) {
            return;
        }
        Player spieler = event.getPlayer();
        // Nach dem Teleport senden, damit ein Dimensionswechsel die Anzeige nicht verwirft.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (spieler.isOnline() && id.equals(welt.plotBei(spieler.getLocation()))) {
                plotAnzeigen(spieler, id.get());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(PlayerPortalEvent event) {
        Location ziel = event.getTo();
        if (welt.istPlotWelt(event.getFrom().getWorld()) || (ziel != null && welt.istPlotWelt(ziel.getWorld()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void modusWechseln(PlayerGameModeChangeEvent event) {
        if (welt.istPlotWelt(event.getPlayer().getWorld())
                && !PlotBerechtigungen.modusErlaubt(event.getPlayer(), event.getNewGameMode())) {
            event.setCancelled(true);
            texte.senden(event.getPlayer(), "zugang.survival");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void respawn(PlayerRespawnEvent event) {
        Location ziel = event.getRespawnLocation();
        bannAmOrt(event.getPlayer(), ziel).ifPresent(id -> event.setRespawnLocation(welt.wegVor(ziel.getWorld(), id)));
    }

    @EventHandler
    public void respawnAbgeschlossen(PlayerPostRespawnEvent event) {
        ankunftAnzeigen(event.getPlayer());
    }

    @EventHandler
    public void beitreten(PlayerJoinEvent event) {
        ankunftAnzeigen(event.getPlayer());
    }

    @EventHandler
    public void weltWechseln(PlayerChangedWorldEvent event) {
        ankunft(event.getPlayer());
    }

    @EventHandler
    public void fahren(VehicleMoveEvent event) {
        if (welt.istPlotWelt(event.getTo().getWorld())) {
            for (var passagier : event.getVehicle().getPassengers()) {
                if (passagier instanceof Player spieler) {
                    bannAmOrt(spieler, event.getTo()).ifPresent(id -> aufWegSetzen(spieler, event.getTo(), id));
                }
            }
        }
    }

    private void zutrittPruefen(PlayerMoveEvent event) {
        Player spieler = event.getPlayer();
        Location ziel = event.getTo();
        bannAmOrt(spieler, ziel).ifPresent(id -> {
            if (bannAmOrt(spieler, event.getFrom()).isPresent()) {
                event.setTo(welt.wegVor(ziel.getWorld(), id));
            } else {
                event.setCancelled(true);
            }
            texte.senden(spieler, "zugang.gebannt");
        });
    }

    private void ankunftAnzeigen(Player spieler) {
        ankunft(spieler);
        welt.plotBei(spieler.getLocation()).ifPresent(id -> plotAnzeigen(spieler, id));
    }

    private void ankunft(Player spieler) {
        boolean plotwelt = welt.istPlotWelt(spieler.getWorld());
        PlotBerechtigungen.modusAmOrtPruefen(spieler, plotwelt);
        if (!plotwelt) {
            return;
        }
        Location ort = spieler.getLocation();
        bannAmOrt(spieler, ort).ifPresent(id -> aufWegSetzen(spieler, ort, id));
    }

    private void plotWechselAnzeigen(Player spieler, Location von, Location ziel) {
        var id = welt.plotBei(ziel);
        if (id.isPresent() && !id.equals(welt.plotBei(von))) {
            plotAnzeigen(spieler, id.get());
        }
    }

    private void plotAnzeigen(Player spieler, PlotId id) {
        var plot = plots.finden(id).orElse(null);
        var plotId = unparsed("id", id.toString());
        if (plot == null) {
            spieler.showTitle(Title.title(texte.komponente("betreten.frei-titel", plotId),
                    texte.komponente("betreten.frei-untertitel", plotId), TITELZEITEN));
            return;
        }
        if (!PlotBerechtigungen.darfBetreten(spieler, plot)) {
            return;
        }
        var profil = plot.profil();
        String besitzer = Objects.requireNonNullElse(spieler.getServer().getOfflinePlayer(plot.besitzer()).getName(),
                plot.besitzer().toString());
        TagResolver[] platzhalter = {
                plotId, unparsed("owner", besitzer), unparsed("name", profil.name()),
                unparsed("description", profil.beschreibung())
        };
        String titel = profil.name().isEmpty() ? "betreten.titel-ohne-name" : "betreten.titel";
        String untertitel = profil.beschreibung().isEmpty() ? "betreten.untertitel-ohne-beschreibung" : "betreten.untertitel";
        spieler.showTitle(Title.title(texte.komponente(titel, platzhalter), texte.komponente(untertitel, platzhalter), TITELZEITEN));
    }

    private void aufWegSetzen(Player spieler, Location ort, PlotId id) {
        if (spieler.teleport(welt.wegVor(ort.getWorld(), id))) {
            texte.senden(spieler, "zugang.gebannt");
        }
    }

    private Optional<PlotId> bannAmOrt(Player spieler, Location ort) {
        return welt.plotBei(ort).flatMap(plots::finden)
                .filter(plot -> !PlotBerechtigungen.darfBetreten(spieler, plot)).map(plot -> plot.id());
    }
}
