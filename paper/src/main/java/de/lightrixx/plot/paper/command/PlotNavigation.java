package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.model.*;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.*;
import de.lightrixx.plot.paper.world.*;
import de.lightrixx.plot.service.PlotDienst;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import java.io.IOException;
import java.util.*;
import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class PlotNavigation {
    @FunctionalInterface interface Besuch { void besuchen(Player player, Plot plot) throws IOException; }
    private final PlotDienst plots;
    private final PlotWelt welt;
    private final PlotKonfiguration config;
    private final PlotNachrichten texte;
    private final Besuch besuch;
    private final NamespacedKey key;
    private PlotGrenzen grenzen;
    private final org.slf4j.Logger log;
    PlotNavigation(PlotDienst plots, PlotWelt welt, PlotKonfiguration config, PlotNachrichten texte, Besuch besuch, org.slf4j.Logger log) {
        this.plots = plots; this.welt = welt; this.config = config; this.texte = texte; this.besuch = besuch;
        key = new NamespacedKey("simpleplots", "lesezeichen_" + config.weltname());
        this.log = log;
    }
    void grenzen(PlotGrenzen grenzen) { this.grenzen = grenzen; }
    void ausfuehren(Player player, String action, String[] args) throws IOException {
        if (!player.hasPermission(action.equals("border") ? "simpleplots.border" : "simpleplots.bookmark")) { texte.senden(player, "fehler.kein-recht"); return; }
        if (action.equals("border")) { grenze(player, args); return; }
        String operation = args.length < 2 ? "list" : args[1].toLowerCase(Locale.ROOT);
        if (operation.equals("list") && args.length <= 2) {
            var rows = lesen(player);
            texte.senden(player, "navigation.liste", unparsed("count", String.valueOf(rows.size())), unparsed("world", config.weltname()));
            for (var row : rows) texte.senden(player, "navigation.eintrag", unparsed("name", row.name()), unparsed("id", row.zelle().toString()));
            return;
        }
        if (args.length < 3 || !List.of("add", "remove", "go").contains(operation)) { texte.senden(player, "navigation.hilfe"); return; }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (!name.matches("[\\p{L}\\p{N} _-]{1,32}") || name.isBlank() || !name.equals(name.strip())) { texte.senden(player, "navigation.name"); return; }
        var rows = new ArrayList<>(lesen(player));
        var previous = rows.stream().filter(row -> row.name().equalsIgnoreCase(name)).findFirst();
        if (operation.equals("go")) {
            if (previous.isEmpty()) { texte.senden(player, "navigation.fehlt"); return; }
            var bookmark = previous.get(); var plot = plots.finden(bookmark.zelle());
            if (plot.isEmpty() || plot.get().zensiert() || !plot.get().besitzer().equals(bookmark.besitzer())
                    || !PlotBerechtigungen.darfBetreten(player, plot.get())) { texte.senden(player, "navigation.veraltet"); return; }
            if (!welt.ladenOderErstellen().getUID().equals(bookmark.welt())) { texte.senden(player, "navigation.veraltet"); return; }
            besuch.besuchen(player, plot.get()); return;
        }
        if (operation.equals("remove")) {
            if (previous.isEmpty()) { texte.senden(player, "navigation.fehlt"); return; }
            rows.remove(previous.get());
        } else {
            var plot = welt.plotBei(player.getLocation()).flatMap(plots::finden);
            if (!welt.istPlotWelt(player.getWorld()) || plot.isEmpty() || plot.get().zensiert() || !PlotBerechtigungen.darfBetreten(player, plot.get())) { texte.senden(player, "navigation.ort"); return; }
            if (previous.isEmpty() && rows.size() >= config.zahl("general.navigation.lesezeichen-limit")) { texte.senden(player, "navigation.limit"); return; }
            previous.ifPresent(rows::remove);
            rows.add(new PlotLesezeichen(name, player.getWorld().getUID(), plot.get().id(), plot.get().besitzer()));
        }
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, PlotLesezeichen.schreiben(rows));
        texte.senden(player, operation.equals("remove") ? "navigation.entfernt" : "navigation.gemerkt", unparsed("name", name));
    }
    private List<PlotLesezeichen> lesen(Player player) throws IOException {
        try { return PlotLesezeichen.lesen(player.getPersistentDataContainer().get(key, PersistentDataType.STRING)); }
        catch (RuntimeException failure) { throw new IOException("Ungültige persönliche Lesezeichen für " + player.getUniqueId() + "; Bestand bleibt erhalten.", failure); }
    }
    private void grenze(Player player, String[] args) {
        if (grenzen == null || args.length > 2) { texte.senden(player, "navigation.hilfe"); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("off")) { grenzen.stoppen(player); texte.senden(player, "navigation.grenze-aus"); return; }
        int dauer;
        try { dauer = args.length == 2 ? Integer.parseInt(args[1]) : config.zahl("general.navigation.grenzen-sekunden"); }
        catch (NumberFormatException failure) { texte.senden(player, "navigation.hilfe"); return; }
        var plot = welt.plotBei(player.getLocation()).flatMap(plots::finden);
        if (!welt.istPlotWelt(player.getWorld()) || plot.isEmpty() || plot.get().zensiert() || !PlotBerechtigungen.darfBetreten(player, plot.get())) { texte.senden(player, "navigation.ort"); return; }
        if (!grenzen.zeigen(player, plot.get(), plots, config, dauer)) { texte.senden(player, "navigation.grenze-limit"); return; }
        texte.senden(player, "navigation.grenze-an", unparsed("seconds", String.valueOf(dauer)));
    }
    List<String> vorschlaege(Player player, String[] args) {
        if (!player.hasPermission(args[0].equalsIgnoreCase("border") ? "simpleplots.border" : "simpleplots.bookmark")) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args[0].equalsIgnoreCase("border")) return args.length == 2 ? List.of("15", "30", "off").stream().filter(s -> s.startsWith(prefix)).toList() : List.of();
        if (args.length == 2) return List.of("list", "add", "go", "remove").stream().filter(s -> s.startsWith(prefix)).toList();
        if (args.length >= 3 && List.of("go", "remove").contains(args[1].toLowerCase(Locale.ROOT))) {
            String eingabe = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).toLowerCase(Locale.ROOT);
            try { return lesen(player).stream().map(PlotLesezeichen::name).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(eingabe)).toList(); }
            catch (IOException failure) { log.debug("Lesezeichen-Vervollständigung für {} nicht verfügbar.", player.getUniqueId(), failure); return List.of(); }
        }
        return List.of();
    }
}
