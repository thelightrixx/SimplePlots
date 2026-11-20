package de.lightrixx.plot.paper;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PlotBerechtigungenTest {
    @Test
    void erlaubtAlleSpielmodiNurMitAdminrecht() {
        Permissible spieler = spielerMitAdminrecht(false);
        Permissible admin = spielerMitAdminrecht(true);

        for (GameMode modus : GameMode.values()) {
            assertTrue(PlotBerechtigungen.modusErlaubt(admin, modus));
            if (modus == GameMode.SURVIVAL) assertTrue(PlotBerechtigungen.modusErlaubt(spieler, modus));
            else assertFalse(PlotBerechtigungen.modusErlaubt(spieler, modus));
        }
        assertFalse(PlotBerechtigungen.modusErlaubt(admin, null));
    }

    @Test
    void behaeltAdminModiBeiAnkunftUndEntziehtSieNachRechteverlust() {
        var spieler = mock(Player.class);
        when(spieler.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        for (GameMode modus : GameMode.values()) {
            when(spieler.getGameMode()).thenReturn(modus);
            PlotBerechtigungen.modusAmOrtPruefen(spieler, true);
        }
        verify(spieler, never()).setGameMode(any());
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(false);
        for (GameMode modus : new GameMode[]{GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR}) {
            when(spieler.getGameMode()).thenReturn(modus);
            PlotBerechtigungen.modusAmOrtPruefen(spieler, true);
        }
        verify(spieler, times(3)).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void setztNurUeberDenAdminbefehlGeaenderteModiBeimVerlassenZurueck() {
        var spieler = mock(Player.class);
        var modus = new AtomicReference<>(GameMode.SURVIVAL);
        var pdc = mock(PersistentDataContainer.class);
        Map<NamespacedKey, Object> daten = new HashMap<>();
        when(spieler.getPersistentDataContainer()).thenReturn(pdc);
        when(spieler.hasPermission(PlotBerechtigungen.ADMIN)).thenReturn(true);
        when(spieler.getGameMode()).thenAnswer(aufruf -> modus.get());
        doAnswer(aufruf -> { modus.set(aufruf.getArgument(0)); return null; }).when(spieler).setGameMode(any());
        when(pdc.has(any(), any())).thenAnswer(aufruf -> daten.containsKey(aufruf.getArgument(0)));
        doAnswer(aufruf -> { daten.put(aufruf.getArgument(0), aufruf.getArgument(2)); return null; }).when(pdc).set(any(), any(), any());
        doAnswer(aufruf -> { daten.remove(aufruf.getArgument(0)); return null; }).when(pdc).remove(any());
        for (GameMode wert : new GameMode[]{GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR}) {
            assertTrue(PlotBerechtigungen.adminModusSetzen(spieler, wert));
            PlotBerechtigungen.modusAmOrtPruefen(spieler, true);
            assertEquals(wert, modus.get());
            PlotBerechtigungen.modusAmOrtPruefen(spieler, false);
            assertEquals(GameMode.SURVIVAL, modus.get());
            assertTrue(daten.isEmpty());
            modus.set(wert);
            PlotBerechtigungen.modusAmOrtPruefen(spieler, false);
            assertEquals(wert, modus.get());
            modus.set(GameMode.SURVIVAL);
        }
        doAnswer(aufruf -> null).when(spieler).setGameMode(any());
        assertFalse(PlotBerechtigungen.adminModusSetzen(spieler, GameMode.SPECTATOR));
        assertTrue(daten.isEmpty());
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
