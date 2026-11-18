package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class PlotAdminBefehl {
    static final List<String> UNTERBEFEHLE = List.of("info", "create", "grid", "limit", "gamemode");
    static final List<String> MODI = List.of("creative", "survival");
    private final PlotKonfiguration konfiguration;
    private final PlotWelt welt;
    private final PlotNachrichten texte;

    PlotAdminBefehl(PlotKonfiguration konfiguration, PlotWelt welt, PlotNachrichten texte) {
        this.konfiguration = konfiguration;
        this.welt = welt;
        this.texte = texte;
    }

    void ausfuehren(CommandSender sender, String[] args) throws IOException {
        if (!sender.hasPermission(PlotBerechtigungen.ADMIN)) {
            texte.senden(sender, "fehler.kein-recht");
            return;
        }
        String aktion = args.length == 1 ? "info" : args[1].toLowerCase(Locale.ROOT);
        switch (aktion) {
            case "info" -> info(sender);
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
            default -> hilfe(sender);
        }
    }

    void hilfe(CommandSender sender) {
        texte.senden(sender, "admin.hilfe");
        if (sender instanceof Player) {
            texte.senden(sender, "admin.modus-hilfe");
        }
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
