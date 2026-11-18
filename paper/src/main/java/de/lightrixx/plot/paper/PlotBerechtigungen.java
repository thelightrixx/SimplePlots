package de.lightrixx.plot.paper;

import de.lightrixx.plot.model.Plot;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.persistence.PersistentDataType;

public final class PlotBerechtigungen {
    public static final String ADMIN = "simpleplots.admin";
    private static final NamespacedKey PLOT_KREATIV = new NamespacedKey("simpleplots", "plot-kreativ");

    private PlotBerechtigungen() {
    }

    public static boolean istAdmin(Permissible spieler) {
        return spieler != null && spieler.hasPermission(ADMIN);
    }

    public static boolean darfBetreten(Player spieler, Plot plot) {
        return istAdmin(spieler) || plot.darfBetreten(spieler.getUniqueId());
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
