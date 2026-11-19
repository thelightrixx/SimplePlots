package de.lightrixx.plot.paper.gui;

import de.lightrixx.plot.model.BauWettbewerb;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotProfil.Kategorie;
import de.lightrixx.plot.model.PlotProfil;
import de.lightrixx.plot.model.PlotRechte.BesucherRecht;
import de.lightrixx.plot.paper.PlotBerechtigungen;
import de.lightrixx.plot.paper.command.PlotAuftraege;
import de.lightrixx.plot.paper.config.PlotMenueKonfiguration;
import de.lightrixx.plot.paper.config.PlotNachrichten;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component;
import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

public final class PlotMenue implements Listener {
    private static final Comparator<PlotId> NACH_ID = Comparator.comparingInt(PlotId::z).thenComparingInt(PlotId::x);
    private final JavaPlugin plugin;
    private final PlotDienst plots;
    private final PlotWelt welt;
    private final PlotNachrichten texte;
    private final WettbewerbDienst wettbewerbe;
    private final PlotMenueKonfiguration konfiguration;
    private final PlotAuftraege auftraege;

    public PlotMenue(JavaPlugin plugin, PlotDienst plots, PlotWelt welt, PlotNachrichten texte, WettbewerbDienst wettbewerbe,
                     PlotMenueKonfiguration konfiguration, PlotAuftraege auftraege) {
        this.plugin = plugin;
        this.plots = plots;
        this.welt = welt;
        this.texte = texte;
        this.wettbewerbe = wettbewerbe;
        this.konfiguration = konfiguration;
        this.auftraege = auftraege;
    }

    public void oeffnen(Player spieler) {
        var menue = new Ansicht(spieler, konfiguration.daten().hauptgroesse(), texte.komponente("menue.titel"));
        var slots = konfiguration.daten().hauptslots();
        knopf(menue, slots.get("eigene"), Material.GRASS_BLOCK, "eigene", klicker -> eigene(klicker, 0));
        knopf(menue, slots.get("galerie"), Material.SPYGLASS, "galerie", klicker -> galerie(klicker, Optional.empty(), Sortierung.NEW));
        knopf(menue, slots.get("karte"), Material.FILLED_MAP, "karte", this::karte);
        knopf(menue, slots.get("wettbewerb"), Material.GOLD_INGOT, "wettbewerb", this::wettbewerb);
        knopf(menue, slots.get("plotwelt"), Material.ENDER_EYE, "plotwelt", klicker -> befehl(klicker, "world", true));
        knopf(menue, slots.get("hauptwelt"), Material.OAK_DOOR, "hauptwelt", klicker -> befehl(klicker, "back", true));
        if (spieler.hasPermission(PlotBerechtigungen.CLAIM)) {
            knopf(menue, slots.get("claim"), Material.COMPASS, "claim", klicker -> befehl(klicker, "auto", true));
        }
        if (spieler.hasPermission(PlotBerechtigungen.MANAGE)) {
            eigenerPlotAmOrt(spieler).ifPresent(plot -> knopf(menue, slots.get("rechte"), Material.TRIPWIRE_HOOK, "rechte",
                    klicker -> rechte(klicker, plot.id())));
        }
        spieler.openInventory(menue.inventar);
    }

    private void eigene(Player spieler, int seite) {
        List<Plot> eigene = plots.vomBesitzer(spieler.getUniqueId());
        int seiteAktiv = gueltigeSeite(seite, eigene.size());
        var menue = seitenAnsicht(spieler, "eigene", eigene.size(), seiteAktiv, neu -> eigene(spieler, neu), this::oeffnen);
        if (eigene.isEmpty()) {
            knopf(menue, leererSlot(), Material.BARRIER, "keine-eigenen", null);
        }
        for (int i = seiteAktiv * seitengroesse(); i < Math.min(eigene.size(), (seiteAktiv + 1) * seitengroesse()); i++) {
            Plot plot = eigene.get(i);
            plotKachel(menue, i % seitengroesse(), symbol("eigenes-plot", Material.GRASS_BLOCK), plot, List.of(),
                    klicker -> befehl(klicker, "visit " + plot.id(), true));
        }
        spieler.openInventory(menue.inventar);
    }

