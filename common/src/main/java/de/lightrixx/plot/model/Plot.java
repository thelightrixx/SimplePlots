package de.lightrixx.plot.model;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Plot(PlotId id, UUID besitzer, PlotRechte rechte, PlotProfil profil, Set<PlotId> zellen) {
    public Plot {
        Objects.requireNonNull(id, "Plot-ID");
        Objects.requireNonNull(besitzer, "Besitzer");
        Objects.requireNonNull(rechte, "Plot-Rechte");
        Objects.requireNonNull(profil, "Plot-Profil");
        if (rechte.enthaelt(besitzer) || profil.likes().contains(besitzer)) {
            throw new IllegalArgumentException("Der Besitzer darf keine zusätzliche Plot-Rolle oder Stimme haben.");
        }
        zellen = Set.copyOf(zellen);
        if (!zellen.contains(id) || !zusammenhaengend(id, zellen)) {
            throw new IllegalArgumentException("Die Plotzellen müssen zusammenhängen und die Haupt-ID enthalten.");
        }
    }

    public Plot(PlotId id, UUID besitzer, Set<UUID> trusted, Set<UUID> gebannt) {
        this(id, besitzer, new PlotRechte(trusted, Set.of(), Set.of(), gebannt, Set.of()), PlotProfil.leer(), Set.of(id));
    }

    public static Plot neu(PlotId id, UUID besitzer) {
        return new Plot(id, besitzer, Set.of(), Set.of());
    }

    public boolean darfBauen(UUID spieler) {
        return besitzer.equals(spieler) || rechte.trusted().contains(spieler);
    }

    public boolean darfBauen(UUID spieler, boolean besitzerOnline) {
        return darfBauen(spieler) || (besitzerOnline && rechte.bauhelfer().contains(spieler));
    }

    public boolean darfLagerNutzen(UUID spieler, boolean besitzerOnline) {
        return darfBauen(spieler, besitzerOnline) || rechte.lager().contains(spieler);
    }

    public boolean darfBetreten(UUID spieler) {
        return !rechte.gebannt().contains(spieler);
    }

    public Plot mitRolle(UUID spieler, RollenAktion aktion) {
        if (besitzer.equals(spieler)) {
            throw new IllegalArgumentException("Die Rolle des Besitzers steht fest.");
        }

        return mitRechten(rechte.mitRolle(spieler, aktion));
    }

    public Plot mitRechten(PlotRechte neu) {
        return new Plot(id, besitzer, neu, profil, zellen);
    }

    public Plot mitProfil(PlotProfil neu) {
        return new Plot(id, besitzer, rechte, neu, zellen);
    }

    public Plot vereinigtMit(Plot nachbar) {
        if (!besitzer.equals(nachbar.besitzer())) {
            throw new IllegalArgumentException("Nur Plots desselben Besitzers dürfen zusammengelegt werden.");
        }
        var flaeche = new HashSet<>(zellen);
        flaeche.addAll(nachbar.zellen);
        return new Plot(id, besitzer, rechte.vereinigtMit(nachbar.rechte), profil, flaeche);
    }

    public PlotId nordrand() {
        return zellen.stream().min(Comparator.comparingInt(PlotId::z).thenComparingInt(PlotId::x)).orElseThrow();
    }

    private static boolean zusammenhaengend(PlotId start, Set<PlotId> zellen) {
        if (zellen.size() == 1) {
            return true;
        }
        var verbleibend = new HashSet<>(zellen);
        var offen = new ArrayDeque<PlotId>();
        offen.add(start);
        verbleibend.remove(start);
        while (!offen.isEmpty()) {
            for (PlotId nachbar : offen.removeFirst().nachbarn()) {
                if (verbleibend.remove(nachbar)) {
                    offen.add(nachbar);
                }
            }
        }
        return verbleibend.isEmpty();
    }

    public enum RollenAktion {
        TRUST, UNTRUST, ADD, UNADD, STORAGE, UNSTORE, DENY, UNDENY
    }
}
