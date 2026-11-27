package de.lightrixx.plot.paper.command;

import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

/** Standortabhängige Befehle bleiben unverändert; die Konsole kann eine Welt ausdrücklich auswählen. */
public final class PlotWeltenBefehl implements TabExecutor {
    private final Map<String, Kontext> welten;
    private final Supplier<String> standard;
    private final PlotNachrichten texte;

    public PlotWeltenBefehl(Map<String, Kontext> welten, Supplier<String> standard, PlotNachrichten texte) {
        this.welten = Map.copyOf(welten); this.standard = standard; this.texte = texte;
    }

    private Kontext aktuell(CommandSender sender) {
        if (sender instanceof Player spieler) {
            for (var kontext : welten.values()) if (kontext.welt().istPlotWelt(spieler.getWorld())) return kontext;
        }
        return welten.get(standard.get());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PlotBerechtigungen.USE)) { texte.senden(sender, "fehler.kein-recht"); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("worlds")) {
            texte.senden(sender, "welten.liste", unparsed("worlds", String.join(", ", welten.keySet().stream().sorted().toList())));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("in")) {
            if (args.length < 3) { texte.senden(sender, "welten.hilfe"); return true; }
            var kontext = welten.get(args[1]);
            if (kontext == null) { texte.senden(sender, "welten.unbekannt"); return true; }
            return kontext.befehl().onCommand(sender, command, label, Arrays.copyOfRange(args, 2, args.length));
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("world")) {
            if (args.length != 2) { texte.senden(sender, "welten.hilfe"); return true; }
            var kontext = welten.get(args[1]);
            if (kontext == null) { texte.senden(sender, "welten.unbekannt"); return true; }
            return kontext.befehl().onCommand(sender, command, label, new String[]{"world"});
        }
        boolean ergebnis = aktuell(sender).befehl().onCommand(sender, command, label, args);
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help"))) texte.senden(sender, "welten.hilfe");
        return ergebnis;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PlotBerechtigungen.USE)) return List.of();
        if (args.length == 2 && (args[0].equalsIgnoreCase("world") || args[0].equalsIgnoreCase("in")))
            return welten.keySet().stream().filter(name -> name.startsWith(args[1].toLowerCase(Locale.ROOT))).sorted().toList();
        if (args.length >= 3 && args[0].equalsIgnoreCase("in")) {
            var kontext = welten.get(args[1]);
            return kontext == null ? List.of() : kontext.befehl().onTabComplete(sender, command, label, Arrays.copyOfRange(args, 2, args.length));
        }
        var vorschlaege = new ArrayList<>(aktuell(sender).befehl().onTabComplete(sender, command, label, args));
        if (args.length == 1) for (String name : List.of("in", "worlds")) if (name.startsWith(args[0].toLowerCase(Locale.ROOT))) vorschlaege.add(name);
        return List.copyOf(vorschlaege);
    }

    public record Kontext(PlotWelt welt, TabExecutor befehl) { }
}
