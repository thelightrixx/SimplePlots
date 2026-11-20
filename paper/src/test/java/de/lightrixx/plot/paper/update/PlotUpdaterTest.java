package de.lightrixx.plot.paper.update;

import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotUpdaterTest {
    @TempDir Path ordner;
    private final LinkedBlockingQueue<Runnable> serverthread = new LinkedBlockingQueue<>();
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final PlotKonfiguration config = mock(PlotKonfiguration.class);
    private final PlotNachrichten texte = mock(PlotNachrichten.class);
    private final CommandSender sender = mock(CommandSender.class);
    private final GithubUpdates github = mock(GithubUpdates.class);
    private PlotUpdater updater;
    private Path original;
    private final GithubUpdates.Release release = new GithubUpdates.Release("v0.6.0-alpha", "SimplePlots-v0.6.0-alpha.jar",
            URI.create("https://github.com/test.jar"), URI.create("https://github.com/SHA256SUMS.txt"), 3, "null");

    @BeforeEach
    void vorbereiten() throws IOException {
        original = ordner.resolve("IndividuellerName.jar");
        Files.writeString(original, "laufendes Plugin");
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getSLF4JLogger()).thenReturn(mock(Logger.class));
        var meta = mock(PluginMeta.class);
        when(meta.getVersion()).thenReturn("v0.5.0-alpha");
        when(plugin.getPluginMeta()).thenReturn(meta);
        var scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(aufruf -> {
            serverthread.add(aufruf.getArgument(1)); return null;
        });
        when(server.getUpdateFolder()).thenReturn("update");
        when(server.getUpdateFolderFile()).thenReturn(ordner.resolve("update").toFile());
        when(config.aktiv("updates.aktiviert")).thenReturn(true);
        when(config.text("updates.kanal")).thenReturn("alpha");
        when(sender.hasPermission("simpleplots.admin.update")).thenReturn(true);
        when(github.pruefen("v0.5.0-alpha", true)).thenReturn(Optional.of(release));
        when(github.herunterladen(release)).thenReturn(new byte[]{1, 2, 3});
        updater = new PlotUpdater(plugin, config, texte, original, github);
    }

    @AfterEach void beenden() { updater.beenden(); }

    @Test
    void bereitetErstNachBestaetigungUnterDemInstalliertenDateinamenVor() throws Exception {
        updater.befehl(sender, new String[]{"admin", "update", "check"});
        naechsterTick();
        updater.befehl(sender, new String[]{"admin", "update", "apply"});
        assertFalse(Files.exists(ordner.resolve("update/IndividuellerName.jar")));
        verify(github, never()).herunterladen(any());
        updater.befehl(sender, new String[]{"admin", "update", "apply", "confirm"});
        naechsterTick();
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(ordner.resolve("update/IndividuellerName.jar")));
        assertEquals("laufendes Plugin", Files.readString(original));
        verify(server, never()).shutdown();
    }

    @Test
    void laesstBeiDownloadFehlernLaufendesPluginUndVorbereitetesUpdateUnveraendert() throws Exception {
        Files.createDirectories(ordner.resolve("update"));
        Files.writeString(ordner.resolve("update/IndividuellerName.jar"), "bereits vorbereitet");
        when(github.herunterladen(release)).thenThrow(new IOException("Prüfsumme stimmt nicht"));
        updater.befehl(sender, new String[]{"admin", "update", "check"}); naechsterTick();
        updater.befehl(sender, new String[]{"admin", "update", "apply", "confirm"}); naechsterTick();
        assertEquals("bereits vorbereitet", Files.readString(ordner.resolve("update/IndividuellerName.jar")));
        assertEquals("laufendes Plugin", Files.readString(original));
        verify(texte).senden(sender, "update.fehler");
    }

    @Test
    void beachtetPermissionUndVerweigertDeaktiviertenPaperUpdateOrdner() throws Exception {
        when(sender.hasPermission("simpleplots.admin.update")).thenReturn(false);
        updater.befehl(sender, new String[]{"admin", "update", "check"});
        verifyNoInteractions(github);
        when(sender.hasPermission("simpleplots.admin.update")).thenReturn(true);
        updater.befehl(sender, new String[]{"admin", "update", "check"}); naechsterTick();
        when(server.getUpdateFolder()).thenReturn("");
        updater.befehl(sender, new String[]{"admin", "update", "apply", "confirm"});
        verify(github, never()).herunterladen(any());
        assertFalse(Files.exists(ordner.resolve("update")));
    }

    private void naechsterTick() throws InterruptedException {
        Runnable rueckmeldung = serverthread.poll(5, TimeUnit.SECONDS);
        assertNotNull(rueckmeldung);
        rueckmeldung.run();
    }
}
