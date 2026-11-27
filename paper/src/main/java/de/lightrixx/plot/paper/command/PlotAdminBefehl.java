package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.paper.world.PlotEntfernen;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class PlotAdminBefehl {
    static final List<String> UNTERBEFEHLE = List.of("info", "create", "grid", "limit", "gamemode", "reload", "diagnostics",
            "update", "bypass", "list", "inspect", "visit", "claim", "unclaim", "setowner", "role", "profile", "censor", "uncensor", "remove", "restore", "setup");
    static final List<String> MODI = List.of("creative", "survival", "adventure", "spectator");
    private final PlotKonfiguration konfiguration;
    private final PlotWelt welt;
    private final PlotNachrichten texte;
    private final PlotBetrieb betrieb;
    private PlotModerationBefehl moderation;

    PlotAdminBefehl(PlotKonfiguration konfiguration, PlotWelt welt, PlotNachrichten texte, PlotBetrieb betrieb) {
        this.konfiguration = konfiguration;
        this.welt = welt;
        this.texte = texte;
        this.betrieb = betrieb;
    }

    void moderation(PlotModerationBefehl moderation) { this.moderation = moderation; }
    void entfernen(PlotEntfernen entfernen) { moderation.entfernen(entfernen); }

    void ausfuehren(CommandSender sender, String[] args) throws IOException {
        String aktion = args.length == 1 ? "info" : args[1].toLowerCase(Locale.ROOT);
        String recht = berechtigung(aktion);
        if (!sender.hasPermission(recht)) {
            texte.senden(sender, "fehler.kein-recht");
            return;
        }
        if (betrieb.reloadLaeuft() && List.of("create", "grid", "limit").contains(aktion)) {
            texte.senden(sender, "admin.reload-laeuft");
            return;
        }
        switch (aktion) {
            case "update", "bypass", "list", "inspect", "visit", "claim", "unclaim", "setowner", "role", "profile", "censor", "uncensor", "remove", "restore" ->
                    moderation.ausfuehren(sender, aktion, args);
            case "info" -> {
                if (args.length <= 2) {
                    info(sender);
                } else {
                    hilfe(sender);
                }
            }
            case "create" -> {
                if (args.length == 2) {
                    welt.ladenOderErstellen();
                    texte.senden(sender, "admin.bereit");
                } else {
                    hilfe(sender);
                }
            }
            case "grid" -> rasterSetzen(sender, args);
            case "limit" -> limitSetzen(sender, args);
            case "gamemode" -> spielmodusSetzen(sender, args);
            case "reload", "diagnostics", "setup" -> {
                if (args.length != 2) {
                    hilfe(sender);
                } else if (aktion.equals("reload")) {
                    betrieb.neuladen(sender);
                } else if (aktion.equals("setup")) {
                    betrieb.einrichtung(sender);
                } else {
                    betrieb.diagnose(sender);
                }
            }
            default -> hilfe(sender);
        }
    }

    void hilfe(CommandSender sender) {
        if (sender.hasPermission(PlotBerechtigungen.ADMIN)) {
            texte.senden(sender, "admin.hilfe");
            if (sender instanceof Player) {
                texte.senden(sender, "admin.modus-hilfe");
            }
        }
        if (sender.hasPermission(PlotBerechtigungen.RELOAD)) {
            texte.senden(sender, "admin.reload-hilfe");
        }
        if (sender.hasPermission(PlotBerechtigungen.DIAGNOSE)) {
            texte.senden(sender, "admin.diagnose-hilfe");
        }
        if (UNTERBEFEHLE.stream().filter(aktion -> !List.of("info", "create", "grid", "limit", "gamemode", "reload", "diagnostics").contains(aktion))
                .anyMatch(aktion -> sender.hasPermission(berechtigung(aktion)))) texte.senden(sender, "moderation.hilfe");
        if (sender.hasPermission("simpleplots.admin.censor")) texte.senden(sender, "moderation.ziel-hilfe");
        if (sender.hasPermission("simpleplots.admin.remove")) texte.senden(sender, "moderation.remove-hilfe");
        if (sender.hasPermission("simpleplots.admin.restore")) texte.senden(sender, "moderation.restore-hilfe");
        if (sender.hasPermission("simpleplots.admin.setup")) texte.senden(sender, "moderation.setup-hilfe");
    }

    static List<String> erlaubteUnterbefehle(CommandSender sender) {
        return UNTERBEFEHLE.stream().filter(aktion -> sender.hasPermission(berechtigung(aktion))).toList();
    }

    private static String berechtigung(String aktion) {
        return switch (aktion) {
            case "reload" -> PlotBerechtigungen.RELOAD;
            case "diagnostics" -> PlotBerechtigungen.DIAGNOSE;
            case "uncensor" -> "simpleplots.admin.censor";
            case "update", "bypass", "list", "inspect", "visit", "claim", "unclaim", "setowner", "role", "profile", "censor", "remove", "restore", "setup" ->
                    "simpleplots.admin." + aktion;
            default -> PlotBerechtigungen.ADMIN;
        };
    }

    private void spielmodusSetzen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player spieler)) {
            texte.senden(sender, "fehler.nur-spieler");
            return;
        }
        if (!welt.istPlotWelt(spieler.getWorld())) {
            texte.senden(spieler, "fehler.keine-plotwelt");
            return;
        }
        if (args.length != 3 || !MODI.contains(args[2].toLowerCase(Locale.ROOT))) {
            texte.senden(spieler, "admin.modus-hilfe");
            return;
        }
        GameMode modus = GameMode.valueOf(args[2].toUpperCase(Locale.ROOT));
        if (PlotBerechtigungen.adminModusSetzen(spieler, modus)) {
            texte.senden(spieler, "admin.modus-gesetzt", unparsed("mode", modus.name().toLowerCase(Locale.ROOT)));
        } else {
            texte.senden(spieler, "admin.modus-fehlgeschlagen");
        }
    }

    private void info(CommandSender sender) {
        PlotRaster raster = welt.raster();
        texte.senden(sender, welt.rasterFestgelegt() ? "admin.info-fest" : "admin.info-offen",
                unparsed("size", Integer.toString(raster.groesse())),
                unparsed("road", Integer.toString(raster.wegbreite())),
                unparsed("limit", Integer.toString(konfiguration.claimLimit())));
        hilfe(sender);
    }

    private void rasterSetzen(CommandSender sender, String[] args) throws IOException {
        if (args.length != 4) {
            hilfe(sender);
            return;
        }
        if (welt.rasterFestgelegt()) {
            texte.senden(sender, "admin.raster-festgelegt");
            return;
        }
        PlotRaster wert;
        try {
            wert = new PlotRaster(Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        } catch (IllegalArgumentException e) {
            texte.senden(sender, "admin.raster-ungueltig",
                    unparsed("max_size", Integer.toString(PlotRaster.MAX_PLOTGROESSE)),
                    unparsed("max_road", Integer.toString(PlotRaster.MAX_WEGBREITE)));
            return;
        }
        konfiguration.rasterSetzen(wert);
        info(sender);
    }

    private void limitSetzen(CommandSender sender, String[] args) throws IOException {
        if (args.length != 3) {
            hilfe(sender);
            return;
        }
        try {
            konfiguration.limitSetzen(Integer.parseInt(args[2]));
        } catch (IllegalArgumentException e) {
            texte.senden(sender, "admin.limit-ungueltig");
            return;
        }
        texte.senden(sender, "admin.limit-gesetzt", unparsed("limit", Integer.toString(konfiguration.claimLimit())));
    }
}
