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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class YamlDatei {
    private static final int MAX_ZEICHEN = 64 * 1024 * 1024;
    private final Path pfad;

    public YamlDatei(Path pfad) {
        this.pfad = pfad.toAbsolutePath();
    }

    public Optional<YamlConfiguration> laden() throws IOException {
        String text;
        try {
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
            new Yaml(new SafeConstructor(optionen)).load(text);
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException | YAMLException e) {
            throw new IOException("Ungültiges YAML in " + pfad, e);
        }
        return Optional.of(yaml);
    }

    public void speichern(YamlConfiguration yaml) throws IOException {
        byte[] inhalt = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(pfad.getParent());
        if (Files.exists(pfad)) {
            // Eine fehlgeschlagene Sicherung darf den bisherigen Spielstand nicht gefährden.
            atomarSchreiben(pfad.resolveSibling(pfad.getFileName() + ".bak"), Files.readAllBytes(pfad));
        }
        atomarSchreiben(pfad, inhalt);
    }

    private static void atomarSchreiben(Path ziel, byte[] inhalt) throws IOException {
        Path temp = Files.createTempFile(ziel.getParent(), ziel.getFileName() + ".", ".tmp");
        try {
            try (var kanal = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var puffer = ByteBuffer.wrap(inhalt);
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
