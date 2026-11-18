package de.lightrixx.plot.paper.config;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.storage.YamlDatei;

import java.io.IOException;
import java.nio.file.Path;

public final class PlotKonfiguration {
    private final YamlDatei datei;
    private PlotRaster raster;
    private int claimLimit;

    public PlotKonfiguration(Path pfad) throws IOException {
        this.datei = new YamlDatei(pfad);
        var yaml = datei.laden().orElseThrow(() -> new IOException("config.yml fehlt."));
        try {
            raster = new PlotRaster(yaml.getInt("raster.plotgroesse"), yaml.getInt("raster.wegbreite"));
            claimLimit = yaml.getInt("claims.pro-spieler");
            if (claimLimit < 1 || !yaml.isInt("claims.pro-spieler")
                    || !yaml.isInt("raster.plotgroesse") || !yaml.isInt("raster.wegbreite")) {
                throw new IllegalArgumentException("Rasterwerte und Claim-Limit müssen positive Ganzzahlen sein.");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültige Einstellungen in config.yml.", e);
        }
    }

    public PlotRaster raster() {
        return raster;
    }

    public int claimLimit() {
        return claimLimit;
    }

    public void rasterSetzen(PlotRaster wert) throws IOException {
        var yaml = datei.laden().orElseThrow(() -> new IOException("config.yml fehlt."));
        yaml.set("raster.plotgroesse", wert.groesse());
        yaml.set("raster.wegbreite", wert.wegbreite());
        datei.speichern(yaml);
        raster = wert;
    }

    public void limitSetzen(int wert) throws IOException {
        if (wert < 1) {
            throw new IllegalArgumentException("Das Claim-Limit muss mindestens 1 sein.");
        }
        var yaml = datei.laden().orElseThrow(() -> new IOException("config.yml fehlt."));
        yaml.set("claims.pro-spieler", wert);
        datei.speichern(yaml);
        claimLimit = wert;
    }
}