    public void galerie(Player spieler, Optional<Kategorie> kategorie, Sortierung sortierung) {
        galerie(spieler, kategorie, sortierung, 0);
    }

    private void galerie(Player spieler, Optional<Kategorie> kategorie, Sortierung sortierung, int seite) {
        Comparator<Plot> neueste = Comparator.<Plot>comparingLong(plot -> plot.profil().gelistetSeit()).reversed()
                .thenComparing(Plot::id, NACH_ID);
        var komparator = sortierung == Sortierung.POPULAR
                ? Comparator.<Plot>comparingInt(plot -> plot.profil().likes().size()).reversed().thenComparing(neueste)
                : neueste;
        var eintraege = plots.galerie(plot -> PlotBerechtigungen.darfBetreten(spieler, plot)).stream()
                .filter(plot -> kategorie.isEmpty() || plot.profil().kategorie().equals(kategorie))
                .sorted(komparator).toList();
        int seiteAktiv = gueltigeSeite(seite, eintraege.size());
        var menue = seitenAnsicht(spieler, "galerie", eintraege.size(), seiteAktiv,
                neu -> galerie(spieler, kategorie, sortierung, neu), this::oeffnen);
        if (eintraege.isEmpty()) {
            knopf(menue, leererSlot(), Material.BARRIER, "galerie-leer", null);
        }
        eintrag(menue, navigationSlot("filter"), symbol("filter", Material.HOPPER), texte.komponente("menue.filter", component("category", kategorietext(kategorie))),
                List.of(texte.komponente("menue.filter-aktion")), klicker -> galerie(klicker, naechsteKategorie(kategorie), sortierung));
        knopf(menue, navigationSlot("extra"), Material.CLOCK, sortierung == Sortierung.NEW ? "neu" : "beliebt",
                klicker -> galerie(klicker, kategorie, sortierung == Sortierung.NEW ? Sortierung.POPULAR : Sortierung.NEW));
        for (int i = seiteAktiv * seitengroesse(); i < Math.min(eintraege.size(), (seiteAktiv + 1) * seitengroesse()); i++) {
            Plot plot = eintraege.get(i);
            plotKachel(menue, i % seitengroesse(), kategorieIcon(plot.profil().kategorie().orElseThrow()), plot,
                    List.of(texte.komponente("menue.likes", unparsed("count", Integer.toString(plot.profil().likes().size())))),
                    klicker -> befehl(klicker, "visit " + plot.id(), true));
        }
        spieler.openInventory(menue.inventar);
    }

    private void karte(Player spieler) {
        var ort = spieler.getLocation();
        PlotId mitte = welt.istPlotWelt(ort.getWorld()) ? welt.raster().zelleBei(ort.getBlockX(), ort.getBlockZ()) : new PlotId(0, 0);
        var menue = new Ansicht(spieler, 54, texte.komponente("menue.karte"));
        for (int z = -2; z <= 2; z++) {
            for (int x = -3; x <= 3; x++) {
                PlotId zelle = mitte.verschoben(x, z);
                int slot = (z + 2) * 9 + x + 4;
                Plot plot = plots.finden(zelle).orElse(null);
                boolean istMitte = zelle.equals(mitte);
                if (plot == null) {
                    eintrag(menue, slot, istMitte ? symbol("karte-mitte", Material.COMPASS) : symbol("karte-frei", Material.GRAY_STAINED_GLASS_PANE),
                            texte.komponente("menue.karte-frei", unparsed("id", zelle.toString())), List.of(), null);
                } else {
                    boolean darfBesuchen = PlotBerechtigungen.darfBetreten(spieler, plot);
                    Material icon = istMitte ? symbol("karte-mitte", Material.COMPASS) : !darfBesuchen ? symbol("karte-gebannt", Material.RED_STAINED_GLASS_PANE)
                            : plot.besitzer().equals(spieler.getUniqueId()) ? symbol("karte-eigen", Material.LIME_STAINED_GLASS_PANE)
                            : symbol("karte-fremd", Material.BLUE_STAINED_GLASS_PANE);
                    plotKachel(menue, slot, icon, plot, List.of(texte.komponente("menue.zelle", unparsed("id", zelle.toString()))),
                            darfBesuchen ? klicker -> befehl(klicker, "visit " + zelle, true) : null);
                }
            }
        }
        knopf(menue, 49, Material.OAK_DOOR, "zurueck", this::oeffnen);
        spieler.openInventory(menue.inventar);
    }

