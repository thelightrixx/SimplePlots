package de.lightrixx.plot.paper.economy;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Nutzt die öffentliche Plugin-Schnittstelle von EconomySystem
 * ({@code economy()}, {@code accountAsync}, {@code withdraw}, {@code depositDirect}, {@code currency}).
 * Vault ist weder installiert noch erforderlich.
 */
public final class EconomySystemWirtschaft implements Wirtschaft {
    private final PlotKonfiguration konfiguration;
    private final Logger log;
    private volatile Anbindung anbindung = Anbindung.nichtAktiv();

    public EconomySystemWirtschaft(PlotKonfiguration konfiguration, Logger log) {
        this.konfiguration = konfiguration;
        this.log = log;
        neuBinden();
    }

    public void neuBinden() {
        String name = konfiguration.text("general.wirtschaft.plugin");
        if (!konfiguration.aktiv("general.wirtschaft.aktiv")) {
            anbindung = Anbindung.nichtAktiv();
            log.info("Wirtschaft: Anbindung nicht aktiv, Claims bleiben kostenlos.");
            return;
        }
        anbindung = Anbindung.suchen(name);
        switch (anbindung.zustand()) {
            case BEREIT -> log.info("Wirtschaft: {} angebunden, Claim-Preis {} je Rasterzelle.", name,
                    formatiere(konfiguration.betrag("general.wirtschaft.preis")));
            case PLUGIN_FEHLT -> log.warn("Wirtschaft: Plugin '{}' fehlt. Claims werden abgelehnt, bis es installiert ist.", name);
            case SCHNITTSTELLE_FEHLT -> log.warn("Wirtschaft: '{}' stellt economy()/accountAsync/withdraw/depositDirect "
                    + "nicht bereit. Claims werden abgelehnt, bis die Anbindung vollständig ist.", name);
            default -> { }
        }
    }

    @Override
    public PlotKauf.KaufErgebnis abbuchen(UUID spieler, String name, BigDecimal betrag) {
        Anbindung aktuell = anbindung;
        if (aktuell.zustand() != Zustand.BEREIT) {
            return PlotKauf.KaufErgebnis.NICHT_VERFUEGBAR;
        }
        try {
            Object konto = konto(aktuell, spieler, name);
            return Boolean.TRUE.equals(aktuell.abzug().invoke(aktuell.dienst(), konto, betrag))
                    ? PlotKauf.KaufErgebnis.GEKAUFT : PlotKauf.KaufErgebnis.KEIN_GELD;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Abbuchung für {} wurde unterbrochen.", name, e);
            return PlotKauf.KaufErgebnis.NICHT_VERFUEGBAR;
        } catch (ExecutionException | TimeoutException | ReflectiveOperationException | RuntimeException e) {
            log.error("Abbuchung für {} über EconomySystem fehlgeschlagen.", name, e);
            return PlotKauf.KaufErgebnis.NICHT_VERFUEGBAR;
        }
    }

    @Override
    public boolean erstatten(UUID spieler, String name, BigDecimal betrag) {
        Anbindung aktuell = anbindung;
        if (aktuell.zustand() != Zustand.BEREIT) {
            return false;
        }
        try {
            Object konto = konto(aktuell, spieler, name);
            return Boolean.TRUE.equals(aktuell.gutschrift().invoke(aktuell.dienst(), konto, betrag));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Erstattung an {} wurde unterbrochen.", name, e);
            return false;
        } catch (ExecutionException | TimeoutException | ReflectiveOperationException | RuntimeException e) {
            log.error("Erstattung an {} über EconomySystem fehlgeschlagen.", name, e);
            return false;
        }
    }

    @Override
    public String formatiere(BigDecimal betrag) {
        Anbindung aktuell = anbindung;
        if (aktuell.zustand() == Zustand.BEREIT) {
            try {
                return (String) aktuell.format().invoke(aktuell.waehrung().invoke(aktuell.dienst()), betrag);
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.warn("Betrag konnte nicht über EconomySystem formatiert werden.", e);
            }
        }
        return betrag.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private Object konto(Anbindung aktuell, UUID spieler, String name)
            throws ReflectiveOperationException, ExecutionException, TimeoutException, InterruptedException {
        if (!(aktuell.konto().invoke(aktuell.dienst(), spieler, name) instanceof CompletableFuture<?> offen)) {
            throw new IllegalStateException("accountAsync liefert kein CompletableFuture.");
        }
        return offen.get(konfiguration.zahl("general.wirtschaft.zeitlimit-millis"), TimeUnit.MILLISECONDS);
    }

    private enum Zustand {
        NICHT_AKTIV, BEREIT, PLUGIN_FEHLT, SCHNITTSTELLE_FEHLT
    }

    private record Anbindung(Zustand zustand, Object dienst, Method konto, Method abzug, Method gutschrift,
                             Method waehrung, Method format) {
        static Anbindung nichtAktiv() {
            return new Anbindung(Zustand.NICHT_AKTIV, null, null, null, null, null, null);
        }

        static Anbindung suchen(String name) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin == null || !plugin.isEnabled()) {
                return new Anbindung(Zustand.PLUGIN_FEHLT, null, null, null, null, null, null);
            }
            try {
                Object dienst = plugin.getClass().getMethod("economy").invoke(plugin);
                if (dienst == null) {
                    return new Anbindung(Zustand.SCHNITTSTELLE_FEHLT, null, null, null, null, null, null);
                }
                Method abzug = Arrays.stream(dienst.getClass().getMethods())
                        .filter(methode -> methode.getName().equals("withdraw")
                                && methode.getParameterCount() == 2
                                && methode.getParameterTypes()[0] != BigDecimal.class
                                && methode.getParameterTypes()[1] == BigDecimal.class)
                        .findFirst()
                        .orElseThrow(() -> new NoSuchMethodException("withdraw(Account, BigDecimal)"));
                Class<?> kontoTyp = abzug.getParameterTypes()[0];
                Method waehrung = dienst.getClass().getMethod("currency");
                return new Anbindung(Zustand.BEREIT, dienst,
                        dienst.getClass().getMethod("accountAsync", UUID.class, String.class),
                        abzug,
                        dienst.getClass().getMethod("depositDirect", kontoTyp, BigDecimal.class),
                        waehrung,
                        waehrung.getReturnType().getMethod("format", BigDecimal.class));
            } catch (ReflectiveOperationException | RuntimeException e) {
                return new Anbindung(Zustand.SCHNITTSTELLE_FEHLT, null, null, null, null, null, null);
            }
        }
    }
}
