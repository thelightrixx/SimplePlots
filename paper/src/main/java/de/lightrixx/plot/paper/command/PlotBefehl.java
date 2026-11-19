package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil.Kategorie;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

public final class PlotBefehl implements TabExecutor {
    private static final String USE = PlotBerechtigungen.USE;
    private static final String CLAIM = PlotBerechtigungen.CLAIM;
    private static final String MANAGE = PlotBerechtigungen.MANAGE;
    private static final List<String> OHNE_ARGUMENTE = List.of("world", "back", "claim", "auto", "list", "info", "menu",
            "like", "unlike", "submit", "vote", "withdraw");
    private static final List<String> BESUCHSBEFEHLE = List.of("help", "world", "back", "home", "visit", "list", "info",
            "menu", "discover", "like", "unlike", "contest", "vote", "withdraw");
    private static final List<String> ROLLENBEFEHLE = List.of("trust", "untrust", "deny", "undeny", "kick", "ban", "unban",
            "add", "unadd", "storage", "unstore");
    private static final List<String> VERWALTUNG = List.of("profile", "sethome", "flag", "merge", "submit");
    private final Server server;
    private final Logger log;
    private final PlotDienst plots;
    private final PlotWelt welt;
    private final PlotKonfiguration konfiguration;
    private final PlotNachrichten texte;
    private final PlotAdminBefehl admin;
    private final PlotMenue menue;
    private final PlotVerwaltungBefehl verwaltung;
    private final PlotWettbewerbBefehl wettbewerb;
    private final PlotAuftraege auftraege;

