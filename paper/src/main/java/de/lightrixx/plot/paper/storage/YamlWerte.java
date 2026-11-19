package de.lightrixx.plot.paper.storage;

import java.util.UUID;

final class YamlWerte {
    private YamlWerte() {
    }

    static UUID uuid(String wert) {
        if (wert == null || wert.length() != 36) {
            throw new IllegalArgumentException("Eine UUID muss die vollständige Form mit 36 Zeichen haben.");
        }
        UUID uuid = UUID.fromString(wert);
        if (!uuid.toString().equalsIgnoreCase(wert)) {
            throw new IllegalArgumentException("Ungültige UUID.");
        }
        return uuid;
    }
}
