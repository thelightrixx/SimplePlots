package de.lightrixx.plot.paper.world;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.command.PlotAuftraege;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.storage.ZensurSpeicher;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.storage.SpeicherAuftraege;
import io.papermc.paper.entity.EntitySerializationFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

@SuppressWarnings("deprecation")
public final class PlotZensur implements Listener {
    private final JavaPlugin plugin;
    private final PlotWelt welt;
    private final PlotDienst plots;
    private final PlotAuftraege auftraege;
    private final PlotNachrichten texte;
    private final Runnable ansichtenAktualisieren;
    private final Map<PlotId, Arbeit> arbeiten = new LinkedHashMap<>();
    private final Map<UUID, Entity> verborgen = new HashMap<>();
    private boolean eigeneEntities;

    public PlotZensur(JavaPlugin plugin, PlotWelt welt, PlotDienst plots, PlotAuftraege auftraege, PlotNachrichten texte,
                      Runnable ansichtenAktualisieren) {
        this.plugin = plugin;
        this.welt = welt;
        this.plots = plots;
        this.auftraege = auftraege;
        this.texte = texte;
        this.ansichtenAktualisieren = ansichtenAktualisieren;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::entitiesAusblenden, 1, 5);
    }

    public void fortsetzen() throws IOException {
        if (plots.alle().stream().noneMatch(Plot::zensiert)) return;
        World plotwelt = welt.ladenOderErstellen();
        for (Plot plot : plots.alle()) {
            if (plot.zensiert()) beginnen(plugin.getServer().getConsoleSender(), plotwelt, plot, false, false, true);
        }
    }

    public boolean beschaeftigt(PlotId id) {
        return arbeiten.containsKey(plots.finden(id).map(Plot::id).orElse(id));
    }

    public void befehl(CommandSender sender, PlotId id, boolean zensieren) throws IOException {
        Plot plot = plots.finden(id).orElse(null);
        if (plot == null) {
            texte.senden(sender, "fehler.kein-plot");
            return;
        }
        if (arbeiten.containsKey(plot.id())) {
            texte.senden(sender, "zensur.laeuft");
            return;
        }
        if (!zensieren && !plot.zensiert()) {
            texte.senden(sender, "rollen.unveraendert");
            return;
        }
        beginnen(sender, welt.ladenOderErstellen(), plot, !zensieren, zensieren && !plot.zensiert(), false);
    }

    private void beginnen(CommandSender sender, World plotwelt, Plot plot, boolean wiederherstellen, boolean neu, boolean automatisch) {
        int maximum = welt.konfiguration().zahl("protection.zensur-maximale-chunks");
        if ((long) plot.zellen().size() * welt.raster().groesse() * welt.raster().groesse() > maximum * 256L) {
            texte.senden(sender, "zensur.zu-gross");
            return;
        }
        List<Flaeche> flaechen = flaechen(plot, welt.raster());
        Set<Long> chunks = new java.util.LinkedHashSet<>();
        for (Flaeche teil : flaechen) chunks.add(((long) (teil.x() >> 4) << 32) | ((teil.z() >> 4) & 0xffffffffL));
        if (chunks.size() > maximum || (!automatisch && arbeiten.size() >= 8)) {
            texte.senden(sender, "zensur.zu-gross");
            return;
        }
        var speicher = new ZensurSpeicher(welt.konfiguration().datenordner().resolve("zensur"), plot, welt.raster(),
                plotwelt.getUID().toString(), plotwelt.getMinHeight(), plotwelt.getMaxHeight());
        var arbeit = new Arbeit(sender, plotwelt, plot, speicher, flaechen, List.copyOf(chunks));
        arbeiten.put(plot.id(), arbeit);
        io(arbeit, () -> {
            if (!plots.finden(plot.id()).filter(plot::equals).isPresent()) {
                throw new IOException("Der Plot wurde vor Beginn der Zensurarbeiten geändert. Befehl erneut ausführen.");
            }
            boolean modus = speicher.oeffnen(wiederherstellen, neu);
            if (!automatisch && !wiederherstellen && modus) {
                speicher.phase("ZENSIERT");
                modus = false;
            }
            if (neu) plots.zensieren(plot.id(), true);
            return new Beginn(modus, automatisch && !modus && speicher.abgeschlossen());
        }, beginn -> {
            arbeit.wiederherstellen = beginn.wiederherstellen();
            if (beginn.unveraendert()) {
                beenden(arbeit);
                entitiesAusblenden();
                return;
            }
            ansichtenAktualisieren.run();
            entitiesAusblenden();
            for (Player spieler : plotwelt.getPlayers()) {
                if (welt.plotBei(spieler.getLocation()).filter(plot.id()::equals).isPresent()
                        && !spieler.teleport(welt.wegVor(plotwelt, plot.id()))) {
                    throw new IllegalStateException("Spieler konnte vor Zensurarbeiten nicht sicher versetzt werden.");
                }
            }
            texte.senden(sender, "zensur.start", unparsed("id", plot.id().toString()));
            spaeter(arbeit, () -> chunkLaden(arbeit));
        });
    }

    private void chunkLaden(Arbeit arbeit) {
        if (arbeiten.values().iterator().next() != arbeit) {
            spaeter(arbeit, () -> chunkLaden(arbeit));
            return;
        }
        if (arbeit.chunk < arbeit.chunks.size()) {
            long key = arbeit.chunks.get(arbeit.chunk++);
            int x = (int) (key >> 32), z = (int) key;
            arbeit.welt.addPluginChunkTicket(x, z, plugin);
            arbeit.geladen.add(key);
            spaeter(arbeit, () -> chunkLaden(arbeit));
        } else {
            io(arbeit, arbeit.speicher::entities, geladen -> {
                if (geladen.isPresent()) {
                    arbeit.entities = geladen.get();
                    entitiesEntfernen(arbeit);
                    spaeter(arbeit, () -> teilBearbeiten(arbeit));
                } else {
                    var entities = new LinkedHashMap<UUID, byte[]>();
                    Set<UUID> enthalten = new HashSet<>();
                    for (Entity entity : arbeit.welt.getEntities()) {
                        if (!(entity instanceof Player) && gehoertZu(arbeit, entity.getLocation())) enthalten.add(entity.getUniqueId());
                    }
                    for (UUID id : enthalten) {
                        Entity entity = plugin.getServer().getEntity(id);
                        if (entity == null) continue;
                        if (entity.getVehicle() != null && enthalten.contains(entity.getVehicle().getUniqueId())) continue;
                        if (baum(entity).stream().anyMatch(e -> e instanceof Player || !enthalten.contains(e.getUniqueId()))) {
                            throw new IllegalStateException("Ein Fahrzeug mit Passagieren überquert die Plotgrenze.");
                        }
                        entities.put(id, plugin.getServer().getUnsafe().serializeEntity(entity,
                                EntitySerializationFlag.FORCE, EntitySerializationFlag.MISC, EntitySerializationFlag.PASSENGERS));
                    }
                    arbeit.entities = Map.copyOf(entities);
                    io(arbeit, () -> { arbeit.speicher.entitiesSichern(entities); return true; }, wert -> {
                        entitiesEntfernen(arbeit);
                        spaeter(arbeit, () -> teilBearbeiten(arbeit));
                    });
                }
            });
        }
    }

    private void entitiesEntfernen(Arbeit arbeit) {
        for (byte[] daten : arbeit.entities.values()) {
            Entity vorlage = plugin.getServer().getUnsafe().deserializeEntity(daten, arbeit.welt, true, true);
            for (Entity entity : baum(vorlage)) {
                Entity vorhanden = plugin.getServer().getEntity(entity.getUniqueId());
                if (vorhanden != null && !(vorhanden instanceof Player)) vorhanden.remove();
            }
        }
    }

    private void teilBearbeiten(Arbeit arbeit) {
        if (arbeit.flaeche >= arbeit.flaechen.size()) {
            if (arbeit.wiederherstellen && !arbeit.details) {
                arbeit.details = true;
                arbeit.flaeche = 0;
                arbeit.schicht = 0;
                spaeter(arbeit, () -> teilBearbeiten(arbeit));
                return;
            }
            abschliessen(arbeit);
            return;
        }
        var abschnitte = new ArrayList<Abschnitt>();
        while (abschnitte.size() < 32 && arbeit.flaeche < arbeit.flaechen.size()) {
            abschnitte.add(new Abschnitt(arbeit.flaeche + "-" + arbeit.schicht, arbeit.flaechen.get(arbeit.flaeche),
                    arbeit.welt.getMinHeight() + arbeit.schicht * 16));
            naechsterTeil(arbeit);
        }
        io(arbeit, () -> arbeit.speicher.teile(abschnitte.stream().map(Abschnitt::name).toList()), daten -> {
            if (arbeit.wiederherstellen && daten.size() != abschnitte.size()) {
                throw new IllegalStateException("Zensursicherung enthält nicht alle Abschnitte.");
            }
            if (daten.size() == abschnitte.size()) anwenden(arbeit, abschnitte, daten, 0);
            else erfassen(arbeit, abschnitte, new LinkedHashMap<>(daten), new LinkedHashMap<>(), 0);
        });
    }

    private void erfassen(Arbeit arbeit, List<Abschnitt> abschnitte, Map<String, byte[]> daten, Map<String, byte[]> neu, int start) {
        long ende = System.nanoTime() + welt.konfiguration().zahl("protection.zensur-zeitbudget-millis") * 1_000_000L;
        int index = start;
        int maximum = welt.konfiguration().zahl("protection.zensur-abschnitte-pro-tick");
        do {
            Abschnitt teil = abschnitte.get(index++);
            if (!daten.containsKey(teil.name())) {
                var struktur = plugin.getServer().getStructureManager().createStructure();
                struktur.fill(teil.ort(arbeit.welt), teil.groesse(arbeit.welt), false);
                try (var puffer = new ByteArrayOutputStream()) {
                    plugin.getServer().getStructureManager().saveStructure(puffer, struktur);
                    neu.put(teil.name(), puffer.toByteArray());
                } catch (IOException e) {
                    throw new IllegalStateException("Plotabschnitt konnte nicht gesichert werden.", e);
                }
            }
        } while (index < abschnitte.size() && index - start < maximum && System.nanoTime() < ende);
        int weiter = index;
        if (weiter < abschnitte.size()) {
            spaeter(arbeit, () -> erfassen(arbeit, abschnitte, daten, neu, weiter));
        } else if (!neu.isEmpty()) {
            io(arbeit, () -> { arbeit.speicher.teileSichern(neu); return true; }, wert -> {
                daten.putAll(neu);
                anwenden(arbeit, abschnitte, daten, 0);
            });
        } else anwenden(arbeit, abschnitte, daten, 0);
    }

    private void anwenden(Arbeit arbeit, List<Abschnitt> abschnitte, Map<String, byte[]> daten, int start) {
        long ende = System.nanoTime() + welt.konfiguration().zahl("protection.zensur-zeitbudget-millis") * 1_000_000L;
        int index = start;
        int maximum = welt.konfiguration().zahl("protection.zensur-abschnitte-pro-tick");
        do {
            Abschnitt teil = abschnitte.get(index++);
            if (arbeit.wiederherstellen) {
                try (var puffer = new ByteArrayInputStream(daten.get(teil.name()))) {
                    var struktur = plugin.getServer().getStructureManager().loadStructure(puffer);
                    if (!struktur.getSize().equals(teil.groesse(arbeit.welt))
                            || struktur.getPaletteCount() != 1) {
                        throw new IOException("Sicherungsteil hat eine unerwartete Größe: " + teil.name());
                    }
                    if (arbeit.details) {
                        struktur.place(teil.ort(arbeit.welt), false, StructureRotation.NONE, Mirror.NONE, 0, 1, new Random(0));
                    } else {
                        // Mehrteilige Blöcke brauchen ihre Nachbarn, bevor Paper NBT und Formen aktualisiert.
                        for (var block : struktur.getPalettes().getFirst().getBlocks()) {
                            arbeit.welt.getBlockAt(teil.flaeche().x() + block.getX(), teil.y() + block.getY(), teil.flaeche().z() + block.getZ())
                                    .setBlockData(block.getBlockData(), false);
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Plotabschnitt konnte nicht wiederhergestellt werden.", e);
                }
            } else ausblenden(arbeit, teil.flaeche(), teil.y());
        } while (index < abschnitte.size() && index - start < maximum && System.nanoTime() < ende);
        int weiter = index;
        if (weiter < abschnitte.size()) spaeter(arbeit, () -> anwenden(arbeit, abschnitte, daten, weiter));
        else spaeter(arbeit, () -> teilBearbeiten(arbeit));
    }

    private void ausblenden(Arbeit arbeit, Flaeche flaeche, int y) {
        var config = welt.konfiguration();
        int erdeAb = PlotGenerator.BODEN_Y - config.zahl("world.erdschichten");
        Material untergrund = config.material("world.material.untergrund");
        Material erde = config.material("world.material.erde");
        Material oberflaeche = config.material("world.material.oberflaeche");
        Material randMaterial = config.material("protection.zensur-rand");
        for (int x = flaeche.x(); x < flaeche.x() + flaeche.breite(); x++) {
            for (int z = flaeche.z(); z < flaeche.z() + flaeche.tiefe(); z++) {
                boolean rand = false;
                if (y <= 64 && y + 16 > 64) {
                    for (int[] richtung : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                        if (!welt.plotBei(new Location(arbeit.welt, x + richtung[0], y, z + richtung[1]))
                                .filter(arbeit.plot.id()::equals).isPresent()) rand = true;
                    }
                }
                for (int hoehe = y; hoehe < Math.min(y + 16, arbeit.welt.getMaxHeight()); hoehe++) {
                    Material material = hoehe == arbeit.welt.getMinHeight() ? Material.BEDROCK
                            : hoehe < erdeAb ? untergrund : hoehe < PlotGenerator.BODEN_Y ? erde
                            : hoehe == PlotGenerator.BODEN_Y ? oberflaeche
                            : hoehe == PlotGenerator.BODEN_Y + 1 && rand ? randMaterial : Material.AIR;
                    var block = arbeit.welt.getBlockAt(x, hoehe, z);
                    if (block.getType() != material) block.setType(material, false);
                }
            }
        }
    }

    private void naechsterTeil(Arbeit arbeit) {
        arbeit.schicht++;
        if (arbeit.welt.getMinHeight() + arbeit.schicht * 16 >= arbeit.welt.getMaxHeight()) {
            arbeit.schicht = 0;
            arbeit.flaeche++;
        }
    }

    private void abschliessen(Arbeit arbeit) {
        if (arbeit.wiederherstellen) {
            eigeneEntities = true;
            try {
                for (byte[] daten : arbeit.entities.values()) {
                    Entity entity = plugin.getServer().getUnsafe().deserializeEntity(daten, arbeit.welt, true, true);
                    for (Entity teil : baum(entity)) {
                        if (!teil.isValid() && !teil.spawnAt(teil.getLocation())) {
                            throw new IllegalStateException("Gesichertes Entity konnte nicht wiederhergestellt werden: " + teil.getType());
                        }
                    }
                }
            } finally {
                eigeneEntities = false;
            }
            entitiesAusblenden();
        }
        arbeit.welt.save(true);
        io(arbeit, () -> {
            if (arbeit.wiederherstellen) {
                arbeit.speicher.phase("WIEDERHERGESTELLT");
                plots.zensieren(arbeit.plot.id(), false);
            } else arbeit.speicher.vollstaendig();
            return true;
        }, wert -> {
            beenden(arbeit);
            welt.wegrandAktualisieren(arbeit.welt, arbeit.plot);
            entitiesAusblenden();
            ansichtenAktualisieren.run();
            texte.senden(arbeit.sender, arbeit.wiederherstellen ? "zensur.wiederhergestellt" : "zensur.fertig",
                    unparsed("id", arbeit.plot.id().toString()));
        });
    }

    private <T> void io(Arbeit arbeit, SpeicherAuftraege.Auftrag<T> auftrag, Consumer<T> danach) {
        boolean[] erfolgreich = {false};
        auftraege.ausfuehren(plugin.getServer().getConsoleSender(), "Zensur " + arbeit.plot.id(), auftrag, wert -> {
            if (arbeiten.get(arbeit.plot.id()) != arbeit) {
                erfolgreich[0] = true;
                return;
            }
            danach.accept(wert);
            erfolgreich[0] = true;
        }, () -> {
            if (!erfolgreich[0]) {
                beenden(arbeit);
                texte.senden(arbeit.sender, "zensur.fehler");
            }
        });
    }

    private void spaeter(Arbeit arbeit, Runnable schritt) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (arbeiten.get(arbeit.plot.id()) != arbeit) return;
            try {
                schritt.run();
            } catch (RuntimeException e) {
                plugin.getSLF4JLogger().error("Zensurarbeiten für {} unterbrochen. Sicherung bleibt erhalten.", arbeit.plot.id(), e);
                beenden(arbeit);
                texte.senden(arbeit.sender, "zensur.fehler");
            }
        });
    }

    private void beenden(Arbeit arbeit) {
        arbeiten.remove(arbeit.plot.id());
        for (long key : arbeit.geladen) arbeit.welt.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
    }

    private boolean gehoertZu(Arbeit arbeit, Location ort) { return welt.plotBei(ort).filter(arbeit.plot.id()::equals).isPresent(); }
    private boolean zensiert(Location ort) { return welt.plotBei(ort).flatMap(plots::finden).filter(Plot::zensiert).isPresent(); }

    private void entitiesAusblenden() {
        if (!plots.hatZensiertePlots() && verborgen.isEmpty()) return;
        if (!welt.istGeladen()) return;
        World plotwelt = plugin.getServer().getWorld(welt.schluessel());
        if (plotwelt == null) return;
        for (Entity entity : plotwelt.getEntities()) {
            if (!(entity instanceof Player) && zensiert(entity.getLocation())) {
                verborgen.put(entity.getUniqueId(), entity);
                for (Player spieler : plugin.getServer().getOnlinePlayers()) spieler.hideEntity(plugin, entity);
            }
        }
        verborgen.values().removeIf(entity -> {
            if (!entity.isValid() || !zensiert(entity.getLocation())) {
                for (Player spieler : plugin.getServer().getOnlinePlayers()) spieler.showEntity(plugin, entity);
                return true;
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityErzeugen(EntitySpawnEvent event) {
        if (!eigeneEntities && zensiert(event.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockPhysik(org.bukkit.event.block.BlockPhysicsEvent event) {
        if (zensiert(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityAnzeigen(io.papermc.paper.event.player.PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player) && zensiert(event.getEntity().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityBewegen(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if (!(event.getEntity() instanceof Player) && (zensiert(event.getFrom()) || zensiert(event.getTo()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void fahrzeugBewegen(org.bukkit.event.vehicle.VehicleMoveEvent event) {
        if (zensiert(event.getTo())) event.getVehicle().teleport(event.getFrom());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fallenlassen(PlayerDropItemEvent event) {
        if (zensiert(event.getItemDrop().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bewegen(PlayerMoveEvent event) {
        if (event.getTo() == null || !event.hasChangedBlock()) return;
        welt.plotBei(event.getTo()).filter(arbeiten::containsKey).ifPresent(id -> {
            event.setTo(welt.wegVor(event.getTo().getWorld(), id));
        });
    }

    static List<Flaeche> flaechen(Plot plot, PlotRaster raster) {
        return flaechen(plot.zellen(), raster);
    }

    static List<Flaeche> flaechen(Set<PlotId> zellen, PlotRaster raster) {
        var flaechen = new ArrayList<Flaeche>();
        for (PlotId zelle : zellen.stream().sorted(java.util.Comparator.comparingInt(PlotId::x).thenComparingInt(PlotId::z)).toList()) {
            int x = Math.toIntExact((long) zelle.x() * raster.rasterweite());
            int z = Math.toIntExact((long) zelle.z() * raster.rasterweite());
            aufteilen(flaechen, x, z, raster.groesse(), raster.groesse());
            boolean ost = zellen.contains(zelle.verschoben(1, 0));
            boolean sued = zellen.contains(zelle.verschoben(0, 1));
            if (ost) aufteilen(flaechen, x + raster.groesse(), z, raster.wegbreite(), raster.groesse());
            if (sued) aufteilen(flaechen, x, z + raster.groesse(), raster.groesse(), raster.wegbreite());
            if (ost && sued && zellen.contains(zelle.verschoben(1, 1))) {
                aufteilen(flaechen, x + raster.groesse(), z + raster.groesse(), raster.wegbreite(), raster.wegbreite());
            }
        }
        return List.copyOf(flaechen);
    }

    private static void aufteilen(List<Flaeche> teile, int x, int z, int breite, int tiefe) {
        for (int dx = 0; dx < breite; ) {
            int bx = Math.min(16 - ((x + dx) & 15), breite - dx);
            for (int dz = 0; dz < tiefe; ) {
                int bz = Math.min(16 - ((z + dz) & 15), tiefe - dz);
                teile.add(new Flaeche(x + dx, z + dz, bx, bz));
                dz += bz;
            }
            dx += bx;
        }
    }

    private static List<Entity> baum(Entity entity) {
        var liste = new ArrayList<Entity>();
        liste.add(entity);
        for (Entity passagier : entity.getPassengers()) liste.addAll(baum(passagier));
        return liste;
    }

    record Flaeche(int x, int z, int breite, int tiefe) { }

    private record Beginn(boolean wiederherstellen, boolean unveraendert) { }

    private record Abschnitt(String name, Flaeche flaeche, int y) {
        Location ort(World welt) { return new Location(welt, flaeche.x(), y, flaeche.z()); }
        BlockVector groesse(World welt) { return new BlockVector(flaeche.breite(), Math.min(16, welt.getMaxHeight() - y), flaeche.tiefe()); }
    }

    private static final class Arbeit {
        final CommandSender sender;
        final World welt;
        final Plot plot;
        final ZensurSpeicher speicher;
        final List<Flaeche> flaechen;
        final List<Long> chunks;
        final Set<Long> geladen = new HashSet<>();
        Map<UUID, byte[]> entities = Map.of();
        int chunk;
        int flaeche;
        int schicht;
        boolean wiederherstellen;
        boolean details;
        Arbeit(CommandSender sender, World welt, Plot plot, ZensurSpeicher speicher, List<Flaeche> flaechen, List<Long> chunks) {
            this.sender = sender; this.welt = welt; this.plot = plot; this.speicher = speicher;
            this.flaechen = flaechen; this.chunks = chunks;
        }
    }
}
