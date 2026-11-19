package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.storage.YamlDatei;

import java.io.IOException;
import java.nio.file.Path;

public final class PlotKonfiguration {
    private final YamlDatei datei;
    private volatile Werte werte;

    public PlotKonfiguration(Path pfad) throws IOException {
        this.datei = new YamlDatei(pfad);
        werte = vorbereiten();
    }

    public Werte vorbereiten() throws IOException {
        var yaml = datei.laden().orElseThrow(() -> new IOException("config.yml fehlt."));
        try {
            var raster = new PlotRaster(yaml.getInt("raster.plotgroesse"), yaml.getInt("raster.wegbreite"));
            int claimLimit = yaml.getInt("claims.pro-spieler");
            if (claimLimit < 1 || !yaml.isInt("claims.pro-spieler")
                    || !yaml.isInt("raster.plotgroesse") || !yaml.isInt("raster.wegbreite")) {
                throw new IllegalArgumentException("Rasterwerte und Claim-Limit müssen positive Ganzzahlen sein.");
            }
            return new Werte(raster, claimLimit);
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültige Einstellungen in config.yml.", e);
        }
    }

    public PlotRaster raster() {
        return werte.raster();
    }

    public int claimLimit() {
        return werte.claimLimit();
    }

    public void rasterSetzen(PlotRaster wert) throws IOException {
        var yaml = datei.laden().orElseThrow(() -> new IOException("config.yml fehlt."));
        yaml.set("raster.plotgroesse", wert.groesse());
        yaml.set("raster.wegbreite", wert.wegbreite());
        datei.speichern(yaml);
        werte = new Werte(wert, claimLimit());
    }

    public void limitSetzen(int wert) throws IOException {
        if (wert < 1) {
            throw new IllegalArgumentException("Das Claim-Limit muss mindestens 1 sein.");
        }
        var yaml = datei.laden().orElseThrow(() -> new IOException("config.yml fehlt."));
        yaml.set("claims.pro-spieler", wert);
        datei.speichern(yaml);
        werte = new Werte(raster(), wert);
    }

    public void uebernehmen(Werte neu) {
        werte = neu;
    }

    public record Werte(PlotRaster raster, int claimLimit) {
        public Werte {
            java.util.Objects.requireNonNull(raster, "Plotraster");
            if (claimLimit < 1) {
                throw new IllegalArgumentException("Das Claim-Limit muss mindestens 1 sein.");
            }
        }
    }
}
