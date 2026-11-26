package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class PlotUebertragung {
    private final PlotDienst plots;
    private final PlotWelt welt;
    private final WettbewerbDienst wettbewerbe;
    private final PlotAuftraege auftraege;
    private final PlotNachrichten texte;
    private final PlotMenue menue;
    private final Map<PlotId, Angebot> angebote = new HashMap<>();

    PlotUebertragung(PlotDienst plots, PlotWelt welt, WettbewerbDienst wettbewerbe, PlotAuftraege auftraege,
                    PlotNachrichten texte, PlotMenue menue) {
        this.plots = plots;
        this.welt = welt;
        this.wettbewerbe = wettbewerbe;
        this.auftraege = auftraege;
        this.texte = texte;
        this.menue = menue;
    }

    void ausfuehren(Player spieler, String aktion, String[] args) {
        if (!spieler.hasPermission(PlotBerechtigungen.MANAGE)) {
            texte.senden(spieler, "fehler.kein-recht");
            return;
        }
        if (!welt.konfiguration().aktiv("general.funktionen.uebertragen")) {
            texte.senden(spieler, "fehler.deaktiviert");
            return;
        }
        angebote.values().removeIf(angebot -> angebot.bis() <= System.currentTimeMillis());
        if (args.length != 2) {
            texte.senden(spieler, "transfer.hilfe");
            return;
        }
        if (aktion.equals("transfer")) {
            anbieten(spieler, args[1]);
            return;
        }
        PlotId id;
        try {
            id = PlotId.lesen(args[1]);
        } catch (IllegalArgumentException e) {
            texte.senden(spieler, "fehler.plot-id");
            return;
        }
        Angebot angebot = angebote.get(id);
        if (angebot == null || !angebot.ziel().equals(spieler.getUniqueId())) {
            texte.senden(spieler, "transfer.fehlt");
            return;
        }
        if (aktion.equals("decline")) {
            angebote.remove(id);
            texte.senden(spieler, "transfer.abgelehnt");
            return;
        }
        if (!spieler.hasPermission(PlotBerechtigungen.CLAIM)) {
            texte.senden(spieler, "fehler.kein-recht");
            return;
        }
        int limit = welt.konfiguration().claimLimit();
        UUID ziel = spieler.getUniqueId();
        angebote.remove(id);
        auftraege.ausfuehren(spieler, "/plot accept", () -> angebot.bis() <= System.currentTimeMillis()
                ? PlotDienst.Uebertragung.VERAENDERT : plots.uebertragen(angebot.stand(), ziel, limit, wettbewerbe::gesperrt), ergebnis -> {
            texte.senden(spieler, "transfer." + ergebnis.name().toLowerCase(Locale.ROOT).replace('_', '-'), unparsed("id", id.toString()));
            if (ergebnis == PlotDienst.Uebertragung.UEBERTRAGEN) {
                menue.schliessen();
                Player vorher = spieler.getServer().getPlayer(angebot.besitzer());
                if (vorher != null) texte.senden(vorher, "transfer.uebertragen", unparsed("id", id.toString()));
            }
        });
    }

    private void anbieten(Player spieler, String name) {
        var plot = welt.plotBei(spieler.getLocation()).flatMap(plots::finden)
                .filter(wert -> wert.besitzer().equals(spieler.getUniqueId()));
        if (plot.isEmpty()) {
            texte.senden(spieler, "fehler.nicht-besitzer");
            return;
        }
        PlotId id = plot.get().id();
        if (name.equalsIgnoreCase("cancel")) {
            angebote.remove(id);
            texte.senden(spieler, "transfer.abgebrochen");
            return;
        }
        if (plot.get().zensiert() || wettbewerbe.gesperrt(id)) {
            texte.senden(spieler, "transfer.gesperrt");
            return;
        }
        Player ziel = spieler.getServer().getPlayerExact(name);
        if (ziel == null || !spieler.canSee(ziel)) {
            texte.senden(spieler, "transfer.nicht-online");
            return;
        }
        if (ziel.getUniqueId().equals(spieler.getUniqueId())) {
            texte.senden(spieler, "transfer.gleich");
            return;
        }
        int sekunden = welt.konfiguration().zahl("general.uebertragung.gueltigkeit-sekunden");
        angebote.put(id, new Angebot(spieler.getUniqueId(), ziel.getUniqueId(), System.currentTimeMillis() + sekunden * 1000L, plot.get()));
        texte.senden(spieler, "transfer.angeboten", unparsed("id", id.toString()), unparsed("player", ziel.getName()));
        texte.senden(ziel, "transfer.angebot", unparsed("id", id.toString()), unparsed("owner", spieler.getName()),
                unparsed("seconds", Integer.toString(sekunden)));
    }

    java.util.List<String> vorschlaege(Player spieler) {
        long jetzt = System.currentTimeMillis();
        return angebote.entrySet().stream().filter(eintrag -> eintrag.getValue().bis() > jetzt
                && eintrag.getValue().ziel().equals(spieler.getUniqueId())).map(eintrag -> eintrag.getKey().toString()).toList();
    }

    private record Angebot(UUID besitzer, UUID ziel, long bis, Plot stand) { }
}
