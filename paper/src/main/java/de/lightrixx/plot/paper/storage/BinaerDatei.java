package de.lightrixx.plot.paper.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class BinaerDatei {
    private BinaerDatei() { }

    public static void speichern(Path ziel, byte[] daten) throws IOException {
        Files.createDirectories(ziel.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(ziel.toAbsolutePath().getParent(), ziel.getFileName() + ".", ".tmp");
        try {
            try (var kanal = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var puffer = ByteBuffer.wrap(daten);
                while (puffer.hasRemaining()) {
                    kanal.write(puffer);
                }
                kanal.force(true);
            }
            Files.move(temp, ziel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }
}