    private void rechte(Player spieler, PlotId erwartet) {
        var plot = verwalteterPlot(spieler, erwartet);
        if (plot.isEmpty()) {
            return;
        }
        var menue = new Ansicht(spieler, konfiguration.daten().rechtegroesse(), texte.komponente("menue.rechte"));
        var slots = konfiguration.daten().rechteslots();
        for (Mitgliedsrolle rolle : Mitgliedsrolle.values()) {
            knopf(menue, slots.get(rolle.schluessel), rolle.icon, rolle.schluessel,
                    klicker -> mitglieder(klicker, erwartet, rolle, 0));
        }
        besucherKnopf(menue, slots.get("doors"), symbol("doors", Material.OAK_DOOR), plot.get(), BesucherRecht.DOORS);
        besucherKnopf(menue, slots.get("switches"), symbol("switches", Material.LEVER), plot.get(), BesucherRecht.SWITCHES);
        knopf(menue, slots.get("zurueck"), Material.ARROW, "zurueck", this::oeffnen);
        spieler.openInventory(menue.inventar);
    }

    private void besucherKnopf(Ansicht menue, int slot, Material icon, Plot plot, BesucherRecht flag) {
        boolean erlaubt = plot.rechte().besucher().contains(flag);
        String name = flag.name().toLowerCase(Locale.ROOT);
        eintrag(menue, slot, icon, texte.komponente("menue." + name),
                List.of(texte.komponente(erlaubt ? "menue.erlaubt" : "menue.gesperrt")), klicker -> {
                    var aktuell = verwalteterPlot(klicker, plot.id());
                    if (aktuell.isPresent()) {
                        boolean jetztErlaubt = aktuell.get().rechte().besucher().contains(flag);
                        verwaltungsbefehl(klicker, "flag " + name + (jetztErlaubt ? " off" : " on"),
                                () -> rechte(klicker, plot.id()));
                    }
                });
    }

