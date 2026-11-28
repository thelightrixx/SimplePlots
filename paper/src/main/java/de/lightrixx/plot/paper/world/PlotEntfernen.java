package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotEntfernung;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.command.PlotAuftraege;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.Predicate;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

/** Speichert den Auftrag vor der Weltänderung; nach einem Abbruch ist jeder Schritt wiederholbar. */
public final class PlotEntfernen implements Listener {
    private final JavaPlugin plugin;
    private final PlotWelt welt;
    private final PlotDienst plots;
    private final PlotAuftraege auftraege;
    private final PlotNachrichten texte;
    private final Runnable ansichtenAktualisieren;
    private final Map<PlotId, Arbeit> arbeiten = new LinkedHashMap<>();
    private Predicate<PlotId> sperre = id -> false;

    public PlotEntfernen(JavaPlugin plugin, PlotWelt welt, PlotDienst plots, PlotAuftraege auftraege,
                         PlotNachrichten texte, Runnable ansichtenAktualisieren) {
        this.plugin = plugin; this.welt = welt; this.plots = plots; this.auftraege = auftraege;
        this.texte = texte; this.ansichtenAktualisieren = ansichtenAktualisieren;
    }

    public void fortsetzen() throws IOException {
        for (var eintrag : plots.entfernungen()) {
            if (eintrag.phase() != PlotEntfernung.Phase.ENTFERNT)
                beginnen(plugin.getServer().getConsoleSender(), welt.ladenOderErstellen(), eintrag);
        }
    }

    public void befehl(CommandSender sender, PlotId id, boolean wiederherstellen) throws IOException {
        var vorhanden = plots.entfernung(id);
        if (arbeiten.containsKey(vorhanden.map(PlotEntfernung::id).orElse(id))) {
            texte.senden(sender, "entfernen.laeuft"); return;
        }
        if (arbeiten.size() >= 8) { texte.senden(sender, "fehler.ausgelastet"); return; }
        if (wiederherstellen) {
            if (vorhanden.isEmpty()) { texte.senden(sender, "entfernen.nicht-entfernt"); return; }
            var eintrag = vorhanden.get();
            if (eintrag.phase() == PlotEntfernung.Phase.ENTFERNEN) {
                beginnen(sender, welt.ladenOderErstellen(), eintrag); return;
            }
            var plotwelt = welt.ladenOderErstellen();
            auftraege.ausfuehren(sender, "/simpleplots admin restore",
                    () -> plots.entfernungsphase(eintrag, PlotEntfernung.Phase.WIEDERHERSTELLEN), erfolgreich -> {
                        if (erfolgreich) beginnen(sender, plotwelt, eintrag.mitPhase(PlotEntfernung.Phase.WIEDERHERSTELLEN));
                        else texte.senden(sender, "entfernen.veraendert");
                    });
            return;
        }
        if (vorhanden.isPresent()) {
            if (vorhanden.get().phase() == PlotEntfernung.Phase.ENTFERNEN)
                beginnen(sender, welt.ladenOderErstellen(), vorhanden.get());
            else texte.senden(sender, "entfernen.bereits-entfernt");
            return;
        }
        var erwartet = plots.finden(id);
        var eintrag = new PlotEntfernung(erwartet.map(Plot::id).orElse(id),
                erwartet.map(Plot::zellen).orElseGet(() -> Set.of(id)), PlotEntfernung.Phase.ENTFERNEN);
        // Grenzen und Aufwand vor dem Speichern prüfen, damit kein unausführbarer Auftrag entsteht.
        try {
            int maximum = welt.konfiguration().zahl("protection.zensur-maximale-chunks");
            for (PlotId zelle : eintrag.zellen()) {
                welt.raster().mitteX(zelle); welt.raster().mitteZ(zelle); welt.raster().nordwegZ(zelle);
                Math.toIntExact((long) zelle.x() * welt.raster().rasterweite() + welt.raster().groesse() - 1);
                Math.toIntExact((long) zelle.z() * welt.raster().rasterweite() + welt.raster().groesse() - 1);
            }
            if ((long) eintrag.zellen().size() * welt.raster().groesse() * welt.raster().groesse() > maximum * 256L) {
                texte.senden(sender, "zensur.zu-gross"); return;
            }
            var teile = flaechen(eintrag);
            if (teile.stream().map(teil -> ((long) (teil.x() >> 4) << 32) | ((teil.z() >> 4) & 0xffffffffL)).distinct().count() > maximum) {
                texte.senden(sender, "zensur.zu-gross"); return;
            }
        } catch (ArithmeticException e) { texte.senden(sender, "fehler.plot-id"); return; }
        World plotwelt = welt.ladenOderErstellen();
        auftraege.ausfuehren(sender, "/simpleplots admin remove", () -> plots.entfernen(id, erwartet, sperre), erfolgreich -> {
            if (erfolgreich) beginnen(sender, plotwelt, eintrag);
            else texte.senden(sender, "entfernen.veraendert");
        });
    }

