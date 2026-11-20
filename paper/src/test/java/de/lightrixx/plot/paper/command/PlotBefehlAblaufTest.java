package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRechte;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.storage.PlotYamlSpeicher;
import de.lightrixx.plot.paper.storage.WettbewerbYamlSpeicher;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import de.lightrixx.plot.storage.SpeicherAuftraege;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotBefehlAblaufTest {
    @TempDir Path ordner;
    private final UUID besitzerId = UUID.randomUUID();
    private final UUID gastId = UUID.randomUUID();
    private final PlotId id = new PlotId(0, 0);
    private final World plotwelt = mock(World.class);
    private final Player besitzer = mock(Player.class);
    private final Player gast = mock(Player.class);
    private final CommandSender konsole = mock(CommandSender.class);
    private final PlotWelt welt = mock(PlotWelt.class);
    private final PlotNachrichten texte = mock(PlotNachrichten.class);
    private final PlotMenue menue = mock(PlotMenue.class);
    private final AtomicReference<Location> standort = new AtomicReference<>();
    private PlotDienst plots;
    private WettbewerbDienst wettbewerbe;
    private PlotBefehl befehl;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void vorbereiten() throws Exception {
        Path config = ordner.resolve("config.yml");
        Files.writeString(config, "raster:\n  plotgroesse: 32\n  wegbreite: 7\nclaims:\n  pro-spieler: 3\n");
        var konfiguration = new PlotKonfiguration(config, material -> true);
        plots = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        wettbewerbe = new WettbewerbDienst(new WettbewerbYamlSpeicher(ordner.resolve("wettbewerb.yml")), plots);
        var server = mock(Server.class);
        when(besitzer.getServer()).thenReturn(server);
        when(gast.getServer()).thenReturn(server);
        when(konsole.getServer()).thenReturn(server);
        when(besitzer.canSee(gast)).thenReturn(true);
        when(server.getPlayer(besitzerId)).thenReturn(besitzer);
        when(server.getPlayer(gastId)).thenReturn(gast);
        when(server.getPlayerExact("Gast")).thenReturn(gast);
        when(server.getOfflinePlayer(any(UUID.class))).thenAnswer(aufruf -> {
            var offline = mock(OfflinePlayer.class);
            when(offline.getName()).thenReturn(aufruf.getArgument(0).equals(besitzerId) ? "Besitzer" : "Gast");
            return offline;
        });
        standort.set(new Location(plotwelt, 5, 65, 5));
        when(besitzer.getLocation()).thenAnswer(aufruf -> standort.get());
        when(besitzer.getWorld()).thenAnswer(aufruf -> standort.get().getWorld());
        when(besitzer.getUniqueId()).thenReturn(besitzerId);
        when(besitzer.getName()).thenReturn("Besitzer");
        when(besitzer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(besitzer.isOnline()).thenReturn(true);
        when(besitzer.hasPermission(anyString())).thenAnswer(aufruf -> Set.of(PlotBerechtigungen.USE,
                PlotBerechtigungen.CLAIM, PlotBerechtigungen.MANAGE).contains(aufruf.getArgument(0)));
        when(besitzer.teleport(any(Location.class))).thenAnswer(aufruf -> {
            standort.set(aufruf.getArgument(0));
            return true;
        });
        when(gast.getUniqueId()).thenReturn(gastId);
        when(gast.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(gast.getName()).thenReturn("Gast");
        when(gast.getWorld()).thenReturn(plotwelt);
        when(gast.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        when(gast.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(gast.hasPermission(PlotBerechtigungen.USE)).thenReturn(true);
        when(gast.teleport(any(Location.class))).thenReturn(true);
        when(konsole.hasPermission(anyString())).thenReturn(true);
        when(konsole.getName()).thenReturn("Konsole");
        when(welt.istPlotWelt(any())).thenAnswer(aufruf -> aufruf.getArgument(0) == plotwelt);
        when(welt.plotBei(any())).thenAnswer(aufruf -> {
            Location ort = aufruf.getArgument(0);
            return ort != null && ort.getWorld() == plotwelt
                    ? plots.plotBei(konfiguration.raster(), ort.getBlockX(), ort.getBlockZ()) : Optional.empty();
        });
        when(welt.konfiguration()).thenReturn(konfiguration);
        when(welt.raster()).thenAnswer(aufruf -> konfiguration.raster());
        when(welt.ladenOderErstellen()).thenReturn(plotwelt);
        when(welt.besuchspunkt(any(), any(), any())).thenAnswer(aufruf -> Optional.of(new Location(plotwelt, 5, 65, 5)));
        when(welt.wegVor(any(), any())).thenReturn(new Location(plotwelt, 15, 65, -1));
        when(welt.mitte(any(), any())).thenAnswer(aufruf -> {
            PlotId ziel = aufruf.getArgument(1);
            return new Location(plotwelt, konfiguration.raster().mitteX(ziel), 65, konfiguration.raster().mitteZ(ziel));
        });
        var auftraege = mock(PlotAuftraege.class);
        // Die Thread-Grenze wird separat geprüft; hier laufen echte Dienste und YAML-Speicher.
        doAnswer(aufruf -> {
            var auftrag = (SpeicherAuftraege.Auftrag<Object>) aufruf.getArgument(2);
            var antwort = (Consumer<Object>) aufruf.getArgument(3);
            antwort.accept(auftrag.ausfuehren());
            return null;
        }).when(auftraege).ausfuehren(any(), anyString(), any(), any());
        var log = mock(Logger.class);
        doAnswer(aufruf -> {
            throw new AssertionError("Unerwarteter Speicherfehler im Befehlsablauf", (Throwable) aufruf.getArgument(3));
        }).when(log).error(anyString(), any(), any(), any());
        befehl = new PlotBefehl(server, log, plots, welt, konfiguration, texte, menue,
                wettbewerbe, auftraege, mock(PlotBetrieb.class));
        befehl.funktionen(mock(de.lightrixx.plot.paper.listener.PlotFlug.class),
                mock(de.lightrixx.plot.paper.update.PlotUpdater.class), mock(de.lightrixx.plot.paper.world.PlotZensur.class));
    }

    @Test
    void beanspruchtUndBesuchtPlotsUeberBefehleUndBehaeltSieNachNeustart() throws IOException {
        ausfuehren(besitzer, "claim");
        assertEquals(besitzerId, plots.finden(id).orElseThrow().besitzer());
        ausfuehren(besitzer, "list");
        ausfuehren(besitzer, "info");
        ausfuehren(besitzer, "auto");
        assertEquals(2, plots.anzahlPlots());
        ausfuehren(besitzer, "home", "1");
        ausfuehren(besitzer, "visit", "0;0");
        verify(besitzer, atLeast(3)).teleport(any(Location.class));
        var neustart = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        assertEquals(plots.alle(), neustart.alle());
    }

    @Test
    void verwaltetRollenUndEntferntEinenGebanntenAnwesendenGast() {
        ausfuehren(besitzer, "claim");
        ausfuehren(besitzer, "trust", "Gast");
        assertTrue(plots.finden(id).orElseThrow().darfBauen(gastId));
        ausfuehren(besitzer, "untrust", gastId.toString());
        ausfuehren(besitzer, "add", "Gast");
        assertTrue(plots.finden(id).orElseThrow().rechte().bauhelfer().contains(gastId));
        ausfuehren(besitzer, "storage", "Gast");
        assertTrue(plots.finden(id).orElseThrow().rechte().lager().contains(gastId));
        ausfuehren(besitzer, "deny", "Gast");
        assertFalse(plots.finden(id).orElseThrow().darfBetreten(gastId));
        assertTrue(plots.finden(id).orElseThrow().rechte().lager().isEmpty());
        verify(gast).teleport(any(Location.class));
        ausfuehren(besitzer, "unban", "Gast");
        assertTrue(plots.finden(id).orElseThrow().darfBetreten(gastId));
        ausfuehren(besitzer, "trust", besitzerId.toString());
        verify(texte).senden(besitzer, "rollen.selbst");
    }

    @Test
    void veroeffentlichtProfileVergibtLikesUndSpeichertFlagsUndBesuchspunkt() {
        ausfuehren(besitzer, "claim");
        ausfuehren(besitzer, "profile", "name", "Großer", "Hafen");
        ausfuehren(besitzer, "profile", "description", "Mit", "Lager");
        ausfuehren(besitzer, "profile", "category", "shop");
        ausfuehren(besitzer, "flag", "doors", "on");
        ausfuehren(besitzer, "sethome");
        ausfuehren(gast, "like");
        var plot = plots.finden(id).orElseThrow();
        assertEquals("Großer Hafen", plot.profil().name());
        assertEquals("Mit Lager", plot.profil().beschreibung());
        assertEquals(Optional.of(PlotProfil.Kategorie.SHOP), plot.profil().kategorie());
        assertEquals(Set.of(gastId), plot.profil().likes());
        assertTrue(plot.profil().besuchspunkt().isPresent());
        assertTrue(plot.rechte().besucher().contains(PlotRechte.BesucherRecht.DOORS));
        ausfuehren(gast, "unlike");
        ausfuehren(besitzer, "sethome", "off");
        assertTrue(plots.finden(id).orElseThrow().profil().likes().isEmpty());
        assertTrue(plots.finden(id).orElseThrow().profil().besuchspunkt().isEmpty());
    }

    @Test
    void verbindetEigeneNachbarnErstMitBestaetigungUndAktualisiertDieUrsprungswelt() throws IOException {
        ausfuehren(besitzer, "claim");
        plots.claim(new PlotId(1, 0), besitzerId, 3);
        ausfuehren(besitzer, "merge", "1;0");
        assertEquals(2, plots.anzahlPlots());
        ausfuehren(besitzer, "merge", "1;0", "confirm");
        assertEquals(1, plots.anzahlPlots());
        assertEquals(2, plots.anzahlZellen());
        verify(welt).wegrandAktualisieren(eq(plotwelt), eq(plots.finden(id).orElseThrow()));
    }

    @Test
    void durchlaeuftWettbewerbMitAbstimmungUndErgebnissen() {
        ausfuehren(besitzer, "claim");
        ausfuehren(konsole, "contest", "start", "Baut", "einen", "Hafen");
        ausfuehren(besitzer, "submit");
        assertTrue(wettbewerbe.gesperrt(id));
        ausfuehren(konsole, "contest", "voting");
        ausfuehren(besitzer, "vote");
        verify(texte).senden(besitzer, "wettbewerb.eigener-plot");
        ausfuehren(gast, "vote");
        assertEquals(id, wettbewerbe.aktuell().orElseThrow().stimmen().get(gastId));
        ausfuehren(konsole, "contest", "finish");
        assertFalse(wettbewerbe.gesperrt(id));
        ausfuehren(konsole, "contest", "results");
        ausfuehren(besitzer, "contest", "entries");
        verify(menue).wettbewerb(besitzer);
    }

    @Test
    void lehntUngueltigeProfileUndUnsichereBesuchspunkteOhneAenderungAb() {
        ausfuehren(besitzer, "claim");
        ausfuehren(besitzer, "profile", "name", "A".repeat(33));
        verify(texte).senden(besitzer, "profil.ungueltig");
        when(welt.besuchspunkt(any(), any(), any())).thenReturn(Optional.empty());
        ausfuehren(besitzer, "sethome");
        verify(texte).senden(besitzer, "profil.ort-unsicher");
        assertEquals(PlotProfil.leer(), plots.finden(id).orElseThrow().profil());
    }

    @Test
    void verweigertBesucheNachPlotbannUndErlaubtAdministrativenZutritt() {
        ausfuehren(besitzer, "claim");
        ausfuehren(besitzer, "deny", "Gast");
        clearInvocations(gast);
        ausfuehren(gast, "visit", "0;0");
        verify(texte, atLeastOnce()).senden(gast, "zugang.gebannt");
        verify(gast, never()).teleport(any(Location.class));
        when(gast.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        when(gast.hasPermission("simpleplots.admin.bypass")).thenReturn(true);
        ausfuehren(gast, "visit", "0;0");
        verify(gast).teleport(any(Location.class));
    }

    @Test
    void uebertraegtErstNachEinemPassendenAngebotUndSetztRollenUndLikesZurueck() {
        when(gast.hasPermission(anyString())).thenAnswer(aufruf -> Set.of(PlotBerechtigungen.USE,
                PlotBerechtigungen.CLAIM, PlotBerechtigungen.MANAGE).contains(aufruf.getArgument(0)));
        ausfuehren(besitzer, "claim");
        ausfuehren(besitzer, "trust", "Gast");
        ausfuehren(besitzer, "profile", "name", "Hafen");
        ausfuehren(besitzer, "profile", "category", "build");
        ausfuehren(gast, "like");
        ausfuehren(gast, "accept", "0;0");
        assertEquals(besitzerId, plots.finden(id).orElseThrow().besitzer());
        ausfuehren(besitzer, "transfer", "Gast");
        assertEquals(besitzerId, plots.finden(id).orElseThrow().besitzer());
        assertTrue(befehl.onTabComplete(gast, null, "plot", new String[]{"accept", ""}).contains("0;0"));
        ausfuehren(gast, "accept", "0;0");
        var plot = plots.finden(id).orElseThrow();
        assertEquals(gastId, plot.besitzer());
        assertTrue(plot.rechte().trusted().isEmpty());
        assertTrue(plot.profil().likes().isEmpty());
        assertEquals("Hafen", plot.profil().name());
        verify(menue).schliessen();
    }

    @Test
    void erlaubtAdminBesitzerwechselUndVerhindertFreigabeZensierterPlots() throws IOException {
        ausfuehren(besitzer, "claim");
        ausfuehren(konsole, "admin", "setowner", "0;0", gastId.toString());
        assertEquals(besitzerId, plots.finden(id).orElseThrow().besitzer());
        ausfuehren(konsole, "admin", "setowner", "0;0", gastId.toString(), "confirm");
        assertEquals(gastId, plots.finden(id).orElseThrow().besitzer());
        plots.zensieren(id, true);
        ausfuehren(konsole, "admin", "unclaim", "0;0", "confirm");
        assertTrue(plots.finden(id).isPresent());
        plots.zensieren(id, false);
        ausfuehren(konsole, "admin", "unclaim", "0;0", "confirm");
        assertTrue(plots.finden(id).isEmpty());
    }

    @Test
    void zaehltZensierteWettbewerbsbeitraegeNichtInDerOeffentlichenAnzeige() throws IOException {
        when(texte.komponente(anyString())).thenReturn(net.kyori.adventure.text.Component.empty());
        var anzeige = new AtomicReference<String>();
        doAnswer(aufruf -> {
            var resolver = java.util.Arrays.copyOfRange(aufruf.getArguments(), 2, aufruf.getArguments().length,
                    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class);
            anzeige.set(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<count>", resolver)));
            return null;
        }).when(texte).senden(eq(besitzer), eq("wettbewerb.status"), any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class));
        ausfuehren(besitzer, "claim");
        ausfuehren(konsole, "contest", "start", "Hafen");
        ausfuehren(besitzer, "submit");
        plots.zensieren(id, true);
        ausfuehren(besitzer, "contest", "status");
        assertEquals("0", anzeige.get());
    }

    private void ausfuehren(CommandSender sender, String... args) {
        assertTrue(befehl.onCommand(sender, null, "plot", args));
    }
}
