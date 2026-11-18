package de.lightrixx.plot.paper.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class YamlDatei {
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
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IOException("Ungültiges YAML in " + pfad, e);
        }
        return Optional.of(yaml);
    }

    public void speichern(YamlConfiguration yaml) throws IOException {
        Files.createDirectories(pfad.getParent());
        Path temp = Files.createTempFile(pfad.getParent(), pfad.getFileName() + ".", ".tmp");
        try {
            Files.writeString(temp, yaml.saveToString());
            Files.move(temp, pfad, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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
