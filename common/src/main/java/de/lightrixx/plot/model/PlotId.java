package de.lightrixx.plot.model;

import java.util.List;

public record PlotId(int x, int z) {
    public static PlotId lesen(String wert) {
        String[] koordinaten = wert.split(";", -1);
        if (koordinaten.length != 2) {
            throw new IllegalArgumentException("Plot-ID muss die Form x;z haben.");
        }
        return new PlotId(Integer.parseInt(koordinaten[0]), Integer.parseInt(koordinaten[1]));
    }

    public PlotId verschoben(int dx, int dz) {
        return new PlotId(x + dx, z + dz);
    }

    public List<PlotId> nachbarn() {
        return List.of(verschoben(1, 0), verschoben(-1, 0), verschoben(0, 1), verschoben(0, -1));
    }

    @Override
    public String toString() {
        return x + ";" + z;
    }
}
