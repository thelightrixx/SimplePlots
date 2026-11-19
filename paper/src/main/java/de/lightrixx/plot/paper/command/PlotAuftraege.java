package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.storage.SpeicherAuftraege;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Bukkit-Zugriffe bleiben in Vorbereitung und Rückmeldung auf dem Serverthread. */
public final class PlotAuftraege {
    private final SpeicherAuftraege speicher;
    private final Executor serverthread;
    private final BooleanSupplier aktiv;
    private final Logger log;
    private final PlotNachrichten texte;
    private final Set<UUID> laufendeSpieler = new HashSet<>();
    private final Map<UUID, Runnable> aktualisierungen = new HashMap<>();

    public PlotAuftraege(JavaPlugin plugin, PlotNachrichten texte) {
        this(new SpeicherAuftraege(256), auftrag -> plugin.getServer().getScheduler().runTask(plugin, auftrag),
                plugin::isEnabled, plugin.getSLF4JLogger(), texte);
    }

    PlotAuftraege(SpeicherAuftraege speicher, Executor serverthread, BooleanSupplier aktiv, Logger log,
                  PlotNachrichten texte) {
        this.speicher = speicher;
        this.serverthread = serverthread;
        this.aktiv = aktiv;
        this.log = log;
        this.texte = texte;
    }

    public <T> void ausfuehren(CommandSender sender, String kontext, SpeicherAuftraege.Auftrag<T> auftrag,
                              Consumer<T> rueckmeldung) {
        ausfuehren(sender, kontext, auftrag, rueckmeldung, () -> { });
    }

    public <T> void ausfuehren(CommandSender sender, String kontext, SpeicherAuftraege.Auftrag<T> auftrag,
                              Consumer<T> rueckmeldung, Runnable abgeschlossen) {
        UUID id = sender instanceof Player spieler ? spieler.getUniqueId() : null;
        String name = sender.getName();
        if (id != null && !laufendeSpieler.add(id)) {
            texte.senden(sender, "fehler.auftrag-laeuft");
            abgeschlossen.run();
            return;
        }
        speicher.einreichen(auftrag).whenComplete((wert, fehler) -> {
            if (fehler != null && !(fehler instanceof RejectedExecutionException)) {
                log.error("{} für {} fehlgeschlagen.", kontext, name, fehler);
            }
            if (!aktiv.getAsBoolean()) {
                return;
            }
            try {
                serverthread.execute(() -> {
                    if (id != null) {
                        laufendeSpieler.remove(id);
                    }
                    Runnable aktualisieren = id == null ? null : aktualisierungen.remove(id);
                    try {
                        if (fehler == null) {
                            rueckmeldung.accept(wert);
                        } else {
                            texte.senden(sender, fehler instanceof RejectedExecutionException
                                    ? "fehler.ausgelastet" : "fehler.verarbeitung");
                        }
                        if (aktualisieren != null) {
                            aktualisieren.run();
                        }
                    } catch (RuntimeException e) {
                        log.error("Rückmeldung zu {} für {} fehlgeschlagen.", kontext, name, e);
                        texte.senden(sender, "fehler.verarbeitung");
                    } finally {
                        abgeschlossen.run();
                    }
                });
            } catch (RuntimeException e) {
                if (aktiv.getAsBoolean()) {
                    log.error("Rückmeldung zu {} konnte nicht auf dem Serverthread eingeplant werden.", kontext, e);
                }
            }
        });
    }

    public void nachAbschluss(Player spieler, Runnable aktualisierung) {
        if (laufendeSpieler.contains(spieler.getUniqueId())) {
            aktualisierungen.put(spieler.getUniqueId(), aktualisierung);
        } else {
            aktualisierung.run();
        }
    }

    public SpeicherAuftraege.Status status() {
        return speicher.status();
    }

    public boolean beenden(Duration wartezeit) {
        return speicher.beenden(wartezeit);
    }
}
