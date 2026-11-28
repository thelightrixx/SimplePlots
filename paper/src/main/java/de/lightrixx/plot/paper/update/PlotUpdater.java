package de.lightrixx.plot.paper.update;

import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.storage.BinaerDatei;
import de.lightrixx.plot.storage.SpeicherAuftraege;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;
import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component;

public final class PlotUpdater {
    private final JavaPlugin plugin;
    private final PlotKonfiguration config;
    private final PlotNachrichten texte;
    private final Path original;
    private final GithubUpdates github;
    private final SpeicherAuftraege auftraege = new SpeicherAuftraege(2);
    private final AtomicBoolean laeuft = new AtomicBoolean();
    private Optional<GithubUpdates.Release> verfuegbar = Optional.empty();
    private String zustand = "ungeprueft";
    private String vorbereitet = "–";
    private BukkitTask timer;

    public PlotUpdater(JavaPlugin plugin, PlotKonfiguration config, PlotNachrichten texte, Path original) {
        this(plugin, config, texte, original, new GithubUpdates(plugin.getServer().getMinecraftVersion(), plugin.getPluginMeta().getAPIVersion().startsWith("1.21")));
    }

    PlotUpdater(JavaPlugin plugin, PlotKonfiguration config, PlotNachrichten texte, Path original, GithubUpdates github) {
        this.plugin = plugin;
        this.config = config;
        this.texte = texte;
        this.original = original.toAbsolutePath().normalize();
        this.github = github;
    }

    public void planen(boolean start) {
        if (timer != null) timer.cancel();
        verfuegbar = Optional.empty();
        zustand = vorbereitet.equals("–") ? "ungeprueft" : "vorbereitet";
        if (!config.aktiv("updates.aktiviert")) return;
        long intervall = config.zahl("updates.intervall-minuten") * 1200L;
        long zuerst = start && config.aktiv("updates.beim-start-pruefen") ? 100L : intervall;
        timer = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> pruefen(null), zuerst, intervall);
    }

    public void befehl(CommandSender sender, String[] args) {
        if (!sender.hasPermission("simpleplots.admin.update")) {
            texte.senden(sender, "fehler.kein-recht");
            return;
        }
        String aktion = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "status";
        if (aktion.equals("status") && args.length <= 3) {
            texte.senden(sender, "update.status", component("state", texte.komponente("update.zustand." + zustand)),
                    unparsed("current", plugin.getPluginMeta().getVersion()),
                    unparsed("available", verfuegbar.map(GithubUpdates.Release::version).orElse("–")),
                    unparsed("staged", vorbereitet));
            texte.senden(sender, "update.hilfe");
        } else if (aktion.equals("check") && args.length == 3) {
            pruefen(sender);
        } else if (aktion.equals("apply") && args.length == 4 && args[3].equalsIgnoreCase("confirm")) {
            anwenden(sender);
        } else {
            texte.senden(sender, "update.hilfe");
        }
    }

    private void pruefen(CommandSender sender) {
        if (!bereit(sender)) return;
        String aktuell = plugin.getPluginMeta().getVersion();
        String kanal = config.text("updates.kanal");
        zustand = "prueft";
        auftraege.einreichen(() -> github.pruefen(aktuell, kanal)).whenComplete((release, fehler) -> rueckmeldung(() -> {
            laeuft.set(false);
            if (fehler != null) {
                fehlgeschlagen(sender, fehler);
                return;
            }
            if (!config.aktiv("updates.aktiviert") || !kanal.equals(config.text("updates.kanal"))) {
                zustand = "ungeprueft";
                if (sender != null) texte.senden(sender, "update.zuerst-pruefen");
                return;
            }
            verfuegbar = release;
            zustand = release.isPresent() ? "verfuegbar" : "aktuell";
            if (sender != null) {
                texte.senden(sender, release.isPresent() ? "update.verfuegbar" : "update.aktuell",
                        unparsed("version", release.map(GithubUpdates.Release::version).orElse(aktuell)));
            } else if (release.isPresent()) {
                plugin.getSLF4JLogger().info("SimplePlots {} ist verfügbar. /plot admin update status", release.get().version());
            }
        }));
    }

    private void anwenden(CommandSender sender) {
        if (verfuegbar.isEmpty()) {
            texte.senden(sender, "update.zuerst-pruefen");
            return;
        }
        if (!bereit(sender)) return;
        var release = verfuegbar.orElseThrow();
        Path ziel;
        try {
            String ordner = plugin.getServer().getUpdateFolder();
            if (ordner == null || ordner.isBlank()) throw new IOException("Papers Update-Ordner ist deaktiviert.");
            Path update = plugin.getServer().getUpdateFolderFile().toPath().toAbsolutePath().normalize();
            if (!Files.isRegularFile(original) || !original.getFileName().toString().endsWith(".jar")
                    || update.equals(original.getParent())) {
                throw new IOException("Installierte JAR oder sicherer Update-Ordner konnte nicht bestimmt werden.");
            }
            ziel = update.resolve(original.getFileName());
        } catch (IOException e) {
            laeuft.set(false);
            fehlgeschlagen(sender, e);
            return;
        }
        zustand = "laedt";
        texte.senden(sender, "update.laden", unparsed("version", release.version()));
        auftraege.einreichen(() -> {
            Files.createDirectories(ziel.getParent());
            if (ziel.getParent().toRealPath().equals(original.toRealPath().getParent())
                    || (Files.exists(ziel) && Files.isSameFile(ziel, original))) {
                throw new IOException("Der Update-Ordner darf nicht auf die laufende Plugin-JAR zeigen.");
            }
            byte[] jar = github.herunterladen(release);
            BinaerDatei.speichern(ziel, jar);
            return release.version();
        }).whenComplete((version, fehler) -> rueckmeldung(() -> {
            laeuft.set(false);
            if (fehler != null) {
                fehlgeschlagen(sender, fehler);
            } else {
                vorbereitet = version;
                zustand = "vorbereitet";
                texte.senden(sender, "update.vorbereitet", unparsed("version", version));
                plugin.getSLF4JLogger().info("Geprüftes Update {} in {} vorbereitet. Übernahme durch Paper beim nächsten Start.", version, ziel);
            }
        }));
    }

    private boolean bereit(CommandSender sender) {
        if (!config.aktiv("updates.aktiviert")) {
            if (sender != null) texte.senden(sender, "fehler.deaktiviert");
            return false;
        }
        if (!laeuft.compareAndSet(false, true)) {
            if (sender != null) texte.senden(sender, "update.laeuft");
            return false;
        }
        return true;
    }

    private void fehlgeschlagen(CommandSender sender, Throwable fehler) {
        zustand = "fehlgeschlagen";
        plugin.getSLF4JLogger().warn("SimplePlots-Update fehlgeschlagen.", fehler);
        if (sender != null) texte.senden(sender, "update.fehler");
    }

    private void rueckmeldung(Runnable auftrag) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, auftrag);
    }

    public void beenden() {
        if (timer != null) timer.cancel();
        if (!auftraege.beenden(Duration.ofSeconds(2))) {
            plugin.getSLF4JLogger().warn("Ein Update-Abruf wird beim Beenden noch abgeschlossen.");
        }
    }
}
