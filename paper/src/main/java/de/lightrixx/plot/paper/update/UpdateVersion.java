package de.lightrixx.plot.paper.update;

import java.util.List;
import java.util.regex.Pattern;

public record UpdateVersion(int major, int minor, int patch, List<String> vorab) implements Comparable<UpdateVersion> {
    private static final Pattern FORMAT = Pattern.compile("v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*))?");

    public UpdateVersion { vorab = List.copyOf(vorab); }

    public static UpdateVersion lesen(String text) {
        var match = FORMAT.matcher(text);
        if (!match.matches()) {
            throw new IllegalArgumentException("Ungültige SimplePlots-Version: " + text);
        }
        return new UpdateVersion(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)), match.group(4) == null ? List.of() : List.of(match.group(4).split("[.-]")));
    }

    @Override
    public int compareTo(UpdateVersion andere) {
        int vergleich = Integer.compare(major, andere.major);
        if (vergleich == 0) vergleich = Integer.compare(minor, andere.minor);
        if (vergleich == 0) vergleich = Integer.compare(patch, andere.patch);
        if (vergleich != 0) return vergleich;
        if (vorab.isEmpty() || andere.vorab.isEmpty()) {
            return Boolean.compare(vorab.isEmpty(), andere.vorab.isEmpty());
        }
        for (int i = 0; i < Math.min(vorab.size(), andere.vorab.size()); i++) {
            String links = vorab.get(i);
            String rechts = andere.vorab.get(i);
            boolean zahlLinks = links.matches("[0-9]+"), zahlRechts = rechts.matches("[0-9]+");
            vergleich = zahlLinks && zahlRechts ? new java.math.BigInteger(links).compareTo(new java.math.BigInteger(rechts))
                    : zahlLinks != zahlRechts ? (zahlLinks ? -1 : 1) : links.compareTo(rechts);
            if (vergleich != 0) return vergleich;
        }
        return Integer.compare(vorab.size(), andere.vorab.size());
    }
}
