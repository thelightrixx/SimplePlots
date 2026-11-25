package de.lightrixx.plot.paper.economy;

import de.lightrixx.plot.economy.PlotKauf;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Geldsystem-Anbindung. Aufrufe erfolgen aus der I/O-Warteschlange, nicht auf dem Serverthread.
 */
public interface Wirtschaft {
    PlotKauf.KaufErgebnis abbuchen(UUID spieler, String name, BigDecimal betrag);

    boolean erstatten(UUID spieler, String name, BigDecimal betrag);

    String formatiere(BigDecimal betrag);
}
