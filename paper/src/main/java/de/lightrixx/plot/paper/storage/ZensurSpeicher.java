package de.lightrixx.plot.paper.storage;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.update.GithubUpdates;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ZensurSpeicher {
    private final Path ordner;
    private final YamlDatei datei;
    private final Plot plot;
    private final PlotRaster raster;
    private final String welt;
    private final int unten;
    private final int oben;
    private YamlConfiguration status;

    public ZensurSpeicher(Path ordner, Plot plot, PlotRaster raster, String welt, int unten, int oben) {
        this.ordner = ordner.resolve(plot.id().toString());
        this.datei = new YamlDatei(this.ordner.resolve("status.yml"));
        this.plot = plot;
        this.raster = raster;
        this.welt = welt;
        this.unten = unten;
        this.oben = oben;
    }

    public boolean oeffnen(boolean wiederherstellen, boolean neu) throws IOException {
        var vorhanden = datei.laden();
        if (neu) {
            if (wiederherstellen) throw new IOException("Aktive Zensursicherung fehlt für " + plot.id());
            status = new YamlConfiguration();
            status.set("format", 1);
            status.set("generation", UUID.randomUUID().toString());
            status.set("welt", welt);
            status.set("unten", unten);
            status.set("oben", oben);
            status.set("groesse", raster.groesse());
            status.set("wegbreite", raster.wegbreite());
            status.set("zellen", plot.zellen().stream().map(Object::toString).sorted().toList());
            status.set("phase", "SICHERN");
            status.createSection("teile");
            datei.speichern(status);
        } else {
            status = vorhanden.orElseThrow(() -> new IOException("Zensursicherung fehlt für " + plot.id()));
            if (status.getInt("format") != 1 || !welt.equals(status.getString("welt"))
                    || unten != status.getInt("unten") || oben != status.getInt("oben")
                    || raster.groesse() != status.getInt("groesse") || raster.wegbreite() != status.getInt("wegbreite")
                    || !plot.zellen().stream().map(Object::toString).sorted().toList().equals(status.getStringList("zellen"))
                    || status.getConfigurationSection("teile") == null
                    || !java.util.Set.of("SICHERN", "ZENSIERT", "WIEDERHERSTELLEN", "WIEDERHERGESTELLT").contains(status.getString("phase", ""))) {
                throw new IOException("Zensursicherung passt nicht zur Plotwelt: " + plot.id());
            }
            try {
                UUID.fromString(status.getString("generation", ""));
            } catch (IllegalArgumentException e) {
                throw new IOException("Ungültige Zensur-Sicherungskennung.", e);
            }
        }
        boolean fortsetzen = wiederherstellen || status.getString("phase", "").startsWith("WIEDERHER");
        if (fortsetzen) {
            if (!status.getBoolean("vollstaendig")) throw new IOException("Zensursicherung ist noch unvollständig.");
            phase("WIEDERHERSTELLEN");
        }
        return fortsetzen;
    }

    public void phase(String wert) throws IOException {
        var neu = kopie();
        neu.set("phase", wert);
        datei.speichern(neu);
        status = neu;
    }

    public Optional<byte[]> teil(String name) throws IOException {
        String hash = status.getString("teile." + name);
        return hash == null ? Optional.empty() : Optional.of(geprueft(name + ".nbt", hash));
    }

    public void teilSichern(String name, byte[] daten) throws IOException {
        if (status.contains("teile." + name)) throw new IOException("Sicherungsteil bereits vorhanden: " + name);
        BinaerDatei.speichern(pfad(name + ".nbt"), daten);
        var neu = kopie();
        neu.set("teile." + name, GithubUpdates.sha256(daten));
        datei.speichern(neu);
        status = neu;
    }

    public Optional<Map<UUID, byte[]>> entities() throws IOException {
        String hash = status.getString("entities");
        if (hash == null) return Optional.empty();
        byte[] bytes = geprueft("entities.yml", hash);
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            var ergebnis = new LinkedHashMap<UUID, byte[]>();
            for (String id : yaml.getKeys(false)) {
                ergebnis.put(UUID.fromString(id), Base64.getDecoder().decode(yaml.getString(id, "")));
            }
            return Optional.of(Map.copyOf(ergebnis));
        } catch (org.bukkit.configuration.InvalidConfigurationException | IllegalArgumentException e) {
            throw new IOException("Ungültige Entity-Sicherung.", e);
        }
    }

    public void entitiesSichern(Map<UUID, byte[]> entities) throws IOException {
        if (status.contains("entities")) throw new IOException("Entity-Sicherung bereits vorhanden.");
        var yaml = new YamlConfiguration();
        entities.forEach((id, daten) -> yaml.set(id.toString(), Base64.getEncoder().encodeToString(daten)));
        byte[] bytes = yaml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BinaerDatei.speichern(pfad("entities.yml"), bytes);
        var neu = kopie();
        neu.set("entities", GithubUpdates.sha256(bytes));
        datei.speichern(neu);
        status = neu;
    }

    public void vollstaendig() throws IOException {
        var neu = kopie();
        neu.set("vollstaendig", true);
        neu.set("phase", "ZENSIERT");
        datei.speichern(neu);
        status = neu;
    }

    private YamlConfiguration kopie() {
        var neu = new YamlConfiguration();
        status.getValues(true).forEach((key, wert) -> {
            if (!status.isConfigurationSection(key)) neu.set(key, wert);
        });
        if (neu.getConfigurationSection("teile") == null) neu.createSection("teile");
        return neu;
    }

    private byte[] geprueft(String name, String hash) throws IOException {
        Path pfad = pfad(name);
        if (Files.size(pfad) > 64L * 1024 * 1024) throw new IOException("Zensursicherung zu groß: " + name);
        byte[] bytes = Files.readAllBytes(pfad);
        if (!GithubUpdates.sha256(bytes).equals(hash)) throw new IOException("Beschädigte Zensursicherung: " + name);
        return bytes;
    }

    private Path pfad(String name) { return ordner.resolve(status.getString("generation")).resolve(name); }
}
