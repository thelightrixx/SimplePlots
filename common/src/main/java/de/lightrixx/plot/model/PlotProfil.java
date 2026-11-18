package de.lightrixx.plot.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record PlotProfil(String name, String beschreibung, Optional<Kategorie> kategorie, long gelistetSeit,
                         Optional<Besuchspunkt> besuchspunkt, Set<UUID> likes) {
    public static final int MAX_NAME = 32;
    public static final int MAX_BESCHREIBUNG = 120;

    public PlotProfil {
        name = gueltigerText(name, MAX_NAME);
        beschreibung = gueltigerText(beschreibung, MAX_BESCHREIBUNG);
        Objects.requireNonNull(kategorie, "Kategorie");
        Objects.requireNonNull(besuchspunkt, "Besuchspunkt");
        likes = Set.copyOf(likes);
        if (kategorie.isPresent() ? name.isEmpty() || gelistetSeit <= 0 : gelistetSeit != 0) {
            throw new IllegalArgumentException("Für eine Veröffentlichung braucht der Plot einen Namen und eine Kategorie.");
        }
    }

    public static PlotProfil leer() {
        return new PlotProfil("", "", Optional.empty(), 0, Optional.empty(), Set.of());
    }

    public PlotProfil mitName(String neuerName) {
        if (neuerName.isBlank()) {
            throw new IllegalArgumentException("Plotname fehlt.");
        }
        return new PlotProfil(neuerName, beschreibung, kategorie, gelistetSeit, besuchspunkt, likes);
    }

    public PlotProfil mitBeschreibung(String text) {
        return new PlotProfil(name, text, kategorie, gelistetSeit, besuchspunkt, likes);
    }

    public PlotProfil mitKategorie(Optional<Kategorie> neu, long jetzt) {
        long seit = neu.isEmpty() ? 0 : (kategorie.isPresent() ? gelistetSeit : jetzt);
        return new PlotProfil(name, beschreibung, neu, seit, besuchspunkt, likes);
    }

    public PlotProfil mitBesuchspunkt(Optional<Besuchspunkt> punkt) {
        return new PlotProfil(name, beschreibung, kategorie, gelistetSeit, punkt, likes);
    }

    public PlotProfil mitLike(UUID spieler, boolean vergeben) {
        var neu = new HashSet<>(likes);
        if (vergeben) {
            neu.add(spieler);
        } else {
            neu.remove(spieler);
        }
        return new PlotProfil(name, beschreibung, kategorie, gelistetSeit, besuchspunkt, neu);
    }

    private static String gueltigerText(String text, int maximum) {
        String wert = text.strip();
        if (wert.codePointCount(0, wert.length()) > maximum || wert.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Ungültige Länge oder unzulässige Zeichen im Plot-Profil.");
        }
        return wert;
    }

    public enum Kategorie {
        BUILD, SHOP, MUSEUM, PARKOUR, FARM
    }

    public record Besuchspunkt(int x, int y, int z, float yaw) {
        public Besuchspunkt {
            if (!Float.isFinite(yaw)) {
                throw new IllegalArgumentException("Ungültige Blickrichtung.");
            }
        }
    }
}
