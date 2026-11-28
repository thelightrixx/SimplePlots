package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.*;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public final class PlotGrenzen implements Listener {
    private record Anzeige(UUID welt, Plot plot, PlotDienst dienst, PlotKonfiguration config, List<PlotUmriss.Kante> kanten, long ende) {}
    private final Plugin plugin;
    private final Map<UUID, Anzeige> anzeigen = new HashMap<>();
    private final BukkitTask takt;
    private static final Particle.DustOptions FARBE = new Particle.DustOptions(Color.fromRGB(38, 189, 111), 1.3f);
    public PlotGrenzen(Plugin plugin) {
        this.plugin = plugin;
        takt = plugin.getServer().getScheduler().runTaskTimer(plugin, this::zeichnen, 10, 10);
    }
    public boolean zeigen(Player player, Plot plot, PlotDienst dienst, PlotKonfiguration config, int sekunden) {
        if (sekunden < 1 || sekunden > config.zahl("general.navigation.grenzen-max-sekunden") || plot.zellen().size() > 10000) return false;
        if (!anzeigen.containsKey(player.getUniqueId()) && anzeigen.size() >= Math.min(64, config.zahl("general.navigation.grenzen-max-spieler"))) return false;
        anzeigen.put(player.getUniqueId(), new Anzeige(player.getWorld().getUID(), plot, dienst, config,
                PlotUmriss.kanten(plot.zellen(), config.raster()), System.nanoTime() + sekunden * 1_000_000_000L));
        return true;
    }
    public void stoppen(Player player) { anzeigen.remove(player.getUniqueId()); }
    private void zeichnen() {
        for (var iterator = anzeigen.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next(); var a = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || System.nanoTime() >= a.ende() || !player.getWorld().getUID().equals(a.welt())
                    || !player.hasPermission(PlotBerechtigungen.USE) || !player.hasPermission("simpleplots.border")
                    || !a.dienst().finden(a.plot().id()).filter(a.plot()::equals).isPresent() || a.plot().zensiert()
                    || !PlotBerechtigungen.darfBetreten(player, a.plot())) { iterator.remove(); continue; }
            var location = player.getLocation();
            try {
                for (var point : PlotUmriss.punkte(a.kanten(), location.getX(), location.getZ(), a.config().zahl("general.navigation.grenzen-reichweite"), a.config().zahl("general.navigation.grenzen-punktabstand"), 128)) {
                    player.spawnParticle(Particle.DUST, point.x(), location.getY() + 0.15, point.z(), 1, 0, 0, 0, 0, FARBE);
                    player.spawnParticle(Particle.DUST, point.x(), location.getY() + 1.65, point.z(), 1, 0, 0, 0, 0, FARBE);
                }
            } catch (RuntimeException failure) {
                iterator.remove(); plugin.getSLF4JLogger().warn("Grenzanzeige für {} beendet.", player.getUniqueId(), failure);
            }
        }
    }
    @EventHandler public void verlassen(PlayerQuitEvent event) { stoppen(event.getPlayer()); }
    public void beenden() { takt.cancel(); anzeigen.clear(); }
}
