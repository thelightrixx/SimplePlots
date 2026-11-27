package de.lightrixx.plot.paper;

import de.lightrixx.plot.paper.config.PlotKonfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bukkit berechnet Vererbung und explizite negative Einzelrechte selbst. */
public final class PlotRangBuendel {
    private final PluginManager manager;
    private final Set<String> registriert = new HashSet<>();

    public PlotRangBuendel(PluginManager manager) { this.manager = manager; }

    @SuppressWarnings("unchecked")
    public void uebernehmen(PlotKonfiguration.Werte werte) {
        var buendel = (Map<String, List<String>>) werte.optionen().get("general.rang-buendel");
        var neu = new HashSet<String>();
        for (var eintrag : buendel.entrySet()) {
            String name = "simpleplots.rang." + eintrag.getKey();
            neu.add(name);
            var kinder = new HashMap<String, Boolean>();
            eintrag.getValue().forEach(recht -> kinder.put(recht, true));
            var permission = manager.getPermission(name);
            if (permission == null) {
                permission = new Permission(name, "SimplePlots-Rechtebündel " + eintrag.getKey(), PermissionDefault.FALSE, kinder);
                manager.addPermission(permission);
            } else {
                permission.getChildren().clear(); permission.getChildren().putAll(kinder);
                permission.recalculatePermissibles();
            }
        }
        for (String alt : Set.copyOf(registriert)) {
            if (neu.contains(alt)) continue;
            var permission = manager.getPermission(alt);
            if (permission != null) { permission.getChildren().clear(); permission.recalculatePermissibles(); }
            manager.removePermission(alt);
        }
        registriert.clear(); registriert.addAll(neu);
    }
}