    private void mitglieder(Player spieler, PlotId erwartet, Mitgliedsrolle rolle, int seite) {
        var plot = verwalteterPlot(spieler, erwartet);
        if (plot.isEmpty()) {
            return;
        }
        Set<UUID> mitglieder = rolle.mitglieder(plot.get());
        var kandidaten = new HashSet<>(mitglieder);
        plugin.getServer().getOnlinePlayers().stream().filter(spieler::canSee).map(Player::getUniqueId)
                .filter(id -> !id.equals(spieler.getUniqueId())).forEach(kandidaten::add);
        var liste = kandidaten.stream().map(id -> new SpielerEintrag(id, spielername(id)))
                .sorted(Comparator.comparing(SpielerEintrag::name, String.CASE_INSENSITIVE_ORDER)).toList();
        int seiteAktiv = gueltigeSeite(seite, liste.size());
        var menue = seitenAnsicht(spieler, rolle.schluessel, liste.size(), seiteAktiv,
                neu -> mitglieder(spieler, erwartet, rolle, neu), klicker -> rechte(klicker, erwartet));
        if (liste.isEmpty()) {
            knopf(menue, leererSlot(), Material.BARRIER, "keine-mitglieder", null);
        }
        for (int i = seiteAktiv * seitengroesse(); i < Math.min(liste.size(), (seiteAktiv + 1) * seitengroesse()); i++) {
            var ziel = liste.get(i);
            boolean gesetzt = mitglieder.contains(ziel.id());
            eintrag(menue, i % seitengroesse(), gesetzt ? symbol("mitglied", Material.LIME_DYE) : symbol("kandidat", Material.GRAY_DYE),
                    texte.komponente("menue.spieler", unparsed("player", ziel.name())),
                    List.of(texte.komponente(gesetzt ? "menue.entziehen" : "menue.erteilen")), klicker -> {
                        var aktuell = verwalteterPlot(klicker, erwartet);
                        if (aktuell.isPresent() && rolle.mitglieder(aktuell.get()).contains(ziel.id()) == gesetzt) {
                            verwaltungsbefehl(klicker, (gesetzt ? rolle.entziehen : rolle.erteilen) + " " + ziel.id(),
                                    () -> mitglieder(klicker, erwartet, rolle, seiteAktiv));
                        }
                    });
        }
        spieler.openInventory(menue.inventar);
    }

    public void wettbewerb(Player spieler) {
        wettbewerb(spieler, 0);
    }

    private void wettbewerb(Player spieler, int seite) {
        var wettbewerb = wettbewerbe.aktuell();
        Map<PlotId, Integer> stimmen = new HashMap<>();
        if (wettbewerb.filter(wert -> wert.phase() == BauWettbewerb.Phase.BEENDET).isPresent()) {
            wettbewerb.get().ergebnisse().forEach(platz -> stimmen.put(platz.plot(), platz.stimmen()));
        }
        List<PlotId> eintraege = wettbewerb.stream().flatMap(wert -> wert.beitraege().keySet().stream())
                .filter(id -> plots.finden(id).filter(plot -> PlotBerechtigungen.darfBetreten(spieler, plot)).isPresent())
                .sorted(Comparator.<PlotId>comparingInt(id -> stimmen.getOrDefault(id, 0)).reversed().thenComparing(NACH_ID)).toList();
        int seiteAktiv = gueltigeSeite(seite, eintraege.size());
        var menue = seitenAnsicht(spieler, "wettbewerb", eintraege.size(), seiteAktiv,
                neu -> wettbewerb(spieler, neu), this::oeffnen);
        if (eintraege.isEmpty()) {
            knopf(menue, leererSlot(), Material.BARRIER, "wettbewerb-leer", null);
        }
        wettbewerb.ifPresent(wert -> eintrag(menue, navigationSlot("extra"), symbol("thema", Material.BOOK),
                texte.komponente("menue.thema", unparsed("theme", wert.thema())),
                List.of(texte.komponente("wettbewerb.phase." + wert.phase().name().toLowerCase(Locale.ROOT))), null));
        for (int i = seiteAktiv * seitengroesse(); i < Math.min(eintraege.size(), (seiteAktiv + 1) * seitengroesse()); i++) {
            PlotId id = eintraege.get(i);
            Plot plot = plots.finden(id).orElseThrow();
            var zusaetze = stimmen.containsKey(id) ? List.of(texte.komponente("menue.stimmen", unparsed("count", stimmen.get(id).toString())))
                    : List.<Component>of();
            plotKachel(menue, i % seitengroesse(), symbol("beitrag", Material.GOLD_INGOT), plot, zusaetze,
                    klicker -> befehl(klicker, "visit " + id, true));
        }
        spieler.openInventory(menue.inventar);
    }

