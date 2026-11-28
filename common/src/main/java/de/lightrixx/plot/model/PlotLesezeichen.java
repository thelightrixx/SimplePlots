package de.lightrixx.plot.model;

import java.nio.charset.StandardCharsets;
import java.util.*;

public record PlotLesezeichen(String name, UUID welt, PlotId zelle, UUID besitzer) {
    public PlotLesezeichen {
        if (name == null || !name.matches("[\\p{L}\\p{N} _-]{1,32}") || name.isBlank() || !name.equals(name.strip())) throw new IllegalArgumentException("Ein Name braucht 1–32 Buchstaben, Zahlen, Leerzeichen, _ oder -.");
        Objects.requireNonNull(welt); Objects.requireNonNull(zelle); Objects.requireNonNull(besitzer);
    }
    public String schluessel() { return name.toLowerCase(Locale.ROOT); }
    public static List<PlotLesezeichen> lesen(String text) {
        if (text == null) return List.of();
        if (text.length() > 32768 || !text.startsWith("1\n")) throw new IllegalArgumentException("Ungültiges Lesezeichenformat.");
        var result = new ArrayList<PlotLesezeichen>(); var namen = new HashSet<String>();
        for (String row : text.substring(2).split("\n")) {
            if (row.isEmpty()) continue;
            String[] values = row.split("\\|", -1);
            if (values.length != 4 || result.size() >= 100) throw new IllegalArgumentException("Ungültiger Lesezeichenbestand.");
            var bookmark = new PlotLesezeichen(new String(Base64.getUrlDecoder().decode(values[0]), StandardCharsets.UTF_8), uuid(values[1]), PlotId.lesen(values[2]), uuid(values[3]));
            if (!namen.add(bookmark.schluessel())) throw new IllegalArgumentException("Doppelter Lesezeichenname.");
            result.add(bookmark);
        }
        return List.copyOf(result);
    }
    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Ungültige Welt- oder Besitzerkennung.");
        return id;
    }
    public static String schreiben(Collection<PlotLesezeichen> bookmarks) {
        if (bookmarks.size() > 100 || bookmarks.stream().map(PlotLesezeichen::schluessel).distinct().count() != bookmarks.size()) throw new IllegalArgumentException("Ungültiger Lesezeichenbestand.");
        var out = new StringBuilder("1\n");
        for (var item : bookmarks) out.append(Base64.getUrlEncoder().withoutPadding().encodeToString(item.name.getBytes(StandardCharsets.UTF_8)))
                .append('|').append(item.welt).append('|').append(item.zelle).append('|').append(item.besitzer).append('\n');
        if (out.length() > 32768) throw new IllegalArgumentException("Lesezeichenbestand zu groß.");
        return out.toString();
    }
}
