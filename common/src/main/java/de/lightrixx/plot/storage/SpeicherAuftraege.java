package de.lightrixx.plot.storage;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Serialisiert zusammengehörige Plot- und Wettbewerbsänderungen außerhalb des Serverthreads. */
public final class SpeicherAuftraege {
    private final ThreadPoolExecutor executor;
    private final AtomicLong erfolgreich = new AtomicLong();
    private final AtomicLong fehlgeschlagen = new AtomicLong();
    private volatile Abschluss letzterAbschluss = new Abschluss(0, 0);

    public SpeicherAuftraege(int kapazitaet) {
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(kapazitaet),
                Thread.ofPlatform().name("SimplePlots-IO").daemon(true).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> einreichen(Auftrag<T> auftrag) {
        Objects.requireNonNull(auftrag, "Speicherauftrag");
        var ergebnis = new CompletableFuture<T>();
        try {
            executor.execute(() -> {
                long start = System.nanoTime();
                try {
                    T wert = auftrag.ausfuehren();
                    erfolgreich.incrementAndGet();
                    letzterAbschluss = new Abschluss(System.currentTimeMillis(), dauer(start));
                    ergebnis.complete(wert);
                } catch (Throwable e) {
                    fehlgeschlagen.incrementAndGet();
                    letzterAbschluss = new Abschluss(System.currentTimeMillis(), dauer(start));
                    ergebnis.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            ergebnis.completeExceptionally(e);
        }
        return ergebnis;
    }

    public Status status() {
        var abschluss = letzterAbschluss;
        return new Status(executor.getQueue().size(), executor.getActiveCount(), erfolgreich.get(), fehlgeschlagen.get(),
                abschluss.zeit(), abschluss.dauerMillis(), executor.isShutdown());
    }

    /** Nimmt keine neuen Aufträge an; bereits angenommene Aufträge werden vollständig abgearbeitet. */
    public boolean beenden(Duration wartezeit) {
        if (wartezeit.isNegative()) {
            throw new IllegalArgumentException("Die Wartezeit darf nicht negativ sein.");
        }
        executor.shutdown();
        try {
            return executor.awaitTermination(wartezeit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long dauer(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @FunctionalInterface
    public interface Auftrag<T> {
        T ausfuehren() throws Exception;
    }

    public record Status(int wartend, int laufend, long erfolgreich, long fehlgeschlagen,
                         long letzterAbschluss, long letzteDauerMillis, boolean beendet) {
    }

    private record Abschluss(long zeit, long dauerMillis) {
    }
}
