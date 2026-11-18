package de.lightrixx.plot.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BauWettbewerb(String thema, Phase phase, Map<PlotId, UUID> beitraege, Map<UUID, PlotId> stimmen) {
    public BauWettbewerb {
        thema = thema.strip();
        if (thema.isEmpty() || thema.codePointCount(0, thema.length()) > 64
                || thema.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Das Wettbewerbsthema muss 1 bis 64 Zeichen lang sein.");
        }
        Objects.requireNonNull(phase, "Wettbewerbsphase");
        beitraege = Map.copyOf(beitraege);
        stimmen = Map.copyOf(stimmen);
        if (new HashSet<>(beitraege.values()).size() != beitraege.size()
                || (phase == Phase.BAUEN && !stimmen.isEmpty())
                || (phase != Phase.BAUEN && beitraege.isEmpty())) {
            throw new IllegalArgumentException("Beiträge oder Stimmen passen nicht zur Wettbewerbsphase.");
        }
        for (var stimme : stimmen.entrySet()) {
            UUID besitzer = beitraege.get(stimme.getValue());
            if (besitzer == null || besitzer.equals(stimme.getKey())) {
                throw new IllegalArgumentException("Ungültige Wettbewerbsstimme.");
            }
        }
    }

    public List<Platzierung> ergebnisse() {
        var anzahl = new HashMap<PlotId, Integer>();
        stimmen.values().forEach(plot -> anzahl.merge(plot, 1, Integer::sum));
        return beitraege.keySet().stream().map(id -> new Platzierung(id, anzahl.getOrDefault(id, 0)))
                .sorted(Comparator.comparingInt(Platzierung::stimmen).reversed()
                        .thenComparingInt(eintrag -> eintrag.plot().x()).thenComparingInt(eintrag -> eintrag.plot().z()))
                .toList();
    }

    public enum Phase {
        BAUEN, ABSTIMMEN, BEENDET
    }

    public record Platzierung(PlotId plot, int stimmen) {
    }
}
