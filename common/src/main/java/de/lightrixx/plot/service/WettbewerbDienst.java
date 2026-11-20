package de.lightrixx.plot.service;

import de.lightrixx.plot.model.BauWettbewerb.Phase;
import de.lightrixx.plot.model.BauWettbewerb;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.storage.WettbewerbSpeicher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WettbewerbDienst {
    private final WettbewerbSpeicher speicher;
    private final PlotDienst plots;
    private volatile Optional<BauWettbewerb> aktuell;

    public WettbewerbDienst(WettbewerbSpeicher speicher, PlotDienst plots) throws IOException {
        this.speicher = speicher;
        this.plots = plots;
        aktuell = speicher.laden();
        if (aktiv()) {
            for (var beitrag : aktuell.orElseThrow().beitraege().entrySet()) {
                if (plots.finden(beitrag.getKey()).filter(plot -> plot.id().equals(beitrag.getKey())
                        && plot.besitzer().equals(beitrag.getValue())).isEmpty()) {
                    throw new IOException("Wettbewerbsplot " + beitrag.getKey() + " passt nicht zu den gespeicherten Claims.");
                }
            }
        }
    }

    public Optional<BauWettbewerb> aktuell() {
        return aktuell;
    }

    public boolean gesperrt(PlotId id) {
        return aktuell.filter(wert -> wert.phase() != Phase.BEENDET)
                .filter(wert -> wert.beitraege().containsKey(id)).isPresent();
    }

    public boolean hatGesperrtePlots() {
        return aktuell.filter(wert -> wert.phase() != Phase.BEENDET && !wert.beitraege().isEmpty()).isPresent();
    }

    private boolean aktiv() {
        return aktuell.filter(wert -> wert.phase() != Phase.BEENDET).isPresent();
    }

    public synchronized Ergebnis starten(String thema) throws IOException {
        if (aktiv()) {
            return Ergebnis.BEREITS_AKTIV;
        }
        speichern(Optional.of(new BauWettbewerb(thema, Phase.BAUEN, Map.of(), Map.of())));
        return Ergebnis.ERFOLG;
    }

    public synchronized Ergebnis abstimmungOeffnen() throws IOException {
        var wettbewerb = inPhase(Phase.BAUEN);
        if (wettbewerb.isEmpty()) {
            return Ergebnis.FALSCHE_PHASE;
        }
        var daten = wettbewerb.get();
        if (daten.beitraege().isEmpty()) {
            return Ergebnis.KEINE_BEITRAEGE;
        }
        speichern(Optional.of(new BauWettbewerb(daten.thema(), Phase.ABSTIMMEN, daten.beitraege(), daten.stimmen())));
        return Ergebnis.ERFOLG;
    }

    public synchronized Ergebnis beenden() throws IOException {
        var wettbewerb = inPhase(Phase.ABSTIMMEN);
        if (wettbewerb.isEmpty()) {
            return Ergebnis.FALSCHE_PHASE;
        }
        var daten = wettbewerb.get();
        speichern(Optional.of(new BauWettbewerb(daten.thema(), Phase.BEENDET, daten.beitraege(), daten.stimmen())));
        return Ergebnis.ERFOLG;
    }

    public synchronized Ergebnis abbrechen() throws IOException {
        if (!aktiv()) {
            return Ergebnis.FALSCHE_PHASE;
        }
        speichern(Optional.empty());
        return Ergebnis.ERFOLG;
    }

    public synchronized Ergebnis einreichen(PlotId id, UUID spieler) throws IOException {
        return einreichen(id, spieler, Integer.MAX_VALUE);
    }

    public synchronized Ergebnis einreichen(PlotId id, UUID spieler, int limit) throws IOException {
        if (limit < 1) throw new IllegalArgumentException("Beitragslimit muss positiv sein.");
        var wettbewerb = inPhase(Phase.BAUEN);
        if (wettbewerb.isEmpty()) {
            return Ergebnis.FALSCHE_PHASE;
        }
        var plot = plots.finden(id).filter(treffer -> !treffer.zensiert() && treffer.besitzer().equals(spieler));
        if (plot.isEmpty()) {
            return Ergebnis.FREMDER_PLOT;
        }
        var daten = wettbewerb.get();
        if (daten.beitraege().containsValue(spieler)) {
            return Ergebnis.BEREITS_EINGEREICHT;
        }
        if (daten.beitraege().size() >= limit) return Ergebnis.BEITRAGSLIMIT;
        var neu = new HashMap<>(daten.beitraege());
        neu.put(plot.get().id(), spieler);
        speichern(Optional.of(new BauWettbewerb(daten.thema(), daten.phase(), neu, daten.stimmen())));
        return Ergebnis.ERFOLG;
    }

    public synchronized Ergebnis zurueckziehen(UUID spieler) throws IOException {
        var wettbewerb = inPhase(Phase.BAUEN);
        if (wettbewerb.isEmpty()) {
            return Ergebnis.FALSCHE_PHASE;
        }
        var daten = wettbewerb.get();
        var neu = new HashMap<>(daten.beitraege());
        if (!neu.values().remove(spieler)) {
            return Ergebnis.KEIN_BEITRAG;
        }
        speichern(Optional.of(new BauWettbewerb(daten.thema(), daten.phase(), neu, daten.stimmen())));
        return Ergebnis.ERFOLG;
    }

    public synchronized Ergebnis abstimmen(PlotId id, UUID spieler) throws IOException {
        var wettbewerb = inPhase(Phase.ABSTIMMEN);
        if (wettbewerb.isEmpty()) {
            return Ergebnis.FALSCHE_PHASE;
        }
        var daten = wettbewerb.get();
        var plot = plots.finden(id).filter(treffer -> !treffer.zensiert() && daten.beitraege().containsKey(treffer.id()));
        if (plot.isEmpty()) {
            return Ergebnis.KEIN_BEITRAG;
        }
        var beitrag = plot.get();
        if (beitrag.besitzer().equals(spieler)) {
            return Ergebnis.EIGENER_PLOT;
        }
        if (!beitrag.darfBetreten(spieler)) {
            return Ergebnis.GEBANNT;
        }
        if (beitrag.id().equals(daten.stimmen().get(spieler))) {
            return Ergebnis.BEREITS_GEWAEHLT;
        }
        var neu = new HashMap<>(daten.stimmen());
        neu.put(spieler, beitrag.id());
        speichern(Optional.of(new BauWettbewerb(daten.thema(), daten.phase(), daten.beitraege(), neu)));
        return Ergebnis.ERFOLG;
    }

    private Optional<BauWettbewerb> inPhase(Phase phase) {
        return aktuell.filter(wettbewerb -> wettbewerb.phase() == phase);
    }

    private void speichern(Optional<BauWettbewerb> neu) throws IOException {
        speicher.speichern(neu);
        aktuell = neu;
    }

    public enum Ergebnis {
        ERFOLG, BEREITS_AKTIV, FALSCHE_PHASE, KEINE_BEITRAEGE, FREMDER_PLOT, BEREITS_EINGEREICHT,
        KEIN_BEITRAG, EIGENER_PLOT, GEBANNT, BEREITS_GEWAEHLT, BEITRAGSLIMIT
    }
}
