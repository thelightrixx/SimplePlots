package de.lightrixx.plot.paper.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.Objects;

public final class YamlDatei {
    private static final int MAX_ZEICHEN = 64 * 1024 * 1024;
    private static final int ERSETZVERSUCHE = 5;
    private final Path pfad;
    private final Austausch austausch;

    public YamlDatei(Path pfad) {
        this(pfad, (quelle, ziel) -> Files.move(quelle, ziel, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    YamlDatei(Path pfad, Austausch austausch) {
        this.pfad = pfad.toAbsolutePath();
        this.austausch = Objects.requireNonNull(austausch, "Atomarer Dateiaustausch");
    }

    public Optional<YamlConfiguration> laden() throws IOException {
        String text;
        try {
            if (Files.size(pfad) > MAX_ZEICHEN) throw new IOException("YAML-Datei ist zu groß: " + pfad);
            text = Files.readString(pfad);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        }
        var yaml = new YamlConfiguration();
        yaml.options().codePointLimit(MAX_ZEICHEN);
        try {
            var optionen = new LoaderOptions();
            optionen.setAllowDuplicateKeys(false);
            optionen.setCodePointLimit(MAX_ZEICHEN);
            Object daten = new Yaml(new SafeConstructor(optionen)).load(text);
            if (!(daten instanceof java.util.Map<?, ?> mapping) || mapping.isEmpty()) {
                throw new IOException("Leeres oder ungültiges YAML-Mapping in " + pfad);
            }
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException | YAMLException e) {
            throw new IOException("Ungültiges YAML in " + pfad, e);
        }
        return Optional.of(yaml);
    }

    public void speichern(YamlConfiguration yaml) throws IOException {
        byte[] inhalt = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        if (inhalt.length > MAX_ZEICHEN) throw new IOException("YAML-Datei würde größer als 64 MiB; bisheriger Stand und Sicherung bleiben erhalten: " + pfad);
        Files.createDirectories(pfad.getParent());
        if (Files.exists(pfad)) {
            // Eine fehlgeschlagene Sicherung darf den bisherigen Spielstand nicht gefährden.
            atomarSchreiben(pfad.resolveSibling(pfad.getFileName() + ".bak"), Files.readAllBytes(pfad));
        }
        atomarSchreiben(pfad, inhalt);
    }

    private void atomarSchreiben(Path ziel, byte[] inhalt) throws IOException {
        Path temp = Files.createTempFile(ziel.getParent(), ziel.getFileName() + ".", ".tmp");
        try {
            try (var kanal = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var puffer = ByteBuffer.wrap(inhalt);
                while (puffer.hasRemaining()) {
                    kanal.write(puffer);
                }
                kanal.force(true);
            }
            ersetzen(temp, ziel);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    private void ersetzen(Path quelle, Path ziel) throws IOException {
        for (int versuch = 1; ; versuch++) {
            try {
                austausch.ersetzen(quelle, ziel);
                return;
            } catch (AccessDeniedException e) {
                if (versuch >= ERSETZVERSUCHE) {
                    throw e;
                }
                // Windows kann den atomaren Austausch durch kurzzeitige Dateisperren verhindern.
                try {
                    Thread.sleep(20);
                } catch (InterruptedException unterbrochen) {
                    Thread.currentThread().interrupt();
                    var fehler = new IOException("Atomarer Dateiaustausch für " + ziel + " wurde unterbrochen.", unterbrochen);
                    fehler.addSuppressed(e);
                    throw fehler;
                }
            }
        }
    }

    @FunctionalInterface
    interface Austausch {
        void ersetzen(Path quelle, Path ziel) throws IOException;
    }
}
