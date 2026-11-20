package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.BauWettbewerb;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component;
import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class PlotWettbewerbBefehl {
    static final List<String> SPIELERBEFEHLE = List.of("status", "entries", "results");
    static final List<String> ADMINBEFEHLE = List.of("start", "voting", "finish", "cancel");
    private static final int ERGEBNISSE_PRO_SEITE = 8;
    private final WettbewerbDienst wettbewerbe;
    private final PlotDienst plots;
    private final PlotMenue menue;
    private final PlotNachrichten texte;
    private final PlotAuftraege auftraege;
    private final de.lightrixx.plot.paper.config.PlotKonfiguration konfiguration;

    PlotWettbewerbBefehl(WettbewerbDienst wettbewerbe, PlotDienst plots, PlotMenue menue, PlotNachrichten texte,
                         PlotAuftraege auftraege, de.lightrixx.plot.paper.config.PlotKonfiguration konfiguration) {
        this.wettbewerbe = wettbewerbe;
        this.plots = plots;
        this.menue = menue;
        this.texte = texte;
        this.auftraege = auftraege;
        this.konfiguration = konfiguration;
    }

    void ausfuehren(CommandSender sender, String[] args) {
        if (!konfiguration.aktiv("contests.aktiviert")) { texte.senden(sender, "fehler.deaktiviert"); return; }
        String aktion = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        if (ADMINBEFEHLE.contains(aktion)) {
            verwalten(sender, aktion, args);
            return;
        }
        switch (aktion) {
            case "entries" -> {
                if (sender instanceof Player spieler) {
                    menue.wettbewerb(spieler);
                } else {
                    texte.senden(sender, "fehler.nur-spieler");
                }
            }
            case "results" -> ergebnisse(sender, args);
            default -> status(sender);
        }
    }

    void einreichen(Player spieler, PlotId plot) {
        if (!konfiguration.aktiv("contests.aktiviert")) { texte.senden(spieler, "fehler.deaktiviert"); return; }
        UUID id = spieler.getUniqueId();
        int limit = konfiguration.zahl("contests.maximale-beitraege");
        auftraege.ausfuehren(spieler, "/plot submit", () -> wettbewerbe.einreichen(plot, id, limit),
                ergebnis -> senden(spieler, "submit", ergebnis));
    }

    void zurueckziehen(Player spieler) {
        UUID id = spieler.getUniqueId();
        auftraege.ausfuehren(spieler, "/plot withdraw", () -> wettbewerbe.zurueckziehen(id),
                ergebnis -> senden(spieler, "withdraw", ergebnis));
    }

    void abstimmen(Player spieler, PlotId plot) {
        if (!konfiguration.aktiv("contests.aktiviert")) { texte.senden(spieler, "fehler.deaktiviert"); return; }
        UUID id = spieler.getUniqueId();
        auftraege.ausfuehren(spieler, "/plot vote", () -> wettbewerbe.abstimmen(plot, id),
                ergebnis -> senden(spieler, "vote", ergebnis));
    }

    private void verwalten(CommandSender sender, String aktion, String[] args) {
        if (!sender.hasPermission("simpleplots.admin.contest")) {
            texte.senden(sender, "fehler.kein-recht");
            return;
        }
        if (aktion.equals("start") ? args.length < 3 : args.length != 2) {
            texte.senden(sender, "wettbewerb.admin-hilfe");
            return;
        }
        if (aktion.equals("start")) {
            String thema = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            try {
                new BauWettbewerb(thema, BauWettbewerb.Phase.BAUEN, java.util.Map.of(), java.util.Map.of());
            } catch (IllegalArgumentException e) {
                texte.senden(sender, "wettbewerb.thema-ungueltig");
                return;
            }
            auftraege.ausfuehren(sender, "/plot contest start", () -> wettbewerbe.starten(thema),
                    ergebnis -> senden(sender, aktion, ergebnis));
            return;
        }
        auftraege.ausfuehren(sender, "/plot contest " + aktion, () -> switch (aktion) {
            case "voting" -> wettbewerbe.abstimmungOeffnen();
            case "finish" -> wettbewerbe.beenden();
            case "cancel" -> wettbewerbe.abbrechen();
            default -> throw new IllegalArgumentException("Unbekannte Wettbewerbsaktion " + aktion);
        }, ergebnis -> senden(sender, aktion, ergebnis));
    }

    private void status(CommandSender sender) {
        wettbewerbe.aktuell().ifPresentOrElse(wert -> texte.senden(sender, "wettbewerb.status",
                        unparsed("theme", wert.thema()), unparsed("count", Long.toString(wert.beitraege().keySet().stream()
                                .filter(id -> plots.finden(id).filter(plot -> !plot.zensiert()).isPresent()).count())),
                        component("phase", texte.komponente("wettbewerb.phase." + wert.phase().name().toLowerCase(Locale.ROOT)))),
                () -> texte.senden(sender, "wettbewerb.keiner"));
        texte.senden(sender, "wettbewerb.hilfe");
        if (sender.hasPermission("simpleplots.admin.contest")) {
            texte.senden(sender, "wettbewerb.admin-hilfe");
        }
    }

    private void ergebnisse(CommandSender sender, String[] args) {
        var wettbewerb = wettbewerbe.aktuell().filter(wert -> wert.phase() == BauWettbewerb.Phase.BEENDET);
        if (wettbewerb.isEmpty()) {
            texte.senden(sender, "wettbewerb.ergebnis-fehlt");
            return;
        }
        var platzierungen = wettbewerb.get().ergebnisse().stream()
                .filter(platz -> plots.finden(platz.plot()).filter(plot -> !plot.zensiert()).isPresent()).toList();
        int seiten = (platzierungen.size() - 1) / ERGEBNISSE_PRO_SEITE + 1;
        int seite;
        try {
            seite = args.length == 3 ? Integer.parseInt(args[2]) : 1;
            if (args.length > 3 || seite < 1 || seite > seiten) {
                throw new IllegalArgumentException("Ergebnisseite außerhalb des gültigen Bereichs.");
            }
        } catch (IllegalArgumentException e) {
            texte.senden(sender, "wettbewerb.seite-ungueltig", unparsed("max", Integer.toString(seiten)));
            return;
        }
        texte.senden(sender, "wettbewerb.ergebnisse", unparsed("theme", wettbewerb.get().thema()),
                unparsed("page", Integer.toString(seite)), unparsed("max", Integer.toString(seiten)));
        for (int i = (seite - 1) * ERGEBNISSE_PRO_SEITE; i < Math.min(platzierungen.size(), seite * ERGEBNISSE_PRO_SEITE); i++) {
            var platz = platzierungen.get(i);
            String name = plots.finden(platz.plot()).map(plot -> plot.profil().name()).filter(text -> !text.isEmpty())
                    .orElse(platz.plot().toString());
            texte.senden(sender, "wettbewerb.ergebnis", unparsed("plot", name), unparsed("id", platz.plot().toString()),
                    unparsed("count", Integer.toString(platz.stimmen())));
        }
    }

    private void senden(CommandSender sender, String aktion, WettbewerbDienst.Ergebnis ergebnis) {
        String schluessel = ergebnis == WettbewerbDienst.Ergebnis.ERFOLG ? aktion
                : ergebnis.name().toLowerCase(Locale.ROOT).replace('_', '-');
        texte.senden(sender, "wettbewerb." + schluessel);
    }
}
