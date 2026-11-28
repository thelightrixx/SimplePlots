package de.lightrixx.plot.util;

import java.util.ArrayList;
import java.util.List;

public final class StartBanner {

    private static final String AKZENT = "\u001B[38;2;103;214;160m";
    private static final String WEISS = "\u001B[97m";
    private static final String GRAU = "\u001B[90m";
    private static final String RESET = "\u001B[0m";

    private static final String[] LOGO = {
            " ____  ____  ",
            "/ ___||  _ \\ ",
            "\\___ \\| |_) |",
            " ___) |  __/ ",
            "|____/|_|    "
    };

    private StartBanner() {
    }

    public static List<String> zeilen(String version, String plattform) {
        String saubereVersion = version == null || version.isBlank() ? "unbekannt" : version.trim();
        if (!saubereVersion.equals("unbekannt") && !saubereVersion.startsWith("v") && !saubereVersion.startsWith("V")) {
            saubereVersion = "v" + saubereVersion;
        }
        String sauberePlattform = plattform == null || plattform.isBlank() ? "unbekannt" : plattform.trim();
        int breite = 0;
        for (String zeile : LOGO) {
            breite = Math.max(breite, zeile.length());
        }
        breite += 3;
        List<String> ergebnis = new ArrayList<>();
        for (int i = 0; i < LOGO.length; i++) {
            String logoTeil = AKZENT + auffuellen(LOGO[i], breite) + RESET;
            String infoTeil = "";
            if (i == 2) {
                infoTeil = AKZENT + "SimplePlots " + saubereVersion + GRAU + " by lightrixx" + RESET;
            } else if (i == 3) {
                infoTeil = WEISS + "Running on " + sauberePlattform + RESET;
            }
            ergebnis.add(logoTeil + infoTeil);
        }
        ergebnis.add("");
        return List.copyOf(ergebnis);
    }

    private static String auffuellen(String text, int breite) {
        StringBuilder builder = new StringBuilder(text);
        while (builder.length() < breite) {
            builder.append(' ');
        }
        return builder.toString();
    }
}
