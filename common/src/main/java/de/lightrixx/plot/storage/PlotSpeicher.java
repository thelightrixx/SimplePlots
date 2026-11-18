package de.lightrixx.plot.storage;

import de.lightrixx.plot.model.Plot;

import java.io.IOException;
import java.util.List;

public interface PlotSpeicher {
    List<Plot> laden() throws IOException;

    void speichern(List<Plot> plots) throws IOException;
}
