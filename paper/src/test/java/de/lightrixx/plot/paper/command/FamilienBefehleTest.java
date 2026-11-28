package de.lightrixx.plot.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class FamilienBefehleTest {
    private final TabExecutor fachlich = mock(TabExecutor.class);
    private final CommandSender sender = mock(CommandSender.class);
    private final Command command = mock(Command.class);
    private final List<String> schluessel = new ArrayList<>();
    private final List<Map<String, String>> werte = new ArrayList<>();
    private FamilienBefehle familie;

    private FamilienBefehle.Eintrag eintrag(String name, String recht, boolean spieler, int min, int max, String... route) {
        return new FamilienBefehle.Eintrag(name, name + " [Argument]", recht.isEmpty() ? List.of() : List.of(recht),
                List.of(), spieler, min, max, List.of(route), name.equals("help") ? List.of("hilfe") : List.of());
    }

    @BeforeEach void vorbereiten() {
        familie = new FamilienBefehle("test", List.of(eintrag("help", "", false, 0, 1, "help"),
                eintrag("version", "", false, 0, 0, "version"),
                eintrag("reload", "test.reload", false, 0, 0, "admin", "reload"),
                eintrag("menu", "test.use", true, 0, 1, "menu")), fachlich, fachlich,
                (ziel, key, values) -> { schluessel.add(key); werte.add(values); });
    }

    private void ausfuehren(String... args) { familie.onCommand(sender, command, "test", args); }

    @Test void leererAufrufUndHilfealiasZeigenNurErlaubteBefehle() {
        ausfuehren();
        assertEquals(List.of("standard-kopf", "standard-help", "standard-version", "standard-fuss"), schluessel);
        assertEquals("/test version [Argument]", werte.get(2).get("befehl"));
        schluessel.clear(); ausfuehren("HILFE");
        assertTrue(schluessel.contains("standard-help"));
        verifyNoInteractions(fachlich);
    }

    @Test void rechteSyntaxUndSpielerpflichtWerdenVorDerAusfuehrungGeprueft() {
        ausfuehren("reload"); assertEquals("standard-kein-recht", schluessel.getLast());
        when(sender.hasPermission("test.reload")).thenReturn(true);
        ausfuehren("reload", "zu-viel"); assertEquals("standard-syntax", schluessel.getLast());
        when(sender.hasPermission("test.use")).thenReturn(true);
        ausfuehren("menu"); assertEquals("standard-nur-spieler", schluessel.getLast());
        verifyNoInteractions(fachlich);
        ausfuehren("RELOAD");
        verify(fachlich).onCommand(eq(sender), eq(command), eq("test"), aryEq(new String[]{"admin", "reload"}));
    }

    @Test void unbekannteBefehleUndUngueltigeSeitenLoesenKeineAktionAus() {
        ausfuehren("unbekannt"); assertEquals("standard-unbekannt", schluessel.getLast());
        for (String seite : List.of("0", "-1", "2", "2147483648")) {
            ausfuehren("help", seite);
            assertTrue(List.of("standard-seite", "standard-syntax").contains(schluessel.getLast()));
        }
        verifyNoInteractions(fachlich);
    }

    @Test void tabVerratenKeineGesperrtenBefehleUndFallenNichtAufSpielernamenZurueck() {
        assertEquals(List.of("help", "version"), familie.onTabComplete(sender, command, "test", new String[]{""}));
        assertTrue(familie.onTabComplete(sender, command, "test", new String[]{"reload", ""}).isEmpty());
        assertTrue(familie.onTabComplete(sender, command, "test", new String[]{"version", ""}).isEmpty());
        when(sender.hasPermission("test.use")).thenReturn(true);
        var player = mock(Player.class); when(player.hasPermission("test.use")).thenReturn(true);
        when(fachlich.onTabComplete(eq(player), eq(command), eq("test"), any())).thenReturn(null);
        assertTrue(familie.onTabComplete(player, command, "test", new String[]{"menu", ""}).isEmpty());
        verify(fachlich).onTabComplete(eq(player), eq(command), eq("test"), aryEq(new String[]{"menu", ""}));
        verifyNoMoreInteractions(fachlich);
    }

    @Test void hilfeWirdNachRechtenPaginiertUndBietetNurVorhandeneSeitenAn() {
        var eintraege = new ArrayList<FamilienBefehle.Eintrag>();
        eintraege.add(eintrag("help", "", false, 0, 1, "help"));
        for (int i = 0; i < 20; i++) eintraege.add(eintrag("aktion" + i, "", false, 0, 0, "aktion" + i));
        familie = new FamilienBefehle("test", eintraege, fachlich, fachlich,
                (ziel, key, values) -> { schluessel.add(key); werte.add(values); });
        ausfuehren("help", "3");
        assertEquals(List.of("standard-kopf", "standard-aktion19", "standard-fuss"), schluessel);
        assertEquals("3", werte.getFirst().get("seiten"));
        assertEquals(List.of("1", "2", "3"), familie.onTabComplete(sender, command, "test", new String[]{"help", ""}));
    }

    @Test void rangbuendelUndAusdruecklicheVerboteGeltenAuchFuerHilfeUndTab() {
        familie = new FamilienBefehle("test", List.of(eintrag("help", "", false, 0, 1, "help"),
                eintrag("reload", "test.reload", false, 0, 0, "reload")), fachlich, fachlich,
                (ziel, key, values) -> schluessel.add(key), (ziel, recht) -> recht.equals("test.reload"));
        ausfuehren("help"); assertTrue(schluessel.contains("standard-reload"));
        assertEquals(List.of("reload"), familie.onTabComplete(sender, command, "test", new String[]{"r"}));
        ausfuehren("reload");
        verify(fachlich).onCommand(eq(sender), eq(command), eq("test"), aryEq(new String[]{"reload"}));
    }

    @Test void gemeinsameRechteUndAlternativeRechteWerdenNichtVerwechselt() {
        var eintrag = new FamilienBefehle.Eintrag("test", "test", List.of("basis"), List.of("eins", "zwei"),
                false, 0, 0, List.of("test"), List.of());
        when(sender.hasPermission("basis")).thenReturn(true);
        assertFalse(eintrag.berechtigt(sender, CommandSender::hasPermission));
        when(sender.hasPermission("zwei")).thenReturn(true);
        assertTrue(eintrag.berechtigt(sender, CommandSender::hasPermission));
        when(sender.hasPermission("basis")).thenReturn(false);
        assertFalse(eintrag.berechtigt(sender, CommandSender::hasPermission));
    }
}
