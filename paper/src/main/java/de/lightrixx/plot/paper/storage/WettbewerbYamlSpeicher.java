package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.model.BauWettbewerb;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.storage.WettbewerbSpeicher;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public final class WettbewerbYamlSpeicher implements WettbewerbSpeicher {
    private final YamlDatei datei;

    public WettbewerbYamlSpeicher(Path pfad) {
        datei = new YamlDatei(pfad);
    }

    @Override
    public Optional<BauWettbewerb> laden() throws IOException {
        var geladen = datei.laden();
        if (geladen.isEmpty()) {
            return Optional.empty();
        }
        var yaml = geladen.get();
        if (!yaml.isInt("format") || yaml.getInt("format") != 1) {
            throw new IOException("Ungültiges Datenformat in wettbewerb.yml.");
        }
        if (!yaml.contains("wettbewerb")) {
            return Optional.empty();
        }
        try {
            return Optional.of(lesen(yaml.getConfigurationSection("wettbewerb")));
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültiger Wettbewerb in wettbewerb.yml.", e);
        }
    }

    private static BauWettbewerb lesen(ConfigurationSection daten) {
        if (daten == null || !daten.isString("thema") || !daten.isString("phase")) {
            throw new IllegalArgumentException("Thema oder Phase fehlt.");
        }
        var beitraege = new HashMap<PlotId, UUID>();
        var stimmen = new HashMap<UUID, PlotId>();
        var beitragsSektion = daten.getConfigurationSection("beitraege");
        var stimmenSektion = daten.getConfigurationSection("stimmen");
        if (beitragsSektion == null || stimmenSektion == null) {
            throw new IllegalArgumentException("Beiträge oder Stimmen fehlen.");
        }
        for (String id : beitragsSektion.getKeys(false)) {
            if (!beitragsSektion.isString(id)
                    || beitraege.putIfAbsent(PlotId.lesen(id), UUID.fromString(beitragsSektion.getString(id))) != null) {
                throw new IllegalArgumentException("Ungültiger Wettbewerbsbeitrag.");
            }
        }
        for (String spieler : stimmenSektion.getKeys(false)) {
            if (!stimmenSektion.isString(spieler)
                    || stimmen.putIfAbsent(UUID.fromString(spieler), PlotId.lesen(stimmenSektion.getString(spieler))) != null) {
                throw new IllegalArgumentException("Ungültige Wettbewerbsstimme.");
            }
        }
        return new BauWettbewerb(daten.getString("thema"), BauWettbewerb.Phase.valueOf(daten.getString("phase")), beitraege, stimmen);
    }

    @Override
    public void speichern(Optional<BauWettbewerb> wettbewerb) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("format", 1);
        wettbewerb.ifPresent(wert -> {
            var daten = yaml.createSection("wettbewerb");
            daten.set("thema", wert.thema());
            daten.set("phase", wert.phase().name());
            var beitraege = daten.createSection("beitraege");
            wert.beitraege().forEach((id, besitzer) -> beitraege.set(id.toString(), besitzer.toString()));
            var stimmen = daten.createSection("stimmen");
            wert.stimmen().forEach((spieler, id) -> stimmen.set(spieler.toString(), id.toString()));
        });
        datei.speichern(yaml);
    }
}
