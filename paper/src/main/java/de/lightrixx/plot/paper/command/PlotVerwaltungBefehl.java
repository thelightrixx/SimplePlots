package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRechte.BesucherRecht;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class PlotVerwaltungBefehl {
    private final PlotDienst plots;
    private final PlotWelt welt;
    private final PlotNachrichten texte;
    private final WettbewerbDienst wettbewerbe;

    PlotVerwaltungBefehl(PlotDienst plots, PlotWelt welt, PlotNachrichten texte, WettbewerbDienst wettbewerbe) {
        this.plots = plots;
        this.welt = welt;
        this.texte = texte;
        this.wettbewerbe = wettbewerbe;
    }

    void ausfuehren(Player spieler, PlotId id, String aktion, String[] args) throws IOException {
        switch (aktion) {
            case "profile" -> profil(spieler, id, args);
            case "sethome" -> setHome(spieler, id, args);
            case "flag" -> flag(spieler, id, args);
            case "like", "unlike" -> {
                boolean vergeben = aktion.equals("like");
                aenderungSenden(spieler, plots.like(id, spieler.getUniqueId(), vergeben), "profil." + aktion);
            }
            case "merge" -> zusammenlegen(spieler, id, args);
            default -> throw new IllegalArgumentException("Unbekannter Plot-Unterbefehl " + aktion);
        }
    }

    private void profil(Player spieler, PlotId id, String[] args) throws IOException {
        if (args.length < 3) {
            texte.senden(spieler, "profil.hilfe");
            return;
        }
        String feld = args[1].toLowerCase(Locale.ROOT);
        String wert = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        PlotDienst.Aenderung ergebnis;
        try {
            ergebnis = switch (feld) {
                case "name" -> plots.profilAendern(id, spieler.getUniqueId(), profil -> profil.mitName(wert));
                case "description" -> plots.profilAendern(id, spieler.getUniqueId(),
                        profil -> profil.mitBeschreibung(wert.equals("-") ? "" : wert));
                case "category" -> {
                    Optional<PlotProfil.Kategorie> kategorie = wert.equalsIgnoreCase("off") ? Optional.empty()
                            : Optional.of(PlotProfil.Kategorie.valueOf(wert.toUpperCase(Locale.ROOT)));
                    long jetzt = System.currentTimeMillis();
                    yield plots.profilAendern(id, spieler.getUniqueId(), profil -> profil.mitKategorie(kategorie, jetzt));
                }
                default -> throw new IllegalArgumentException("Ungültiges Profilfeld.");
            };
        } catch (IllegalArgumentException e) {
            texte.senden(spieler, "profil.ungueltig");
            return;
        }
        aenderungSenden(spieler, ergebnis, "profil.gespeichert");
    }

    private void setHome(Player spieler, PlotId id, String[] args) throws IOException {
        Optional<PlotProfil.Besuchspunkt> punkt;
        if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
            punkt = Optional.empty();
        } else if (args.length == 1) {
            var ort = spieler.getLocation();
            var standort = new PlotProfil.Besuchspunkt(ort.getBlockX(), ort.getBlockY(), ort.getBlockZ(), ort.getYaw());
            if (welt.besuchspunkt(spieler.getWorld(), id, standort).isEmpty()) {
                texte.senden(spieler, "profil.ort-unsicher");
                return;
            }
            punkt = Optional.of(standort);
        } else {
            texte.senden(spieler, "profil.hilfe");
            return;
        }
        aenderungSenden(spieler, plots.profilAendern(id, spieler.getUniqueId(), profil -> profil.mitBesuchspunkt(punkt)),
                punkt.isEmpty() ? "profil.ort-entfernt" : "profil.ort-gesetzt");
    }

    private void flag(Player spieler, PlotId id, String[] args) throws IOException {
        if (args.length != 3 || !(args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("off"))) {
            texte.senden(spieler, "rollen.flag-hilfe");
            return;
        }
        BesucherRecht flag;
        try {
            flag = BesucherRecht.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            texte.senden(spieler, "rollen.flag-hilfe");
            return;
        }
        boolean erlaubt = args[2].equalsIgnoreCase("on");
        aenderungSenden(spieler, plots.besucherRecht(id, spieler.getUniqueId(), flag, erlaubt),
                erlaubt ? "rollen.flag-an" : "rollen.flag-aus");
    }

    private void zusammenlegen(Player spieler, PlotId id, String[] args) throws IOException {
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equalsIgnoreCase("confirm"))) {
            texte.senden(spieler, "merge.hilfe");
            return;
        }
        PlotId nachbar;
        try {
            nachbar = PlotId.lesen(args[1]);
        } catch (IllegalArgumentException e) {
            texte.senden(spieler, "fehler.plot-id");
            return;
        }
        var ergebnis = plots.zusammenlegen(id, nachbar, spieler.getUniqueId(), wettbewerbe::gesperrt, args.length == 3);
        texte.senden(spieler, "merge." + ergebnis.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                unparsed("target", nachbar.toString()), unparsed("source", id.toString()));
        if (ergebnis == PlotDienst.MergeErgebnis.VEREINIGT) {
            var plot = plots.finden(id).orElseThrow();
            welt.wegrandAktualisieren(spieler.getWorld(), plot);
            for (Player gast : spieler.getWorld().getPlayers()) {
                if (!PlotBerechtigungen.darfBetreten(gast, plot) && welt.plotBei(gast.getLocation()).filter(id::equals).isPresent()
                        && gast.teleport(welt.wegVor(gast.getWorld(), id))) {
                    texte.senden(gast, "zugang.gebannt");
                }
            }
        }
    }

    private void aenderungSenden(Player spieler, PlotDienst.Aenderung ergebnis, String erfolg) {
        texte.senden(spieler, switch (ergebnis) {
            case GEAENDERT -> erfolg;
            case UNVERAENDERT -> "rollen.unveraendert";
            case FREMDER_PLOT -> "fehler.nicht-besitzer";
            case NAME_VERGEBEN -> "profil.name-vergeben";
            case NICHT_GELISTET -> "profil.nicht-gelistet";
            case EIGENER_PLOT -> "profil.eigener-plot";
            case GEBANNT -> "zugang.gebannt";
        });
    }
}
