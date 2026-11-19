package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.storage.SpeicherAuftraege;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(10)
class PlotAuftraegeTest {
    @Test
    void schreibtAusserhalbDesServerthreadsUndMeldetErstDanachAufDemServerthreadZurueck() throws Exception {
        var speicher = new SpeicherAuftraege(4);
        var serverthread = new LinkedBlockingQueue<Runnable>();
        var texte = mock(PlotNachrichten.class);
        var auftraege = new PlotAuftraege(speicher, serverthread::add, () -> true, mock(Logger.class), texte);
        var sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Konsole");
        Thread haupt = Thread.currentThread();
        var gemeldet = new AtomicBoolean();
        try {
            auftraege.ausfuehren(sender, "Test", () -> {
                assertNotSame(haupt, Thread.currentThread());
                return "gespeichert";
            }, antwort -> {
                assertSame(haupt, Thread.currentThread());
                assertEquals("gespeichert", antwort);
                gemeldet.set(true);
            });
            Runnable rueckmeldung = serverthread.poll(5, TimeUnit.SECONDS);
            assertNotNull(rueckmeldung);
            assertFalse(gemeldet.get());
            rueckmeldung.run();
            assertTrue(gemeldet.get());
        } finally {
            assertTrue(auftraege.beenden(Duration.ofSeconds(5)));
        }
    }

    @Test
    void begrenztSpielerAufEinenAuftragUndGibtSieAuchNachFehlernWiederFrei() throws Exception {
        var speicher = new SpeicherAuftraege(4);
        var serverthread = new LinkedBlockingQueue<Runnable>();
        var texte = mock(PlotNachrichten.class);
        var auftraege = new PlotAuftraege(speicher, serverthread::add, () -> true, mock(Logger.class), texte);
        var spieler = mock(Player.class);
        when(spieler.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spieler.getName()).thenReturn("Testspieler");
        var freigabe = new CountDownLatch(1);
        var abschluss = new AtomicBoolean();
        try {
            auftraege.ausfuehren(spieler, "Testfehler", () -> {
                assertTrue(freigabe.await(5, TimeUnit.SECONDS));
                throw new IOException("voll");
            }, wert -> fail("Keine Erfolgsmeldung bei Schreibfehlern"), () -> abschluss.set(true));
            auftraege.ausfuehren(spieler, "Doppelter Auftrag", () -> fail("Darf nicht ausgeführt werden"), wert -> { });
            verify(texte).senden(spieler, "fehler.auftrag-laeuft");
            var aktualisiert = new AtomicBoolean();
            auftraege.nachAbschluss(spieler, () -> aktualisiert.set(true));
            freigabe.countDown();
            Runnable rueckmeldung = serverthread.poll(5, TimeUnit.SECONDS);
            assertNotNull(rueckmeldung);
            rueckmeldung.run();
            verify(texte).senden(spieler, "fehler.verarbeitung");
            assertTrue(abschluss.get());
            assertTrue(aktualisiert.get());
            auftraege.ausfuehren(spieler, "Folgeauftrag", () -> true, assertTrueWert -> assertTrue(assertTrueWert));
            rueckmeldung = serverthread.poll(5, TimeUnit.SECONDS);
            assertNotNull(rueckmeldung);
            rueckmeldung.run();
            assertEquals(1, auftraege.status().erfolgreich());
        } finally {
            freigabe.countDown();
            assertTrue(auftraege.beenden(Duration.ofSeconds(5)));
        }
    }
}
