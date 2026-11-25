package de.lightrixx.plot.paper.economy;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotKaeuferTest {
    private final PlotId id = new PlotId(0, 0);
    private final PlotKauf.Kaeufer anna = new PlotKauf.Kaeufer(UUID.randomUUID(), "Anna");
    private final TestWirtschaft wirtschaft = new TestWirtschaft();

    @Test
    void speichertKostenloseClaimsOhneAbbuchung() throws IOException {
        var speicherungen = new AtomicInteger();
        assertEquals(PlotKauf.KaufErgebnis.GEKAUFT, kaeufer(true, "0").abschliessen(anna, id, speicherungen::incrementAndGet));
        assertEquals(1, speicherungen.get());
        assertNull(wirtschaft.abgebucht);
        assertNull(wirtschaft.erstattet);
    }

    @Test
    void buchtDenPreisGenauEinmalAbUndReichtDenNamenWeiter() throws IOException {
        var speicherungen = new AtomicInteger();
        assertEquals(PlotKauf.KaufErgebnis.GEKAUFT,
                kaeufer(true, "250.00").abschliessen(anna, id, speicherungen::incrementAndGet));
        assertEquals(1, speicherungen.get());
        assertEquals(new BigDecimal("250.00"), wirtschaft.abgebucht);
        assertEquals("Anna", wirtschaft.name);
        assertNull(wirtschaft.erstattet);
    }

    @Test
    void speichertNichtOhneGuthaben() {
        wirtschaft.ergebnis = PlotKauf.KaufErgebnis.KEIN_GELD;
        var speicherungen = new AtomicInteger();
        assertEquals(PlotKauf.KaufErgebnis.KEIN_GELD,
                assertDoesNotThrow(() -> kaeufer(true, "250.00").abschliessen(anna, id, speicherungen::incrementAndGet)));
        assertEquals(0, speicherungen.get());
        assertNull(wirtschaft.erstattet);
    }

    @Test
    void reichtFehlendeWirtschaftAlsErgebnisWeiter() {
        wirtschaft.ergebnis = PlotKauf.KaufErgebnis.NICHT_VERFUEGBAR;
        var speicherungen = new AtomicInteger();
        assertEquals(PlotKauf.KaufErgebnis.NICHT_VERFUEGBAR,
                assertDoesNotThrow(() -> kaeufer(true, "250.00").abschliessen(anna, id, speicherungen::incrementAndGet)));
        assertEquals(0, speicherungen.get());
    }

    @Test
    void erstattetDenPreisWennDieSpeicherungScheitert() {
        var fehler = new IOException("Speicher gesperrt");
        assertSame(fehler, assertThrows(IOException.class,
                () -> kaeufer(true, "250.00").abschliessen(anna, id, () -> {
                    throw fehler;
                })));
        assertEquals(new BigDecimal("250.00"), wirtschaft.abgebucht);
        assertEquals(new BigDecimal("250.00"), wirtschaft.erstattet);
    }

    private PlotKaeufer kaeufer(boolean aktiv, String preis) {
        var konfiguration = mock(PlotKonfiguration.class);
        when(konfiguration.aktiv("general.wirtschaft.aktiv")).thenReturn(aktiv);
        when(konfiguration.betrag("general.wirtschaft.preis")).thenReturn(new BigDecimal(preis));
        return new PlotKaeufer(wirtschaft, konfiguration, mock(Logger.class));
    }

    private static final class TestWirtschaft implements Wirtschaft {
        private PlotKauf.KaufErgebnis ergebnis = PlotKauf.KaufErgebnis.GEKAUFT;
        private BigDecimal abgebucht;
        private BigDecimal erstattet;
        private String name;

        @Override
        public PlotKauf.KaufErgebnis abbuchen(UUID spieler, String name, BigDecimal betrag) {
            this.name = name;
            abgebucht = betrag;
            return ergebnis;
        }

        @Override
        public boolean erstatten(UUID spieler, String name, BigDecimal betrag) {
            erstattet = betrag;
            return true;
        }

        @Override
        public String formatiere(BigDecimal betrag) {
            return betrag.toPlainString();
        }
    }
}