    private Ansicht seitenAnsicht(Player spieler, String titel, int anzahl, int seite, IntConsumer wechseln, Consumer<Player> zurueck) {
        var menue = new Ansicht(spieler, seitengroesse() + 9, texte.komponente("menue." + titel));
        if (seite > 0) {
            knopf(menue, navigationSlot("vorherige"), Material.ARROW, "vorherige", klicker -> wechseln.accept(seite - 1));
        }
        if ((seite + 1) * seitengroesse() < anzahl) {
            knopf(menue, navigationSlot("naechste"), Material.ARROW, "naechste", klicker -> wechseln.accept(seite + 1));
        }
        knopf(menue, navigationSlot("zurueck"), Material.OAK_DOOR, "zurueck", zurueck);
        return menue;
    }

    private void plotKachel(Ansicht menue, int slot, Material icon, Plot plot, List<Component> zusaetze, Consumer<Player> aktion) {
        PlotProfil profil = plot.profil();
        Component titel = profil.name().isEmpty() ? texte.komponente("menue.plot", unparsed("id", plot.id().toString()))
                : texte.komponente("menue.plot-name", unparsed("name", profil.name()));
        var lore = new ArrayList<Component>();
        lore.add(texte.komponente("menue.plot-besitz", unparsed("owner", spielername(plot.besitzer())), unparsed("id", plot.id().toString())));
        profil.kategorie().ifPresent(kategorie -> lore.add(kategorietext(Optional.of(kategorie))));
        String beschreibung = profil.beschreibung();
        for (int von = 0; von < beschreibung.length();) {
            int bis = beschreibung.offsetByCodePoints(von, Math.min(42, beschreibung.codePointCount(von, beschreibung.length())));
            lore.add(texte.komponente("menue.beschreibung", unparsed("description", beschreibung.substring(von, bis))));
            von = bis;
        }
        lore.addAll(zusaetze);
        eintrag(menue, slot, icon, titel, lore, aktion);
    }

    private Optional<Plot> eigenerPlotAmOrt(Player spieler) {
        return welt.plotBei(spieler.getLocation()).flatMap(plots::finden).filter(plot -> plot.besitzer().equals(spieler.getUniqueId()));
    }

    private Optional<Plot> verwalteterPlot(Player spieler, PlotId id) {
        var plot = eigenerPlotAmOrt(spieler).filter(treffer -> treffer.id().equals(id) && spieler.hasPermission(PlotBerechtigungen.MANAGE));
        if (plot.isEmpty()) {
            texte.senden(spieler, "menue.verwaltung-ort");
        }
        return plot;
    }

    private String spielername(UUID id) {
        return Optional.ofNullable(plugin.getServer().getOfflinePlayer(id).getName()).orElseGet(id::toString);
    }

    private Component kategorietext(Optional<Kategorie> kategorie) {
        return texte.komponente("kategorie." + kategorie.map(wert -> wert.name().toLowerCase(Locale.ROOT)).orElse("all"));
    }

    private static Optional<Kategorie> naechsteKategorie(Optional<Kategorie> kategorie) {
        Kategorie[] werte = Kategorie.values();
        int index = kategorie.map(wert -> wert.ordinal() + 1).orElse(0);
        return index < werte.length ? Optional.of(werte[index]) : Optional.empty();
    }

    private Material kategorieIcon(Kategorie kategorie) {
        return symbol("kategorie." + kategorie.name().toLowerCase(Locale.ROOT), switch (kategorie) {
            case BUILD -> Material.BRICKS;
            case SHOP -> Material.EMERALD;
            case MUSEUM -> Material.PAINTING;
            case PARKOUR -> Material.LEATHER_BOOTS;
            case FARM -> Material.WHEAT;
        });
    }

    private int gueltigeSeite(int seite, int anzahl) {
        return Math.max(0, Math.min(seite, Math.max(0, (anzahl - 1) / seitengroesse())));
    }

    private void knopf(Ansicht menue, int slot, Material icon, String schluessel, Consumer<Player> aktion) {
        eintrag(menue, slot, symbol(schluessel, icon), texte.komponente("menue." + schluessel), List.of(), aktion);
    }

