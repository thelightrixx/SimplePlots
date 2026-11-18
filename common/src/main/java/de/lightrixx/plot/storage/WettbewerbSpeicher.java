package de.lightrixx.plot.storage;

import de.lightrixx.plot.model.BauWettbewerb;

import java.io.IOException;
import java.util.Optional;

public interface WettbewerbSpeicher {
    Optional<BauWettbewerb> laden() throws IOException;

    void speichern(Optional<BauWettbewerb> wettbewerb) throws IOException;
}
