package de.lightrixx.plot.paper;

import org.bukkit.GameMode;
import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotBerechtigungenTest {
    @Test
    void erlaubtKreativNurMitAdminrecht() {
        Permissible spieler = spielerMitAdminrecht(false);
        Permissible admin = spielerMitAdminrecht(true);

        assertTrue(PlotBerechtigungen.modusErlaubt(spieler, GameMode.SURVIVAL));
        assertTrue(PlotBerechtigungen.modusErlaubt(admin, GameMode.SURVIVAL));
        assertFalse(PlotBerechtigungen.modusErlaubt(spieler, GameMode.CREATIVE));
        assertTrue(PlotBerechtigungen.modusErlaubt(admin, GameMode.CREATIVE));
        assertFalse(PlotBerechtigungen.modusErlaubt(admin, GameMode.SPECTATOR));
    }

    private static Permissible spielerMitAdminrecht(boolean adminrecht) {
        return (Permissible) Proxy.newProxyInstance(Permissible.class.getClassLoader(), new Class<?>[]{Permissible.class},
                (proxy, methode, argumente) -> {
                    if (methode.getName().equals("hasPermission")) {
                        return adminrecht && "simpleplots.admin".equals(argumente[0]);
                    }
                    throw new UnsupportedOperationException(methode.getName());
                });
    }
}
