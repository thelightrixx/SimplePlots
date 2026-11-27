package de.lightrixx.plot.paper.listener;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotGenerator;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlotFlug implements Listener {
    private static final NamespacedKey EIGEN = new NamespacedKey("simpleplots", "eigenes-fly");
    private static final NamespacedKey VERWALTET = new NamespacedKey("simpleplots", "flug-geschwindigkeit-vorher");
    private final JavaPlugin plugin;
    private final PlotWelt welt;
    private final PlotDienst plots;
    private final PlotNachrichten texte;
    private final Map<UUID, Long> fallsicher = new HashMap<>();
    private long ticks;
    private java.util.function.Predicate<org.bukkit.World> anderePlotwelt = welt -> false;

    public void anderePlotwelten(java.util.function.Predicate<org.bukkit.World> pruefung) { anderePlotwelt = java.util.Objects.requireNonNull(pruefung); }

    public PlotFlug(JavaPlugin plugin, PlotWelt welt, PlotDienst plots, PlotNachrichten texte) {
        this.plugin = plugin;
        this.welt = welt;
        this.plots = plots;
        this.texte = texte;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ticks++;
            fallsicher.entrySet().removeIf(eintrag -> eintrag.getValue() < ticks
                    || (plugin.getServer().getPlayer(eintrag.getKey()) instanceof Player spieler && spieler.isOnGround()));
            if (ticks % welt.konfiguration().zahl("fly.pruefintervall-ticks") == 0) {
                for (Player spieler : plugin.getServer().getOnlinePlayers()) aktualisieren(spieler);
            }
        }, 1, 1);
    }

    public void befehl(Player spieler, String[] args) {
        if (!PlotBerechtigungen.istAdmin(spieler) && !spieler.hasPermission(PlotBerechtigungen.FLY)) {
            texte.senden(spieler, "fehler.kein-recht");
            return;
        }
        if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            texte.senden(spieler, "flug.hilfe");
            return;
        }
        boolean aktiv = args.length == 1 ? !eingeschaltet(spieler) : args[1].equalsIgnoreCase("on");
        if (aktiv && !PlotBerechtigungen.istAdmin(spieler) && !welt.konfiguration().aktiv("fly.eigener-plot.aktiviert")) {
            texte.senden(spieler, "fehler.deaktiviert");
            return;
        }
        spieler.getPersistentDataContainer().set(EIGEN, PersistentDataType.BOOLEAN, aktiv);
        aktualisieren(spieler);
        if (aktiv && eigenerFlug(spieler, spieler.getLocation()) && verwaltet(spieler)) spieler.setFlying(true);
        texte.senden(spieler, aktiv ? (PlotBerechtigungen.istAdmin(spieler) ? "flug.admin-an" : "flug.an") : "flug.aus");
    }

    private boolean eingeschaltet(Player spieler) {
        return Boolean.TRUE.equals(spieler.getPersistentDataContainer().get(EIGEN, PersistentDataType.BOOLEAN));
    }

    boolean erlaubt(Player spieler, Location ort) {
        if ((spieler.getGameMode() != GameMode.SURVIVAL
                && !(spieler.getGameMode() == GameMode.ADVENTURE && PlotBerechtigungen.istAdmin(spieler)))
                || spieler.isDead() || spieler.isInsideVehicle()
                || !welt.istPlotWelt(ort.getWorld())) return false;
        return (welt.plotBei(ort).isEmpty() && welt.konfiguration().aktiv("fly.wege.aktiviert")) || eigenerFlug(spieler, ort);
    }

    private boolean eigenerFlug(Player spieler, Location ort) {
        return welt.istPlotWelt(ort.getWorld()) && eingeschaltet(spieler) && (PlotBerechtigungen.istAdmin(spieler)
                || (spieler.hasPermission(PlotBerechtigungen.FLY) && welt.konfiguration().aktiv("fly.eigener-plot.aktiviert")
                && ort.getY() <= PlotGenerator.BODEN_Y + 1 + welt.konfiguration().zahl("fly.eigener-plot.hoehe")
                && welt.plotBei(ort).flatMap(plots::finden)
                .filter(plot -> !plot.zensiert() && plot.besitzer().equals(spieler.getUniqueId())).isPresent()));
    }

    public void aktualisieren(Player spieler) {
        if (anderePlotwelt.test(spieler.getWorld())) return;
        boolean verwaltet = verwaltet(spieler);
        if (!erlaubt(spieler, spieler.getLocation())) {
            if (verwaltet) freigeben(spieler);
            return;
        }
        if (!verwaltet && spieler.getAllowFlight()) return;
        if (!verwaltet) {
            spieler.getPersistentDataContainer().set(VERWALTET, PersistentDataType.FLOAT, spieler.getFlySpeed());
            spieler.setAllowFlight(true);
        }
        String bereich = welt.plotBei(spieler.getLocation()).isEmpty() ? "wege" : "eigener-plot";
        spieler.setFlySpeed((float) welt.konfiguration().dezimal("fly." + bereich + ".geschwindigkeit"));
    }

    private boolean verwaltet(Player spieler) { return spieler.getPersistentDataContainer().has(VERWALTET, PersistentDataType.FLOAT); }

    private void freigeben(Player spieler) {
        Float vorher = spieler.getPersistentDataContainer().get(VERWALTET, PersistentDataType.FLOAT);
        if (vorher == null) return;
        if (spieler.getGameMode() != GameMode.CREATIVE && spieler.getGameMode() != GameMode.SPECTATOR) {
            boolean flog = spieler.isFlying();
            spieler.setFlying(false);
            spieler.setAllowFlight(false);
            if (flog) {
                spieler.setFallDistance(0);
                fallsicher.put(spieler.getUniqueId(), ticks + 240);
            }
        }
        spieler.setFlySpeed(vorher);
        spieler.getPersistentDataContainer().remove(VERWALTET);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bewegen(PlayerMoveEvent event) {
        if (!event.hasChangedPosition() || event.getTo() == null) return;
        Player spieler = event.getPlayer();
        if (verwaltet(spieler) && spieler.isFlying() && welt.istPlotWelt(event.getFrom().getWorld())
                && spieler.getGameMode() != GameMode.CREATIVE && spieler.getGameMode() != GameMode.SPECTATOR
                && event.getFrom().getWorld().equals(event.getTo().getWorld()) && !erlaubt(spieler, event.getTo())
                && !(event instanceof org.bukkit.event.player.PlayerTeleportEvent)) {
            var von = welt.plotBei(event.getFrom());
            Location sicher = von.map(id -> welt.wegVor(spieler.getWorld(), id)).orElseGet(() -> {
                Location ort = event.getFrom().clone();
                ort.setY(PlotGenerator.BODEN_Y + 1.5);
                return ort;
            });
            event.setTo(sicher);
            spieler.setFlying(false);
            spieler.setFallDistance(0);
            fallsicher.put(spieler.getUniqueId(), ticks + 240);
            texte.senden(spieler, "flug.grenze");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void angekommen(PlayerMoveEvent event) {
        if (event instanceof org.bukkit.event.player.PlayerTeleportEvent) plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) aktualisieren(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void starten(PlayerToggleFlightEvent event) {
        if (anderePlotwelt.test(event.getPlayer().getWorld())) return;
        if (verwaltet(event.getPlayer()) && event.isFlying()
                && event.getPlayer().getGameMode() != GameMode.CREATIVE && event.getPlayer().getGameMode() != GameMode.SPECTATOR
                && !erlaubt(event.getPlayer(), event.getPlayer().getLocation())) {
            event.setCancelled(true);
            freigeben(event.getPlayer());
        }
    }

    @EventHandler
    public void beitreten(PlayerJoinEvent event) {
        if (anderePlotwelt.test(event.getPlayer().getWorld())) return;
        freigeben(event.getPlayer()); aktualisieren(event.getPlayer());
    }
    @EventHandler
    public void weltwechsel(PlayerChangedWorldEvent event) { aktualisieren(event.getPlayer()); }
    @EventHandler
    public void respawn(PlayerPostRespawnEvent event) {
        if (anderePlotwelt.test(event.getPlayer().getWorld())) return;
        freigeben(event.getPlayer()); aktualisieren(event.getPlayer());
    }
    @EventHandler
    public void verlassen(PlayerQuitEvent event) { freigeben(event.getPlayer()); fallsicher.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fallschaden(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player spieler && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && fallsicher.getOrDefault(spieler.getUniqueId(), -1L) >= ticks) {
            event.setCancelled(true);
            fallsicher.remove(spieler.getUniqueId());
        }
    }

    public void beenden() {
        for (Player spieler : plugin.getServer().getOnlinePlayers()) freigeben(spieler);
    }
}
