package de.lightrixx.plot.economy;

import de.lightrixx.plot.model.PlotId;

import java.io.IOException;
import java.util.UUID;

@FunctionalInterface
public interface PlotKauf {
    /**
     * Schließt einen bereits geprüften Claim synchron ab. Bei Annahme genau einmal speichern.
     * Eine Ablehnung lässt den Speicher unangetastet. Eine kostenpflichtige Implementierung
     * muss Abbuchung und Speicherung auch bei Fehlern konsistent halten.
     */
    boolean abschliessen(UUID spieler, PlotId plot, Speicherung speicherung) throws IOException;

    static PlotKauf kostenlos() {
        return (spieler, plot, speicherung) -> {
            speicherung.speichern();
            return true;
        };
    }

    @FunctionalInterface
    interface Speicherung {
        void speichern() throws IOException;
    }
}
