package de.lightrixx.plot.model;

import java.util.Optional;

public record PlotRaster(int groesse, int wegbreite) {
    public static final int MAX_PLOTGROESSE = 1024;
    public static final int MAX_WEGBREITE = 128;

    public PlotRaster {
        if (groesse < 1 || groesse > MAX_PLOTGROESSE || wegbreite < 1 || wegbreite > MAX_WEGBREITE) {
            throw new IllegalArgumentException("Plotgröße oder Wegbreite außerhalb des gültigen Bereichs.");
        }
    }

    public Optional<PlotId> plotBei(int blockX, int blockZ) {
        if (istWeg(blockX, blockZ)) {
            return Optional.empty();
        }
        return Optional.of(zelleBei(blockX, blockZ));
    }

    public boolean istWeg(int blockX, int blockZ) {
        return istWegachse(blockX) || istWegachse(blockZ);
    }

    public PlotId zelleBei(int blockX, int blockZ) {
        return new PlotId(Math.floorDiv(blockX, rasterweite()), Math.floorDiv(blockZ, rasterweite()));
    }

    public boolean istWegachse(int block) {
        return Math.floorMod(block, rasterweite()) >= groesse;
    }

    public boolean istWegrandachse(int block) {
        int position = Math.floorMod(block, rasterweite());
        return position == groesse || position == rasterweite() - 1;
    }

    public int mitteX(PlotId id) {
        return id.x() * rasterweite() + (groesse - 1) / 2;
    }

    public int mitteZ(PlotId id) {
        return id.z() * rasterweite() + (groesse - 1) / 2;
    }

    public int nordwegZ(PlotId id) {
        return id.z() * rasterweite() - 1;
    }

    public int spawnAchse() {
        return -((wegbreite + 1) / 2);
    }

    public int rasterweite() {
        return groesse + wegbreite;
    }
}
