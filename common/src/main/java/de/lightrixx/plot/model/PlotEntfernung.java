package de.lightrixx.plot.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Nur die Fläche und der Arbeitsstand bleiben erhalten, keine früheren Besitz- oder Inhaltsdaten. */
public record PlotEntfernung(PlotId id, Set<PlotId> zellen, Phase phase) {
    public PlotEntfernung {
        Objects.requireNonNull(id, "Plot-ID");
        Objects.requireNonNull(phase, "Phase");
        zellen = Set.copyOf(zellen);
        // Verwendet dieselbe Zusammenhangsprüfung wie reguläre Claims.
        new Plot(id, new UUID(0, 0), new PlotRechte(Set.of(), Set.of(), Set.of(), Set.of(), Set.of()), PlotProfil.leer(), zellen);
    }

    public PlotEntfernung mitPhase(Phase neu) { return new PlotEntfernung(id, zellen, neu); }

    public enum Phase { ENTFERNEN, ENTFERNT, WIEDERHERSTELLEN }
}
