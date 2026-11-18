package de.lightrixx.plot.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record PlotRechte(Set<UUID> trusted, Set<UUID> bauhelfer, Set<UUID> lager, Set<UUID> gebannt,
                         Set<BesucherRecht> besucher) {
    public PlotRechte {
        trusted = Set.copyOf(trusted);
        bauhelfer = Set.copyOf(bauhelfer);
        lager = Set.copyOf(lager);
        gebannt = Set.copyOf(gebannt);
        besucher = Set.copyOf(besucher);
        for (UUID spieler : gebannt) {
            if (trusted.contains(spieler) || bauhelfer.contains(spieler) || lager.contains(spieler)) {
                throw new IllegalArgumentException("Gebannte Spieler dürfen keine Plot-Rechte haben.");
            }
        }
        if (bauhelfer.stream().anyMatch(trusted::contains)) {
            throw new IllegalArgumentException("Trusted und Bauhelfer dürfen sich nicht überschneiden.");
        }
    }

    boolean enthaelt(UUID spieler) {
        return trusted.contains(spieler) || bauhelfer.contains(spieler) || lager.contains(spieler) || gebannt.contains(spieler);
    }

    PlotRechte mitRolle(UUID spieler, Plot.RollenAktion aktion) {
        var vertraute = new HashSet<>(trusted);
        var helfer = new HashSet<>(bauhelfer);
        var lagernutzer = new HashSet<>(lager);
        var ausgeschlossen = new HashSet<>(gebannt);
        switch (aktion) {
            case TRUST -> {
                ausgeschlossen.remove(spieler);
                helfer.remove(spieler);
                vertraute.add(spieler);
            }
            case UNTRUST -> vertraute.remove(spieler);
            case ADD -> {
                if (!vertraute.contains(spieler) && !ausgeschlossen.contains(spieler)) {
                    helfer.add(spieler);
                }
            }
            case UNADD -> helfer.remove(spieler);
            case STORAGE -> {
                if (!ausgeschlossen.contains(spieler)) {
                    lagernutzer.add(spieler);
                }
            }
            case UNSTORE -> lagernutzer.remove(spieler);
            case DENY -> {
                vertraute.remove(spieler);
                helfer.remove(spieler);
                lagernutzer.remove(spieler);
                ausgeschlossen.add(spieler);
            }
            case UNDENY -> ausgeschlossen.remove(spieler);
        }
        return new PlotRechte(vertraute, helfer, lagernutzer, ausgeschlossen, besucher);
    }

    public PlotRechte mitBesucherRecht(BesucherRecht recht, boolean erlaubt) {
        var neu = new HashSet<>(besucher);
        if (erlaubt) {
            neu.add(recht);
        } else {
            neu.remove(recht);
        }
        return new PlotRechte(trusted, bauhelfer, lager, gebannt, neu);
    }

    public PlotRechte vereinigtMit(PlotRechte andere) {
        var ausgeschlossen = new HashSet<>(gebannt);
        ausgeschlossen.addAll(andere.gebannt);
        return new PlotRechte(gemeinsame(trusted, andere.trusted), gemeinsame(bauhelfer, andere.bauhelfer),
                gemeinsame(lager, andere.lager), ausgeschlossen, gemeinsame(besucher, andere.besucher));
    }

    private static <T> Set<T> gemeinsame(Set<T> a, Set<T> b) {
        var gemeinsam = new HashSet<>(a);
        gemeinsam.retainAll(b);
        return gemeinsam;
    }

    public enum BesucherRecht {
        DOORS, SWITCHES
    }
}
