package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.gui.PlotMenue;
import de.lightrixx.plot.paper.update.PlotUpdater;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.paper.world.PlotZensur;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class PlotModerationBefehl {
    private final PlotDienst plots;
    private final PlotWelt welt;
    private final WettbewerbDienst wettbewerbe;
    private final PlotAuftraege auftraege;
    private final PlotNachrichten texte;
    private final PlotMenue menue;
    private final PlotUpdater updater;
    private final PlotZensur zensur;

    PlotModerationBefehl(PlotDienst plots, PlotWelt welt, WettbewerbDienst wettbewerbe, PlotAuftraege auftraege,
                        PlotNachrichten texte, PlotMenue menue, PlotUpdater updater, PlotZensur zensur) {
        this.plots = plots; this.welt = welt; this.wettbewerbe = wettbewerbe; this.auftraege = auftraege;
        this.texte = texte; this.menue = menue; this.updater = updater; this.zensur = zensur;
    }

    void ausfuehren(CommandSender sender, String aktion, String[] args) throws IOException {
        if (aktion.equals("update")) { updater.befehl(sender, args); return; }
        if (aktion.equals("bypass")) { bypass(sender, args); return; }
        if (aktion.equals("list")) {
            if (args.length != 3) { hilfe(sender); return; }
            spielerId(sender, args[2]).ifPresent(id -> {
                for (Plot plot : plots.vomBesitzer(id)) texte.senden(sender, "moderation.eintrag", unparsed("id", plot.id().toString()));
            });
            return;
        }
        if (args.length < 3) { hilfe(sender); return; }
        PlotId id;
        try { id = PlotId.lesen(args[2]); }
        catch (IllegalArgumentException e) { texte.senden(sender, "fehler.plot-id"); return; }
        if (aktion.equals("censor") || aktion.equals("uncensor")) {
            if (args.length > 4 || (args.length == 4 && !args[3].equalsIgnoreCase("on") && !args[3].equalsIgnoreCase("off"))) {
                hilfe(sender); return;
            }
            zensur.befehl(sender, id, !aktion.equals("uncensor") && (args.length == 3 || args[3].equalsIgnoreCase("on")));
            return;
        }
        if (aktion.equals("claim")) {
            if (args.length != 4) { hilfe(sender); return; }
            try {
                welt.raster().mitteX(id);
                welt.raster().mitteZ(id);
                welt.raster().nordwegZ(id);
                Math.toIntExact((long) id.x() * welt.raster().rasterweite() + welt.raster().groesse() - 1);
                Math.toIntExact((long) id.z() * welt.raster().rasterweite() + welt.raster().groesse() - 1);
            } catch (ArithmeticException e) { texte.senden(sender, "fehler.plot-id"); return; }
            welt.ladenOderErstellen();
            spielerId(sender, args[3]).ifPresent(ziel -> auftraege.ausfuehren(sender, "/plot admin claim",
                    () -> plots.claimKostenlos(id, ziel, Integer.MAX_VALUE), ergebnis -> {
                        texte.senden(sender, ergebnis == PlotDienst.ClaimErgebnis.CLAIMED ? "moderation.gespeichert" : "claim.belegt",
                                unparsed("id", id.toString()));
                        menue.schliessen();
                    }));
            return;
        }
        Plot plot = plots.finden(id).orElse(null);
        if (plot == null) { texte.senden(sender, "fehler.kein-plot"); return; }
        if (aktion.equals("inspect")) {
            if (args.length != 3) { hilfe(sender); return; }
            texte.senden(sender, "moderation.inspect", unparsed("id", plot.id().toString()),
                    unparsed("owner", plot.besitzer().toString()), unparsed("cells", Integer.toString(plot.zellen().size())),
                    unparsed("censored", Boolean.toString(plot.zensiert())));
            return;
        }
        if (aktion.equals("visit")) {
            if (args.length != 3) { hilfe(sender); return; }
            if (sender instanceof Player spieler) {
                texte.senden(sender, spieler.teleport(welt.wegVor(welt.ladenOderErstellen(), plot.id())) ? "teleport.vor-plot" : "fehler.teleport");
            } else texte.senden(sender, "fehler.nur-spieler");
            return;
        }
        if (aktion.equals("setowner")) {
            if (args.length != 5 || !args[4].equalsIgnoreCase("confirm")) { hilfe(sender); return; }
            spielerId(sender, args[3]).ifPresent(ziel -> auftraege.ausfuehren(sender, "/plot admin setowner",
                    () -> plots.uebertragen(id, plot.besitzer(), ziel, Integer.MAX_VALUE, wettbewerbe::gesperrt), ergebnis -> {
                        texte.senden(sender, "transfer." + ergebnis.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                                unparsed("id", plot.id().toString()));
                        menue.schliessen();
                    }));
            return;
        }
        if (aktion.equals("unclaim")) {
            if (args.length != 4 || !args[3].equalsIgnoreCase("confirm")) { hilfe(sender); return; }
            auftraege.ausfuehren(sender, "/plot admin unclaim", () -> plots.freigeben(id, wettbewerbe::gesperrt),
                    ergebnis -> antwort(sender, ergebnis));
            return;
        }
        if (aktion.equals("role")) {
            if (args.length != 5) { hilfe(sender); return; }
            Plot.RollenAktion rolle;
            try { rolle = Plot.RollenAktion.valueOf(args[3].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { hilfe(sender); return; }
            spielerId(sender, args[4]).ifPresent(ziel -> auftraege.ausfuehren(sender, "/plot admin role",
                    () -> plots.rolleAendern(id, plot.besitzer(), ziel, rolle), ergebnis -> {
                        texte.senden(sender, ergebnis == PlotDienst.RollenErgebnis.GEAENDERT ? "moderation.gespeichert"
                                : ergebnis == PlotDienst.RollenErgebnis.GESPERRT ? "zensur.gesperrt" : "rollen.unveraendert");
                        menue.schliessen();
                        Player online = sender.getServer().getPlayer(ziel);
                        if (rolle == Plot.RollenAktion.DENY && online != null
                                && welt.plotBei(online.getLocation()).filter(plot.id()::equals).isPresent()
                                && plots.finden(id).filter(neu -> !PlotBerechtigungen.darfBetreten(online, neu)).isPresent()) {
                            online.teleport(welt.wegVor(online.getWorld(), plot.id()));
                        }
                    }));
            return;
        }
        if (aktion.equals("profile")) {
            if (args.length < 5) { hilfe(sender); return; }
            String text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
            int laenge = text.codePointCount(0, text.length());
            if ((args[3].equalsIgnoreCase("name") && laenge > welt.konfiguration().zahl("general.profil.name-laenge"))
                    || (args[3].equalsIgnoreCase("description") && laenge > welt.konfiguration().zahl("general.profil.beschreibung-laenge"))) {
                texte.senden(sender, "profil.ungueltig"); return;
            }
            UnaryOperator<PlotProfil> aenderung;
            try {
                aenderung = switch (args[3].toLowerCase(Locale.ROOT)) {
                    case "name" -> profil -> profil.mitName(text);
                    case "description" -> profil -> profil.mitBeschreibung(text.equals("-") ? "" : text);
                    case "category" -> {
                        var kategorie = text.equalsIgnoreCase("off") ? Optional.<PlotProfil.Kategorie>empty()
                                : Optional.of(PlotProfil.Kategorie.valueOf(text.toUpperCase(Locale.ROOT)));
                        long jetzt = System.currentTimeMillis();
                        yield profil -> profil.mitKategorie(kategorie, jetzt);
                    }
                    default -> throw new IllegalArgumentException("Ungültiges Profilfeld.");
                };
                aenderung.apply(plot.profil());
            } catch (IllegalArgumentException e) { texte.senden(sender, "profil.ungueltig"); return; }
            auftraege.ausfuehren(sender, "/plot admin profile", () -> plots.profilAendern(id, plot.besitzer(), aenderung),
                    ergebnis -> antwort(sender, ergebnis));
            return;
        }
        hilfe(sender);
    }

    private void antwort(CommandSender sender, PlotDienst.Aenderung ergebnis) {
        texte.senden(sender, switch (ergebnis) {
            case GEAENDERT -> "moderation.gespeichert";
            case GESPERRT -> "zensur.gesperrt";
            case NAME_VERGEBEN -> "profil.name-vergeben";
            case FREMDER_PLOT -> "fehler.kein-plot";
            default -> "rollen.unveraendert";
        });
        menue.schliessen();
    }

    private Optional<UUID> spielerId(CommandSender sender, String text) {
        try {
            if (text.length() == 36) return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            texte.senden(sender, "fehler.unbekannter-spieler"); return Optional.empty();
        }
        var online = sender.getServer().getPlayerExact(text);
        var id = online != null ? Optional.of(online.getUniqueId())
                : Optional.ofNullable(sender.getServer().getOfflinePlayerIfCached(text)).map(OfflinePlayer::getUniqueId);
        if (id.isEmpty()) texte.senden(sender, "fehler.unbekannter-spieler");
        return id;
    }

    private void bypass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player spieler)) { texte.senden(sender, "fehler.nur-spieler"); return; }
        if (args.length != 3 || (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off"))) { hilfe(sender); return; }
        boolean aktiv = args[2].equalsIgnoreCase("on");
        PlotBerechtigungen.bypassSetzen(spieler, aktiv);
        texte.senden(sender, aktiv ? "moderation.bypass-an" : "moderation.bypass-aus");
        if (!aktiv) welt.plotBei(spieler.getLocation()).flatMap(plots::finden)
                .filter(plot -> !plot.darfBetreten(spieler.getUniqueId()))
                .ifPresent(plot -> spieler.teleport(welt.wegVor(spieler.getWorld(), plot.id())));
    }

    private void hilfe(CommandSender sender) { texte.senden(sender, "moderation.hilfe"); }
}
