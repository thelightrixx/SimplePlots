package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.PlotEntfernung;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.service.PlotDienst;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotEntfernungSpeicherTest {
    @TempDir Path ordner;
    private final PlotId id = new PlotId(0, 0);
    private final UUID besitzer = UUID.randomUUID();
    private PlotDienst laden() throws IOException { return new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos()); }

    @Test
    void persistiertAllePhasenUndVerdichtetMitWeiterenClaimsOhneEntfernteZellenZuVerlieren() throws IOException {
        var dienst = laden();
        dienst.claim(id, besitzer, 500);
        assertTrue(dienst.entfernen(id, dienst.finden(id), ziel -> false));
        var neu = laden();
        assertTrue(neu.finden(id).isEmpty());
        var eintrag = neu.entfernung(id).orElseThrow();
        assertEquals(PlotEntfernung.Phase.ENTFERNEN, eintrag.phase());
        assertTrue(neu.entfernungsphase(eintrag, PlotEntfernung.Phase.ENTFERNT));
        for (int i = 1; i <= 130; i++) neu.claim(new PlotId(i, 0), besitzer, 500);
        neu = laden();
        assertEquals(130, neu.anzahlPlots());
        eintrag = neu.entfernung(id).orElseThrow();
        assertEquals(PlotEntfernung.Phase.ENTFERNT, eintrag.phase());
        assertTrue(neu.entfernungsphase(eintrag, PlotEntfernung.Phase.WIEDERHERSTELLEN));
        neu = laden();
        assertTrue(neu.wiederhergestellt(neu.entfernung(id).orElseThrow()));
        assertTrue(laden().entfernungen().isEmpty());
        assertTrue(laden().finden(id).isEmpty());
    }

    @Test
    void behaeltBeiFehlgeschlagenemJournalDenKomplettenVorherigenClaim() throws IOException {
        var dienst = laden();
        dienst.claim(id, besitzer, 1);
        var vorher = dienst.finden(id);
        Path bak = ordner.resolve("plots-aenderungen.yml.bak");
        Files.delete(bak); Files.createDirectory(bak); Files.writeString(bak.resolve("behalten"), "behalten");
        assertThrows(IOException.class, () -> dienst.entfernen(id, vorher, ziel -> false));
        assertEquals(vorher, dienst.finden(id)); assertEquals(vorher, laden().finden(id));
        assertTrue(laden().entfernungen().isEmpty());
    }

    @Test
    void migriertFormatDreiMitDerErstenEntfernungUndBehaeltBesitzBisZumSpeicherabschluss() throws IOException {
        var dienst = laden(); dienst.claim(id, besitzer, 1);
        var basis = new YamlDatei(ordner.resolve("plots.yml"));
        var yaml = basis.laden().orElseThrow(); yaml.set("format", 3); yaml.set("entfernte-flaechen", null); basis.speichern(yaml);
        dienst = laden();
        assertTrue(dienst.entfernen(id, dienst.finden(id), ziel -> false));
        assertTrue(laden().finden(id).isEmpty()); assertEquals(1, laden().entfernungen().size());
    }

    @Test
    void lehntUngenaueEntfernungsdatenAbUndLaesstDateienUnveraendert() throws IOException {
        var dienst = laden(); dienst.entfernen(id, Optional.empty(), ziel -> false);
        Path journal = ordner.resolve("plots-aenderungen.yml");
        String vorher = Files.readString(journal).replace("ENTFERNEN", "UNBEKANNT");
        Files.writeString(journal, vorher);
        assertThrows(IOException.class, this::laden);
        assertEquals(vorher, Files.readString(journal));
    }
}
