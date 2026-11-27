package de.lightrixx.plot.storage;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotEntfernung;

import java.io.IOException;
import java.util.List;

public interface PlotSpeicher {
    List<Plot> laden() throws IOException;

    void speichern(List<Plot> plots) throws IOException;

    default List<PlotEntfernung> entfernungen() throws IOException { return List.of(); }

    /** Claims und entfernte Flächen müssen zusammen atomar gespeichert werden. */
    default void speichern(List<Plot> plots, List<PlotEntfernung> entfernt) throws IOException {
        if (!entfernt.isEmpty()) throw new IOException("Der Plotspeicher unterstützt keine entfernten Flächen.");
        speichern(plots);
    }
}