    public void sperre(Predicate<PlotId> pruefung) { sperre = Objects.requireNonNull(pruefung); }

    private List<PlotZensur.Flaeche> flaechen(PlotEntfernung eintrag) {
        return PlotZensur.flaechen(eintrag.zellen(), welt.raster());
    }

    private void beginnen(CommandSender sender, World plotwelt, PlotEntfernung eintrag) {
        if (arbeiten.containsKey(eintrag.id())) return;
        var arbeit = new Arbeit(sender, plotwelt, eintrag, flaechen(eintrag));
        arbeiten.put(eintrag.id(), arbeit);
        ansichtenAktualisieren.run();
        texte.senden(sender, "entfernen.start", unparsed("id", eintrag.id().toString()));
        spaeter(arbeit);
    }

    private void schritt(Arbeit arbeit) {
        if (arbeiten.values().iterator().next() != arbeit) { spaeter(arbeit); return; }
        // Erst nach erfolgreicher Auftragsspeicherung Spieler und Entities aus der Fläche entfernen.
        for (Player spieler : arbeit.welt.getPlayers()) {
            if (gehoertZu(arbeit, spieler.getLocation()) && !spieler.teleport(welt.wegVor(arbeit.welt, arbeit.eintrag.id())))
                throw new IllegalStateException("Spieler konnte vor dem Entfernen nicht sicher versetzt werden.");
        }
        long ende = System.nanoTime() + welt.konfiguration().zahl("protection.zensur-zeitbudget-millis") * 1_000_000L;
        int abschnitte = 0;
        while (arbeit.teil < arbeit.teile.size()) {
            var teil = arbeit.teile.get(arbeit.teil);
            int cx = teil.x() >> 4, cz = teil.z() >> 4;
            if (arbeit.ticketX != cx || arbeit.ticketZ != cz) {
                ticketFreigeben(arbeit);
                arbeit.welt.addPluginChunkTicket(cx, cz, plugin);
                arbeit.ticketX = cx; arbeit.ticketZ = cz;
                for (var entity : arbeit.welt.getChunkAt(cx, cz).getEntities()) {
                    if (!(entity instanceof Player) && gehoertZu(arbeit, entity.getLocation())) entity.remove();
                }
            }
            boolean restore = arbeit.eintrag.phase() == PlotEntfernung.Phase.WIEDERHERSTELLEN;
            for (int x = teil.x(); x < teil.x() + teil.breite(); x++) {
                for (int z = teil.z(); z < teil.z() + teil.tiefe(); z++) {
                    for (int y = arbeit.y; y < Math.min(arbeit.y + 16, arbeit.welt.getMaxHeight()); y++) {
                        var block = arbeit.welt.getBlockAt(x, y, z);
                        Material material = restore ? standardMaterial(arbeit.welt, x, y, z) : Material.AIR;
                        // Ein Zwischenzustand entfernt auch NBT von vorherigen Containern.
                        if (restore && block.getType() != Material.AIR) block.setType(Material.AIR, false);
                        if (block.getType() != material) block.setType(material, false);
                    }
                }
            }
            arbeit.y += 16;
            if (arbeit.y >= arbeit.welt.getMaxHeight()) {
                arbeit.y = arbeit.welt.getMinHeight(); arbeit.teil++;
            }
            if (++abschnitte >= welt.konfiguration().zahl("protection.zensur-abschnitte-pro-tick") || System.nanoTime() >= ende) break;
        }
        if (arbeit.teil < arbeit.teile.size()) { spaeter(arbeit); return; }
        arbeit.welt.save(true);
        boolean[] abgeschlossen = {false};
        auftraege.ausfuehren(plugin.getServer().getConsoleSender(), "Plotfläche " + arbeit.eintrag.id(), () -> {
            return arbeit.eintrag.phase() == PlotEntfernung.Phase.WIEDERHERSTELLEN ? plots.wiederhergestellt(arbeit.eintrag)
                    : plots.entfernungsphase(arbeit.eintrag, PlotEntfernung.Phase.ENTFERNT);
        }, erfolgreich -> {
            if (!erfolgreich) throw new IllegalStateException("Entfernungsauftrag wurde zwischenzeitlich geändert.");
            abgeschlossen[0] = true;
            beenden(arbeit);
            welt.konfigurationAktualisieren();
            ansichtenAktualisieren.run();
            texte.senden(arbeit.sender, arbeit.eintrag.phase() == PlotEntfernung.Phase.WIEDERHERSTELLEN
                    ? "entfernen.wiederhergestellt" : "entfernen.fertig", unparsed("id", arbeit.eintrag.id().toString()));
        }, () -> { if (!abgeschlossen[0]) { beenden(arbeit); texte.senden(arbeit.sender, "entfernen.fehler"); } });
    }