    public PlotBefehl(Server server, Logger log, PlotDienst plots, PlotWelt welt,
                      PlotKonfiguration konfiguration, PlotNachrichten texte, PlotMenue menue, WettbewerbDienst wettbewerbe,
                      PlotAuftraege auftraege, PlotBetrieb betrieb) {
        this.server = server;
        this.log = log;
        this.plots = plots;
        this.welt = welt;
        this.konfiguration = konfiguration;
        this.texte = texte;
        this.auftraege = auftraege;
        this.admin = new PlotAdminBefehl(konfiguration, welt, texte, betrieb);
        this.menue = menue;
        this.verwaltung = new PlotVerwaltungBefehl(plots, welt, texte, wettbewerbe, auftraege);
        this.wettbewerb = new PlotWettbewerbBefehl(wettbewerbe, plots, menue, texte, auftraege);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(USE)) {
            texte.senden(sender, "fehler.kein-recht");
            return true;
        }
        String aktion = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            if (aktion.equals("help")) {
                hilfe(sender);
            } else if (aktion.equals("admin")) {
                admin.ausfuehren(sender, args);
            } else if (aktion.equals("contest")) {
                wettbewerb.ausfuehren(sender, args);
            } else if (sender instanceof Player spieler) {
                spielerbefehl(spieler, aktion, args);
            } else {
                texte.senden(sender, "fehler.nur-spieler");
            }
        } catch (IOException e) {
            log.error("/plot {} für {} fehlgeschlagen.", aktion, sender.getName(), e);
            texte.senden(sender, "fehler.verarbeitung");
        }
        return true;
    }

    private void spielerbefehl(Player spieler, String aktion, String[] args) throws IOException {
        if (OHNE_ARGUMENTE.contains(aktion) && args.length != 1) {
            texte.senden(spieler, "fehler.syntax", unparsed("command", aktion));
            return;
        }
        switch (aktion) {
            case "world" -> {
                if (plotModus(spieler)) {
                    teleport(spieler, welt.spawn(welt.ladenOderErstellen()), "teleport.welt");
                }
            }
            case "back" -> {
                if (welt.istPlotWelt(spieler.getWorld())) {
                    teleport(spieler, welt.rueckkehr(), "teleport.zurueck");
                } else {
                    texte.senden(spieler, "fehler.keine-plotwelt");
                }
            }
            case "claim", "auto" -> claim(spieler, aktion.equals("auto"));
            case "home" -> home(spieler, args);
            case "visit" -> besuchen(spieler, args);
            case "list" -> liste(spieler);
            case "info" -> info(spieler);
            case "menu" -> menue.oeffnen(spieler);
            case "discover" -> entdecken(spieler, args);
            case "profile", "flag", "sethome", "merge", "like", "unlike", "submit", "vote" -> amPlot(spieler, aktion, args);
            case "withdraw" -> wettbewerb.zurueckziehen(spieler);
            case "ban" -> rolle(spieler, "deny", args);
            case "unban" -> rolle(spieler, "undeny", args);
            case "trust", "untrust", "deny", "undeny", "kick", "add", "unadd", "storage", "unstore" -> rolle(spieler, aktion, args);
            default -> hilfe(spieler);
        }
    }

    private void amPlot(Player spieler, String aktion, String[] args) throws IOException {
        if (VERWALTUNG.contains(aktion) && !spieler.hasPermission(MANAGE)) {
            texte.senden(spieler, "fehler.kein-recht");
            return;
        }
        var id = plotAmStandort(spieler);
        if (id.isEmpty()) {
            return;
        }
        switch (aktion) {
            case "submit" -> wettbewerb.einreichen(spieler, id.get());
            case "vote" -> wettbewerb.abstimmen(spieler, id.get());
            default -> verwaltung.ausfuehren(spieler, id.get(), aktion, args);
        }
    }

    private void entdecken(Player spieler, String[] args) {
        if (args.length > 3) {
            texte.senden(spieler, "profil.entdecken-hilfe");
            return;
        }
        try {
            Optional<Kategorie> kategorie = args.length < 2 || args[1].equalsIgnoreCase("all") ? Optional.empty()
                    : Optional.of(Kategorie.valueOf(args[1].toUpperCase(Locale.ROOT)));
            var sortierung = args.length < 3 ? PlotMenue.Sortierung.NEW
                    : PlotMenue.Sortierung.valueOf(args[2].toUpperCase(Locale.ROOT));
            menue.galerie(spieler, kategorie, sortierung);
        } catch (IllegalArgumentException e) {
            texte.senden(spieler, "profil.entdecken-hilfe");
        }
    }

    private void hilfe(CommandSender sender) {
        texte.senden(sender, "hilfe.reise");
        texte.senden(sender, "hilfe.galerie");
        if (sender.hasPermission(CLAIM)) {
            texte.senden(sender, "hilfe.claim");
        }
        if (sender.hasPermission(MANAGE)) {
            texte.senden(sender, "hilfe.rollen");
            texte.senden(sender, "hilfe.verwaltung");
        }
        if (!PlotAdminBefehl.erlaubteUnterbefehle(sender).isEmpty()) {
            admin.hilfe(sender);
        }
    }

    private void claim(Player spieler, boolean auto) throws IOException {
        if (!spieler.hasPermission(CLAIM)) {
            texte.senden(spieler, "fehler.kein-recht");
            return;
        }
        if (!plotModus(spieler)) {
            return;
        }
        World zielWelt = auto ? welt.ladenOderErstellen() : spieler.getWorld();
        var id = auto ? Optional.<PlotId>empty() : plotAmStandort(spieler);
        if (!auto && id.isEmpty()) {
            return;
        }
        int limit = konfiguration.claimLimit();
        UUID besitzer = spieler.getUniqueId();
        auftraege.ausfuehren(spieler, "/plot " + (auto ? "auto" : "claim"), () -> {
            PlotId ziel = auto ? plots.naechsterFreierPlot() : id.orElseThrow();
            return new ClaimAntwort(ziel, plots.claim(ziel, besitzer, limit));
        }, antwort -> {
            switch (antwort.ergebnis()) {
                case BELEGT -> texte.senden(spieler, "claim.belegt", unparsed("id", antwort.id().toString()));
                case LIMIT -> texte.senden(spieler, "claim.limit", unparsed("limit", Integer.toString(limit)));
                case ABGELEHNT -> texte.senden(spieler, "claim.abgelehnt");
                case CLAIMED -> {
                    texte.senden(spieler, "claim.erfolg", unparsed("id", antwort.id().toString()));
                    if (auto && spieler.isOnline() && plotModus(spieler)) {
                        teleport(spieler, welt.mitte(zielWelt, antwort.id()), "teleport.plot");
                    }
                }
            }
        });
    }

    private void home(Player spieler, String[] args) throws IOException {
        if (args.length > 2) {
            texte.senden(spieler, "hilfe.reise");
            return;
        }
        var plot = nachNummer(spieler.getUniqueId(), args.length == 2 ? args[1] : "1");
        if (plot.isEmpty()) {
            texte.senden(spieler, "fehler.kein-plot");
        } else {
            zumPlot(spieler, plot.get());
        }
    }

    private void besuchen(Player spieler, String[] args) throws IOException {
        if (args.length >= 2 && args[1].startsWith("#")) {
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).substring(1);
            var plot = plots.nachName(name);
            if (plot.isEmpty()) {
                texte.senden(spieler, "fehler.kein-plot");
            } else {
                zumPlot(spieler, plot.get());
            }
            return;
        }
        if (args.length < 2 || args.length > 3 || (args.length == 3 && args[1].contains(";"))) {
            texte.senden(spieler, "hilfe.reise");
            return;
        }
        Optional<Plot> plot;
        if (args[1].contains(";")) {
            try {
                plot = plots.finden(PlotId.lesen(args[1]));
            } catch (IllegalArgumentException e) {
                texte.senden(spieler, "fehler.plot-id");
                return;
            }
        } else {
            String nummer = args.length == 3 ? args[2] : "1";
            plot = spielerId(args[1]).flatMap(id -> nachNummer(id, nummer));
        }
        if (plot.isEmpty()) {
            texte.senden(spieler, "fehler.kein-plot");
        } else {
            zumPlot(spieler, plot.get());
        }
    }

    private void zumPlot(Player spieler, Plot plot) throws IOException {
        if (!PlotBerechtigungen.darfBetreten(spieler, plot)) {
            texte.senden(spieler, "zugang.gebannt");
        } else if (plotModus(spieler)) {
            World zielWelt = welt.ladenOderErstellen();
            var besuchspunkt = plot.profil().besuchspunkt().flatMap(punkt -> welt.besuchspunkt(zielWelt, plot.id(), punkt));
            teleport(spieler, besuchspunkt.orElseGet(() -> welt.wegVor(zielWelt, plot.id())),
                    besuchspunkt.isPresent() ? "teleport.besuchspunkt" : "teleport.vor-plot");
        }
    }

    private Optional<Plot> nachNummer(UUID besitzer, String wert) {
        int nummer;
        try {
            nummer = Integer.parseInt(wert);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        List<Plot> besitz = plots.vomBesitzer(besitzer);
        return nummer > 0 && nummer <= besitz.size() ? Optional.of(besitz.get(nummer - 1)) : Optional.empty();
    }

    private void liste(Player spieler) {
        var besitz = plots.vomBesitzer(spieler.getUniqueId());
        if (besitz.isEmpty()) {
            texte.senden(spieler, "liste.leer");
            return;
        }
        for (int i = 0; i < besitz.size(); i++) {
            texte.senden(spieler, "liste.eintrag", unparsed("number", Integer.toString(i + 1)),
                    unparsed("id", besitz.get(i).id().toString()));
        }
    }

    private void info(Player spieler) {
        plotAmStandort(spieler).ifPresent(id -> {
            var plot = plots.finden(id);
            if (plot.isEmpty()) {
                texte.senden(spieler, "info.frei", unparsed("id", id.toString()));
                return;
            }
            Plot besitz = plot.get();
            texte.senden(spieler, "info.besitzer", unparsed("id", id.toString()),
                    unparsed("owner", nameVon(besitz.besitzer())));
            if (!besitz.profil().name().isEmpty()) {
                texte.senden(spieler, "profil.anzeige", unparsed("name", besitz.profil().name()),
                        unparsed("description", besitz.profil().beschreibung()),
                        unparsed("count", Integer.toString(besitz.profil().likes().size())));
            }
            if (besitz.zellen().size() > 1) {
                texte.senden(spieler, "merge.info", unparsed("count", Integer.toString(besitz.zellen().size())));
            }
            if (PlotBerechtigungen.istAdmin(spieler) || besitz.besitzer().equals(spieler.getUniqueId())) {
                texte.senden(spieler, "info.rollen", unparsed("trusted", namen(besitz.rechte().trusted())),
                        unparsed("denied", namen(besitz.rechte().gebannt())));
                texte.senden(spieler, "info.bauhelfer-lager", unparsed("helpers", namen(besitz.rechte().bauhelfer())),
                        unparsed("storage", namen(besitz.rechte().lager())));
            }
        });
    }

    private void rolle(Player spieler, String aktion, String[] args) throws IOException {
        if (!spieler.hasPermission(MANAGE)) {
            texte.senden(spieler, "fehler.kein-recht");
            return;
        }
        if (args.length != 2) {
            texte.senden(spieler, "hilfe.rollen");
            return;
        }
        var id = plotAmStandort(spieler);
        if (id.isEmpty()) {
            return;
        }
        if (plots.finden(id.get()).filter(plot -> plot.besitzer().equals(spieler.getUniqueId())).isEmpty()) {
            texte.senden(spieler, "fehler.nicht-besitzer");
            return;
        }
        var ziel = spielerId(args[1]);
        if (ziel.isEmpty()) {
            texte.senden(spieler, "fehler.unbekannter-spieler");
            return;
        }
        UUID zielId = ziel.get();
        Player online = server.getPlayer(zielId);
        if (aktion.equals("kick")) {
            if (zielId.equals(spieler.getUniqueId())) {
                texte.senden(spieler, "rollen.selbst");
            } else if (online == null || !welt.plotBei(online.getLocation()).equals(id)) {
                texte.senden(spieler, "rollen.kick-abwesend");
            } else if (PlotBerechtigungen.istAdmin(online)) {
                texte.senden(spieler, "rollen.kick-admin");
            } else if (rausbringen(online, id.get(), "zugang.gekickt")) {
                texte.senden(spieler, "rollen.kick", unparsed("player", online.getName()));
            } else {
                texte.senden(spieler, "fehler.teleport");
            }
            return;
        }

        var aenderung = Plot.RollenAktion.valueOf(aktion.toUpperCase(Locale.ROOT));
        UUID besitzer = spieler.getUniqueId();
        auftraege.ausfuehren(spieler, "/plot " + aktion,
                () -> plots.rolleAendern(id.get(), besitzer, zielId, aenderung), ergebnis -> {
            switch (ergebnis) {
                case FREMDER_PLOT -> texte.senden(spieler, "fehler.nicht-besitzer");
                case BESITZER -> texte.senden(spieler, "rollen.selbst");
                case GEBANNT -> texte.senden(spieler, "rollen.gebannt");
                case UNVERAENDERT -> texte.senden(spieler, "rollen.unveraendert");
                case GEAENDERT -> {
                    texte.senden(spieler, "rollen." + aktion, unparsed("player", nameVon(zielId)));
                    Player anwesend = server.getPlayer(zielId);
                    if (aenderung == Plot.RollenAktion.DENY && anwesend != null && !PlotBerechtigungen.istAdmin(anwesend)
                            && welt.plotBei(anwesend.getLocation()).equals(id)
                            && !rausbringen(anwesend, id.get(), "zugang.gebannt")) {
                        texte.senden(spieler, "fehler.teleport");
                    }
                }
            }
        });
    }

    private boolean rausbringen(Player spieler, PlotId id, String text) {
        if (!spieler.teleport(welt.wegVor(spieler.getWorld(), id))) {
            return false;
        }
        texte.senden(spieler, text);
        return true;
    }

    private Optional<PlotId> plotAmStandort(Player spieler) {
        if (!welt.istPlotWelt(spieler.getWorld())) {
            texte.senden(spieler, "fehler.keine-plotwelt");
            return Optional.empty();
        }
        var id = welt.plotBei(spieler.getLocation());
        if (id.isEmpty()) {
            texte.senden(spieler, "fehler.auf-weg");
        }
        return id;
    }

    private boolean plotModus(Player spieler) {
        if (PlotBerechtigungen.modusErlaubt(spieler, spieler.getGameMode())) {
            return true;
        }
        texte.senden(spieler, "zugang.survival");
        return false;
    }

    private void teleport(Player spieler, Location ziel, String text) {
        texte.senden(spieler, spieler.teleport(ziel) ? text : "fehler.teleport");
    }

    private Optional<UUID> spielerId(String wert) {
        if (wert.length() == 36 && wert.indexOf('-') >= 0) {
            try {
                return Optional.of(UUID.fromString(wert));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        Player online = server.getPlayerExact(wert);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        return Optional.ofNullable(server.getOfflinePlayerIfCached(wert)).map(OfflinePlayer::getUniqueId);
    }

    private String nameVon(UUID id) {
        return Optional.ofNullable(server.getOfflinePlayer(id).getName()).orElseGet(id::toString);
    }

    private String namen(Collection<UUID> ids) {
        return ids.isEmpty() ? "–" : ids.stream().map(this::nameVon).sorted().collect(Collectors.joining(", "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0 || !sender.hasPermission(USE)) {
            return List.of();
        }
        String aktion = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return passende(unterbefehle(sender), aktion);
        }
        if (args.length == 3) {
            if (aktion.equals("admin") && args[1].equalsIgnoreCase("gamemode") && sender.hasPermission(PlotBerechtigungen.ADMIN)) {
                return passende(PlotAdminBefehl.MODI, args[2]);
            }
            if (aktion.equals("discover")) {
                return passende(List.of("new", "popular"), args[2]);
            }
            if (sender.hasPermission(MANAGE)) {
                if (aktion.equals("flag")) {
                    return passende(List.of("on", "off"), args[2]);
                }
                if (aktion.equals("profile") && args[1].equalsIgnoreCase("category")) {
                    return passende(kategorien("off"), args[2]);
                }
                if (aktion.equals("merge")) {
                    return passende(List.of("confirm"), args[2]);
                }
            }
        }
        if (args.length != 2) {
            return List.of();
        }
        if (aktion.equals("admin")) {
            return passende(PlotAdminBefehl.erlaubteUnterbefehle(sender), args[1]);
        }
        if (aktion.equals("contest")) {
            var befehle = new ArrayList<>(PlotWettbewerbBefehl.SPIELERBEFEHLE);
            if (sender.hasPermission(PlotBerechtigungen.ADMIN)) {
                befehle.addAll(PlotWettbewerbBefehl.ADMINBEFEHLE);
            }
            return passende(befehle, args[1]);
        }
        if (aktion.equals("discover")) {
            return passende(kategorien("all"), args[1]);
        }
        if (sender.hasPermission(MANAGE)) {
            var vorschlaege = switch (aktion) {
                case "profile" -> List.of("name", "description", "category");
                case "sethome" -> List.of("off");
                case "flag" -> List.of("doors", "switches");
                default -> List.<String>of();
            };
            if (!vorschlaege.isEmpty()) {
                return passende(vorschlaege, args[1]);
            }
        }
        if (sender instanceof Player spieler && (aktion.equals("visit")
                || (ROLLENBEFEHLE.contains(aktion) && sender.hasPermission(MANAGE)))) {
            if (aktion.equals("visit") && args[1].startsWith("#")) {
                var namen = plots.galerie(plot -> PlotBerechtigungen.darfBetreten(spieler, plot)).stream()
                        .map(plot -> "#" + plot.profil().name()).toList();
                return passende(namen, args[1]);
            }
            return passende(spielerVorschlaege(spieler, aktion), args[1]);
        }
        return List.of();
    }

    private List<String> unterbefehle(CommandSender sender) {
        var befehle = new ArrayList<String>();
        if (sender instanceof Player) {
            befehle.addAll(BESUCHSBEFEHLE);
            if (sender.hasPermission(CLAIM)) {
                befehle.addAll(List.of("claim", "auto"));
            }
            if (sender.hasPermission(MANAGE)) {
                befehle.addAll(ROLLENBEFEHLE);
                befehle.addAll(VERWALTUNG);
            }
        } else {
            befehle.addAll(List.of("help", "contest"));
        }
        if (!PlotAdminBefehl.erlaubteUnterbefehle(sender).isEmpty()) {
            befehle.add("admin");
        }
        return befehle;
    }

    private List<String> spielerVorschlaege(Player spieler, String aktion) {
        if (List.of("untrust", "undeny", "unban", "unadd", "unstore").contains(aktion)) {
            return welt.plotBei(spieler.getLocation()).flatMap(plots::finden)
                    .filter(plot -> plot.besitzer().equals(spieler.getUniqueId()))
                    .map(plot -> switch (aktion) {
                        case "untrust" -> plot.rechte().trusted();
                        case "unadd" -> plot.rechte().bauhelfer();
                        case "unstore" -> plot.rechte().lager();
                        default -> plot.rechte().gebannt();
                    })
                    .stream().flatMap(Collection::stream).map(this::nameVon).toList();
        }
        return server.getOnlinePlayers().stream().filter(ziel -> spieler.canSee(ziel)).map(Player::getName).toList();
    }

    private static List<String> kategorien(String zusatz) {
        var ergebnis = new ArrayList<String>();
        ergebnis.add(zusatz);
        for (Kategorie kategorie : Kategorie.values()) {
            ergebnis.add(kategorie.name().toLowerCase(Locale.ROOT));
        }
        return ergebnis;
    }

    private static List<String> passende(Collection<String> werte, String praefix) {
        String klein = praefix.toLowerCase(Locale.ROOT);
        return werte.stream().filter(wert -> wert.toLowerCase(Locale.ROOT).startsWith(klein))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private record ClaimAntwort(PlotId id, PlotDienst.ClaimErgebnis ergebnis) {
    }
}
