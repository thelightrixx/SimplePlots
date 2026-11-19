package de.lightrixx.plot.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class SpeicherAuftraegeTest {
    @Test
    void serialisiertAuftraegeUndBegrenztDenRueckstauOhneSynchronenFallback() throws Exception {
        var auftraege = new SpeicherAuftraege(1);
        var gestartet = new CountDownLatch(1);
        var freigabe = new CountDownLatch(1);
        var reihenfolge = new ArrayList<Integer>();
        Thread aufrufer = Thread.currentThread();
        try {
            var erster = auftraege.einreichen(() -> {
                assertNotSame(aufrufer, Thread.currentThread());
                gestartet.countDown();
                assertTrue(freigabe.await(5, TimeUnit.SECONDS));
                reihenfolge.add(1);
                return 1;
            });
            assertTrue(gestartet.await(5, TimeUnit.SECONDS));
            var zweiter = auftraege.einreichen(() -> reihenfolge.add(2));
            var abgewiesen = auftraege.einreichen(() -> reihenfolge.add(3));
            var fehler = assertThrows(ExecutionException.class, () -> abgewiesen.get(5, TimeUnit.SECONDS));
            assertInstanceOf(RejectedExecutionException.class, fehler.getCause());
            assertEquals(1, auftraege.status().wartend());
            assertEquals(1, auftraege.status().laufend());
            freigabe.countDown();
            assertEquals(1, erster.get(5, TimeUnit.SECONDS));
            zweiter.get(5, TimeUnit.SECONDS);
            assertEquals(List.of(1, 2), reihenfolge);
            assertEquals(2, auftraege.status().erfolgreich());
        } finally {
            freigabe.countDown();
            assertTrue(auftraege.beenden(Duration.ofSeconds(5)));
        }
    }

    @Test
    void meldetSchreibfehlerUndVerarbeitetFolgeauftraegeWeiter() throws Exception {
        var auftraege = new SpeicherAuftraege(4);
        try {
            var kaputt = auftraege.einreichen(() -> { throw new IOException("Dateisystem nicht beschreibbar"); });
            var danach = auftraege.einreichen(() -> "gespeichert");
            assertInstanceOf(IOException.class,
                    assertThrows(ExecutionException.class, () -> kaputt.get(5, TimeUnit.SECONDS)).getCause());
            assertEquals("gespeichert", danach.get(5, TimeUnit.SECONDS));
            assertEquals(1, auftraege.status().fehlgeschlagen());
            assertEquals(1, auftraege.status().erfolgreich());
            assertTrue(auftraege.status().letzterAbschluss() > 0);
        } finally {
            assertTrue(auftraege.beenden(Duration.ofSeconds(5)));
        }
        assertTrue(auftraege.status().beendet());
        assertInstanceOf(RejectedExecutionException.class,
                assertThrows(ExecutionException.class, () -> auftraege.einreichen(() -> null).get()).getCause());
    }

    @Test
    void beendetAngenommeneAuftraegeVorDemShutdown() throws Exception {
        var auftraege = new SpeicherAuftraege(32);
        var ergebnisse = new ArrayList<CompletableFuture<Integer>>();
        for (int i = 0; i < 20; i++) {
            int nummer = i;
            ergebnisse.add(auftraege.einreichen(() -> nummer));
        }
        assertTrue(auftraege.beenden(Duration.ofSeconds(5)));
        for (int i = 0; i < ergebnisse.size(); i++) {
            assertEquals(i, ergebnisse.get(i).get());
        }
        assertEquals(20, auftraege.status().erfolgreich());
    }

    @Test
    void beendetDasFutureAuchBeiProgrammierfehlernUndVerliertKeineFolgeauftraege() throws Exception {
        var auftraege = new SpeicherAuftraege(4);
        try {
            var fehlgeschlagen = auftraege.einreichen(() -> { throw new AssertionError("Fehler im Auftrag"); });
            assertInstanceOf(AssertionError.class,
                    assertThrows(ExecutionException.class, () -> fehlgeschlagen.get(5, TimeUnit.SECONDS)).getCause());
            assertEquals("danach", auftraege.einreichen(() -> "danach").get(5, TimeUnit.SECONDS));
            assertEquals(1, auftraege.status().fehlgeschlagen());
        } finally {
            assertTrue(auftraege.beenden(Duration.ofSeconds(5)));
        }
    }
}