    private Material standardMaterial(World plotwelt, int x, int y, int z) {
        var config = welt.konfiguration();
        if (y == plotwelt.getMinHeight()) return Material.BEDROCK;
        if (y < PlotGenerator.BODEN_Y - config.zahl("world.erdschichten")) return config.material("world.material.untergrund");
        if (y < PlotGenerator.BODEN_Y) return config.material("world.material.erde");
        if (y == PlotGenerator.BODEN_Y) return config.material("world.material." + (welt.raster().istWeg(x, z) ? "weg" : "oberflaeche"));
        return Material.AIR;
    }

    private boolean gehoertZu(Arbeit arbeit, Location ort) {
        return welt.istPlotWelt(ort.getWorld()) && plots.entfernungBei(welt.raster(), ort.getBlockX(), ort.getBlockZ())
                .filter(eintrag -> eintrag.id().equals(arbeit.eintrag.id())).isPresent();
    }

    private boolean entfernt(Location ort) {
        return welt.istPlotWelt(ort.getWorld()) && plots.entfernungBei(welt.raster(), ort.getBlockX(), ort.getBlockZ()).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bewegen(PlayerMoveEvent event) {
        if (event.getTo() == null || (!event.hasChangedBlock() && event.getFrom().getWorld() == event.getTo().getWorld())
                || !entfernt(event.getTo())) return;
        var eintrag = plots.entfernungBei(welt.raster(), event.getTo().getBlockX(), event.getTo().getBlockZ()).orElseThrow();
        // Restore mit ~ bleibt über der leeren Fläche für berechtigte fliegende Spieler erreichbar.
        if (eintrag.phase() == PlotEntfernung.Phase.ENTFERNT && event.getPlayer().hasPermission("simpleplots.admin.restore")
                && (event.getPlayer().isFlying() || event.getPlayer().getGameMode() == GameMode.SPECTATOR)) return;
        event.setTo(welt.wegVor(event.getTo().getWorld(), eintrag.id()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleportieren(PlayerTeleportEvent event) { bewegen(event); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void respawn(PlayerRespawnEvent event) {
        Location ort = event.getRespawnLocation();
        if (entfernt(ort)) plots.entfernungBei(welt.raster(), ort.getBlockX(), ort.getBlockZ())
                .ifPresent(eintrag -> event.setRespawnLocation(welt.wegVor(ort.getWorld(), eintrag.id())));
    }

    @EventHandler
    public void beitreten(PlayerJoinEvent event) {
        Location ort = event.getPlayer().getLocation();
        if (entfernt(ort)) plots.entfernungBei(welt.raster(), ort.getBlockX(), ort.getBlockZ())
                .ifPresent(eintrag -> {
                    if (!event.getPlayer().teleport(welt.wegVor(ort.getWorld(), eintrag.id())))
                        plugin.getSLF4JLogger().warn("Spieler {} konnte nicht aus entfernter Plotfläche {} versetzt werden.",
                                event.getPlayer().getUniqueId(), eintrag.id());
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void physik(BlockPhysicsEvent event) { if (entfernt(event.getBlock().getLocation())) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityErzeugen(EntitySpawnEvent event) { if (entfernt(event.getLocation())) event.setCancelled(true); }

    private void spaeter(Arbeit arbeit) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (arbeiten.get(arbeit.eintrag.id()) != arbeit) return;
            try { schritt(arbeit); }
            catch (RuntimeException e) {
                plugin.getSLF4JLogger().error("Plotfläche {}: Arbeit unterbrochen; Auftrag bleibt fortsetzbar.", arbeit.eintrag.id(), e);
                beenden(arbeit); texte.senden(arbeit.sender, "entfernen.fehler");
            }
        });
    }

    public void beenden() { for (var arbeit : List.copyOf(arbeiten.values())) beenden(arbeit); }
    private void beenden(Arbeit arbeit) { arbeiten.remove(arbeit.eintrag.id()); ticketFreigeben(arbeit); }
    private void ticketFreigeben(Arbeit arbeit) {
        if (arbeit.ticketX != Integer.MIN_VALUE) arbeit.welt.removePluginChunkTicket(arbeit.ticketX, arbeit.ticketZ, plugin);
        arbeit.ticketX = Integer.MIN_VALUE; arbeit.ticketZ = Integer.MIN_VALUE;
    }

    private static final class Arbeit {
        final CommandSender sender;
        final World welt;
        final PlotEntfernung eintrag;
        final List<PlotZensur.Flaeche> teile;
        int teil;
        int y;
        int ticketX = Integer.MIN_VALUE, ticketZ = Integer.MIN_VALUE;
        Arbeit(CommandSender sender, World welt, PlotEntfernung eintrag, List<PlotZensur.Flaeche> teile) {
            this.sender = sender; this.welt = welt; this.eintrag = eintrag; this.teile = teile; this.y = welt.getMinHeight();
        }
    }
}
