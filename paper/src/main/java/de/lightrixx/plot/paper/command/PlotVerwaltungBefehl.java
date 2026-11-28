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
import org.bukkit.NamespacedKey;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class PlotVerwaltungBefehl {
    private final PlotDienst plots;
    private final PlotWelt welt;
    private final PlotNachrichten texte;
    private final WettbewerbDienst wettbewerbe;
    private final PlotAuftraege auftraege;

    PlotVerwaltungBefehl(PlotDienst plots, PlotWelt welt, PlotNachrichten texte, WettbewerbDienst wettbewerbe,
                         PlotAuftraege auftraege) {
        this.plots = plots;
        this.welt = welt;
        this.texte = texte;
        this.wettbewerbe = wettbewerbe;
        this.auftraege = auftraege;
    }

    void ausfuehren(Player spieler, PlotId id, String aktion, String[] args) {
        if ((aktion.equals("merge") || aktion.equals("unlink")) && !welt.konfiguration().aktiv("general.funktionen.zusammenlegen")) {
            texte.senden(spieler, "fehler.deaktiviert");
            return;
        }
        switch (aktion) {
            case "profile" -> profil(spieler, id, args);
            case "sethome" -> setHome(spieler, id, args);
            case "flag" -> flag(spieler, id, args);
            case "like", "unlike" -> {
                boolean vergeben = aktion.equals("like");
                UUID spielerId = spieler.getUniqueId();
                auftraege.ausfuehren(spieler, "/simpleplots " + aktion, () -> plots.like(id, spielerId, vergeben),
                        ergebnis -> aenderungSenden(spieler, ergebnis, "profil." + aktion));
            }
            case "merge" -> zusammenlegen(spieler, id, args);
            case "unlink" -> aufteilen(spieler, id, args);
            case "biome" -> biom(spieler, id, args);
            default -> throw new IllegalArgumentException("Unbekannter Plot-Unterbefehl " + aktion);
        }
    }

    private void profil(Player spieler, PlotId id, String[] args) {
        if (args.length < 3) {
            texte.senden(spieler, "profil.hilfe");
            return;
        }
        String feld = args[1].toLowerCase(Locale.ROOT);
        String wert = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        int laenge = wert.codePointCount(0, wert.length());
        if ((feld.equals("name") && laenge > welt.konfiguration().zahl("general.profil.name-laenge"))
                || (feld.equals("description") && laenge > welt.konfiguration().zahl("general.profil.beschreibung-laenge"))) {
            texte.senden(spieler, "profil.ungueltig");
            return;
        }
        UnaryOperator<PlotProfil> aenderung;
        try {
            aenderung = switch (feld) {
                case "name" -> profil -> profil.mitName(wert);
                case "description" -> profil -> profil.mitBeschreibung(wert.equals("-") ? "" : wert);
                case "category" -> {
                    Optional<PlotProfil.Kategorie> kategorie = wert.equalsIgnoreCase("off") ? Optional.empty()
                            : Optional.of(PlotProfil.Kategorie.valueOf(wert.toUpperCase(Locale.ROOT)));
                    long jetzt = System.currentTimeMillis();
                    yield profil -> profil.mitKategorie(kategorie, jetzt);
                }
                default -> throw new IllegalArgumentException("Ungültiges Profilfeld.");
            };
            plots.finden(id).ifPresent(plot -> aenderung.apply(plot.profil()));
        } catch (IllegalArgumentException e) {
            texte.senden(spieler, "profil.ungueltig");
            return;
        }
        UUID besitzer = spieler.getUniqueId();
        auftraege.ausfuehren(spieler, "/simpleplots profile", () -> plots.profilAendern(id, besitzer, aenderung),
                ergebnis -> aenderungSenden(spieler, ergebnis, "profil.gespeichert"));
    }

    private void setHome(Player spieler, PlotId id, String[] args) {
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
        UUID besitzer = spieler.getUniqueId();
        auftraege.ausfuehren(spieler, "/simpleplots sethome",
                () -> plots.profilAendern(id, besitzer, profil -> profil.mitBesuchspunkt(punkt)),
                ergebnis -> aenderungSenden(spieler, ergebnis, punkt.isEmpty() ? "profil.ort-entfernt" : "profil.ort-gesetzt"));
    }

    private void flag(Player spieler, PlotId id, String[] args) {
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
        UUID besitzer = spieler.getUniqueId();
        auftraege.ausfuehren(spieler, "/simpleplots flag", () -> plots.besucherRecht(id, besitzer, flag, erlaubt),
                ergebnis -> aenderungSenden(spieler, ergebnis, erlaubt ? "rollen.flag-an" : "rollen.flag-aus"));
    }

    private void zusammenlegen(Player spieler, PlotId id, String[] args) {
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
        UUID besitzer = spieler.getUniqueId();
        boolean bestaetigt = args.length == 3;
        var plotwelt = spieler.getWorld();
        auftraege.ausfuehren(spieler, "/simpleplots merge",
                () -> plots.zusammenlegen(id, nachbar, besitzer, wettbewerbe::gesperrt, bestaetigt), ergebnis -> {
            texte.senden(spieler, "merge." + ergebnis.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                    unparsed("target", nachbar.toString()), unparsed("source", id.toString()));
            if (ergebnis == PlotDienst.MergeErgebnis.VEREINIGT) {
                var plot = plots.finden(id).orElseThrow();
                welt.wegrandAktualisieren(plotwelt, plot);
                for (Player gast : plotwelt.getPlayers()) {
                    if (!PlotBerechtigungen.darfBetreten(gast, plot) && welt.plotBei(gast.getLocation()).filter(id::equals).isPresent()
                            && gast.teleport(welt.wegVor(gast.getWorld(), id))) {
                        texte.senden(gast, "zugang.gebannt");
                    }
                }
            }
        });
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
            case GESPERRT -> "zensur.gesperrt";
        });
    }

    private void aufteilen(Player spieler, PlotId id, String[] args) {
        if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("confirm"))) {
            texte.senden(spieler, "trennen.hilfe");
            return;
        }
        UUID besitzer = spieler.getUniqueId();
        var vorher = plots.finden(id);
        var plotwelt = spieler.getWorld();
        boolean bestaetigt = args.length == 2;
        auftraege.ausfuehren(spieler, "/simpleplots unlink",
                () -> plots.aufteilen(id, besitzer, wettbewerbe::gesperrt, bestaetigt), ergebnis -> {
            texte.senden(spieler, "trennen." + ergebnis.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            if (ergebnis == PlotDienst.TrennErgebnis.GETRENNT) {
                vorher.ifPresent(plot -> welt.wegrandAktualisieren(plotwelt, plot));
            }
        });
    }

    private void biom(Player spieler, PlotId id, String[] args) {
        if (!welt.konfiguration().aktiv("general.funktionen.biome")) {
            texte.senden(spieler, "fehler.deaktiviert");
            return;
        }
        if (args.length != 2) {
            texte.senden(spieler, "biom.hilfe");
            return;
        }
        Optional<String> biom = Optional.empty();
        if (!args[1].equalsIgnoreCase("off")) {
            var key = NamespacedKey.fromString(args[1].toLowerCase(Locale.ROOT));
            if (key == null || RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key) == null) {
                texte.senden(spieler, "biom.ungueltig");
                return;
            }
            biom = Optional.of(key.toString());
        }
        var ziel = biom;
        UUID besitzer = spieler.getUniqueId();
        var plotwelt = spieler.getWorld();
        auftraege.ausfuehren(spieler, "/simpleplots biome", () -> plots.finden(id).filter(plot -> wettbewerbe.gesperrt(plot.id())).isPresent()
                ? PlotDienst.Aenderung.GESPERRT : plots.profilAendern(id, besitzer, profil -> profil.mitBiom(ziel)), ergebnis -> {
            if (ergebnis == PlotDienst.Aenderung.GESPERRT) texte.senden(spieler, "biom.gesperrt");
            else aenderungSenden(spieler, ergebnis, "biom.gespeichert");
            if (ergebnis == PlotDienst.Aenderung.GEAENDERT) plots.finden(id).ifPresent(plot -> welt.wegrandAktualisieren(plotwelt, plot));
        });
    }
}
