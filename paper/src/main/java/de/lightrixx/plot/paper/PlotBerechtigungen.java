package de.lightrixx.plot.paper;

import de.lightrixx.plot.model.Plot;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.persistence.PersistentDataType;

public final class PlotBerechtigungen {
    public static final String USE = "simpleplots.use";
    public static final String CLAIM = "simpleplots.claim";
    public static final String MANAGE = "simpleplots.manage";
    public static final String ADMIN = "simpleplots.admin";
    public static final String RELOAD = "simpleplots.admin.reload";
    public static final String DIAGNOSE = "simpleplots.admin.diagnostics";
    public static final String FLY = "simpleplots.fly";
    private static final NamespacedKey PLOT_KREATIV = new NamespacedKey("simpleplots", "plot-kreativ");
    private static final NamespacedKey BYPASS_AUS = new NamespacedKey("simpleplots", "bypass-aus");

    private PlotBerechtigungen() {
    }

    public static boolean istAdmin(Permissible spieler) {
        return spieler != null && spieler.hasPermission(ADMIN);
    }

    public static boolean darfBetreten(Player spieler, Plot plot) {
        return bypassAktiv(spieler) || plot.darfBetreten(spieler.getUniqueId());
    }

    public static boolean bypassAktiv(Player spieler) {
        return spieler != null && spieler.hasPermission("simpleplots.admin.bypass")
                && !spieler.getPersistentDataContainer().has(BYPASS_AUS);
    }

    public static void bypassSetzen(Player spieler, boolean wert) {
        if (wert) {
            spieler.getPersistentDataContainer().remove(BYPASS_AUS);
        } else {
            spieler.getPersistentDataContainer().set(BYPASS_AUS, PersistentDataType.BOOLEAN, true);
        }
    }

    public static boolean modusErlaubt(Permissible spieler, GameMode modus) {
        return modus == GameMode.SURVIVAL || (modus == GameMode.CREATIVE && istAdmin(spieler));
    }

    public static boolean adminModusSetzen(Player spieler, GameMode modus) {
        GameMode vorher = spieler.getGameMode();
        spieler.setGameMode(modus);
        if (spieler.getGameMode() != modus) {
            return false;
        }
        var daten = spieler.getPersistentDataContainer();
        if (modus != GameMode.CREATIVE) {
            daten.remove(PLOT_KREATIV);
        } else if (vorher != GameMode.CREATIVE) {
            daten.set(PLOT_KREATIV, PersistentDataType.BOOLEAN, true);
        }
        return true;
    }

    public static void modusAmOrtPruefen(Player spieler, boolean inPlotwelt) {
        var daten = spieler.getPersistentDataContainer();
        GameMode modus = spieler.getGameMode();
        boolean plotKreativ = daten.has(PLOT_KREATIV, PersistentDataType.BOOLEAN);
        if (inPlotwelt ? !modusErlaubt(spieler, modus) : plotKreativ && modus == GameMode.CREATIVE) {
            spieler.setGameMode(GameMode.SURVIVAL);
        }
        if (plotKreativ && spieler.getGameMode() != GameMode.CREATIVE) {
            daten.remove(PLOT_KREATIV);
        }
    }
}
