package de.lightrixx.plot.paper.economy;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import org.slf4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;

/** Zieht den konfigurierten Claim-Preis ein und erstattet ihn, wenn die Speicherung scheitert. */
public final class PlotKaeufer implements PlotKauf {
    private final Wirtschaft wirtschaft;
    private final PlotKonfiguration konfiguration;
    private final Logger log;

    public PlotKaeufer(Wirtschaft wirtschaft, PlotKonfiguration konfiguration, Logger log) {
        this.wirtschaft = wirtschaft;
        this.konfiguration = konfiguration;
        this.log = log;
    }

    @Override
    public KaufErgebnis abschliessen(Kaeufer kaeufer, PlotId plot, Speicherung speicherung) throws IOException {
        BigDecimal preis = konfiguration.betrag("general.wirtschaft.preis");
        if (!konfiguration.aktiv("general.wirtschaft.aktiv") || preis.signum() == 0) {
            speicherung.speichern();
            return KaufErgebnis.GEKAUFT;
        }
        KaufErgebnis ergebnis = wirtschaft.abbuchen(kaeufer.id(), kaeufer.name(), preis);
        if (ergebnis != KaufErgebnis.GEKAUFT) {
            return ergebnis;
        }
        try {
            speicherung.speichern();
        } catch (IOException | RuntimeException e) {
            if (!wirtschaft.erstatten(kaeufer.id(), kaeufer.name(), preis)) {
                log.error("Erstattung von {} für Plot {} an {} nach fehlgeschlagener Speicherung fehlgeschlagen.",
                        preis.toPlainString(), plot, kaeufer.name());
            }
            throw e;
        }
        return KaufErgebnis.GEKAUFT;
    }
}