    private static void eintrag(Ansicht menue, int slot, Material icon, Component titel, List<Component> lore, Consumer<Player> aktion) {
        var item = new ItemStack(icon);
        item.editMeta(meta -> {
            meta.itemName(titel);
            meta.lore(lore.stream().map(zeile -> zeile.decoration(TextDecoration.ITALIC, false)).toList());
        });
        menue.inventar.setItem(slot, item);
        if (aktion != null) {
            menue.aktionen.put(slot, aktion);
        }
    }

    private void befehl(Player spieler, String befehl, boolean schliessen) {
        if (schliessen) {
            spieler.closeInventory();
        }
        spieler.performCommand(plugin.getName().toLowerCase(Locale.ROOT) + ":plot " + befehl);
    }

    private int seitengroesse() {
        return konfiguration.daten().seitengroesse();
    }

    private int navigationSlot(String name) {
        return konfiguration.daten().navigationSlot(name);
    }

    private int leererSlot() {
        return (seitengroesse() / 9 / 2) * 9 + 4;
    }

    private Material symbol(String name, Material standard) {
        return konfiguration.symbol(name, standard);
    }

    private void verwaltungsbefehl(Player spieler, String befehl, Runnable aktualisierung) {
        Inventory vorher = spieler.getOpenInventory().getTopInventory();
        befehl(spieler, befehl, false);
        auftraege.nachAbschluss(spieler, () -> {
            if (spieler.isOnline() && spieler.getOpenInventory().getTopInventory() == vorher) {
                aktualisierung.run();
            }
        });
    }

    public void schliessen() {
        for (Player spieler : plugin.getServer().getOnlinePlayers()) {
            if (spieler.getOpenInventory().getTopInventory().getHolder(false) instanceof Ansicht) {
                spieler.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void klicken(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Ansicht menue)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player spieler) || !menue.betrachter.equals(spieler.getUniqueId())
                || event.getClick() != ClickType.LEFT || menue.klickGeplant) {
            return;
        }
        var aktion = menue.aktionen.get(event.getRawSlot());
        if (aktion != null) {
            menue.klickGeplant = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                menue.klickGeplant = false;
                if (spieler.isOnline() && spieler.hasPermission(PlotBerechtigungen.USE)
                        && spieler.getOpenInventory().getTopInventory().getHolder(false) == menue) {
                    aktion.accept(spieler);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ziehen(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Ansicht) {
            event.setCancelled(true);
        }
    }

    private final class Ansicht implements InventoryHolder {
        private final UUID betrachter;
        private final Inventory inventar;
        private final Map<Integer, Consumer<Player>> aktionen = new HashMap<>();
        private boolean klickGeplant;

        private Ansicht(Player spieler, int groesse, Component titel) {
            betrachter = spieler.getUniqueId();
            inventar = plugin.getServer().createInventory(this, groesse, titel);
        }

        @Override
        public Inventory getInventory() {
            return inventar;
        }
    }

    private record SpielerEintrag(UUID id, String name) {
    }

    private enum Mitgliedsrolle {
        TRUSTED("trusted", "trust", "untrust", Material.IRON_PICKAXE),
        BAUHELFER("bauhelfer", "add", "unadd", Material.WOODEN_AXE),
        LAGER("lager", "storage", "unstore", Material.CHEST);

        private final String schluessel;
        private final String erteilen;
        private final String entziehen;
        private final Material icon;

        Mitgliedsrolle(String schluessel, String erteilen, String entziehen, Material icon) {
            this.schluessel = schluessel;
            this.erteilen = erteilen;
            this.entziehen = entziehen;
            this.icon = icon;
        }

        private Set<UUID> mitglieder(Plot plot) {
            return switch (this) {
                case TRUSTED -> plot.rechte().trusted();
                case BAUHELFER -> plot.rechte().bauhelfer();
                case LAGER -> plot.rechte().lager();
            };
        }
    }

    public enum Sortierung {
        NEW, POPULAR
    }
}
