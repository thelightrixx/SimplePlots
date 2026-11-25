package de.lightrixx.plot.economy;

import de.lightrixx.plot.model.PlotId;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@FunctionalInterface
public interface PlotKauf {
    /**
     * Schließt einen bereits geprüften Claim synchron ab. Bei Annahme genau einmal speichern.
     * Eine Ablehnung lässt den Speicher unangetastet. Eine kostenpflichtige Implementierung
     * muss Abbuchung und Speicherung auch bei Fehlern konsistent halten.
     */
    KaufErgebnis abschliessen(Kaeufer kaeufer, PlotId plot, Speicherung speicherung) throws IOException;

    static PlotKauf kostenlos() {
        return (kaeufer, plot, speicherung) -> {
            speicherung.speichern();
            return KaufErgebnis.GEKAUFT;
        };
    }

    enum KaufErgebnis {
        GEKAUFT, KEIN_GELD, NICHT_VERFUEGBAR
    }

    record Kaeufer(UUID id, String name) {
        public Kaeufer {
            Objects.requireNonNull(id, "Spieler-ID");
            name = name == null || name.isBlank() ? id.toString() : name;
        }
    }

    @FunctionalInterface
    interface Speicherung {
        void speichern() throws IOException;
    }
}
