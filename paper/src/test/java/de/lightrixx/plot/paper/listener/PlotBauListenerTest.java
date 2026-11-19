package de.lightrixx.plot.paper.listener;

import de.lightrixx.plot.economy.PlotKauf;
import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.paper.config.PlotKonfiguration;
import de.lightrixx.plot.paper.storage.PlotYamlSpeicher;
import de.lightrixx.plot.paper.storage.WettbewerbYamlSpeicher;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PlotBauListenerTest {
    @TempDir
    Path ordner;
    private final UUID besitzer = UUID.randomUUID();
    private final PlotId id = new PlotId(0, 0);
    private World plotwelt;
    private PlotDienst plots;
    private WettbewerbDienst wettbewerbe;
    private PlotBauListener listener;
    private PlotSchutz schutz;
    private PlotWelt welt;

    @BeforeEach
    void vorbereiten() throws IOException {
        plotwelt = welt(new NamespacedKey("simpleplots", "plots"));
        World hauptwelt = welt(NamespacedKey.minecraft("overworld"));
        var server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, methode, argumente) -> {
                    if (methode.getName().equals("getWorld")) {
                        return hauptwelt.getKey().equals(argumente[0]) ? hauptwelt : null;
                    }
                    throw new UnsupportedOperationException(methode.getName());
                });
        Path config = ordner.resolve("config.yml");
        Files.writeString(config, "raster:\n  plotgroesse: 32\n  wegbreite: 7\nclaims:\n  pro-spieler: 3\n");
        plots = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        plots.claim(id, besitzer, 3);
        plots.claim(new PlotId(1, 0), UUID.randomUUID(), 3);
        var plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        welt = new PlotWelt(plugin, new PlotKonfiguration(config), plots, ordner.resolve("welt.yml"));
        wettbewerbe = new WettbewerbDienst(new WettbewerbYamlSpeicher(ordner.resolve("wettbewerb.yml")), plots);
        listener = new PlotBauListener(welt, plots, wettbewerbe);
        schutz = new PlotSchutz(welt, plots, wettbewerbe);
    }

    @Test
    void erlaubtBesitzernDenGrasbodenUndDenUntergrund() {
        Player spieler = spieler(besitzer, GameMode.SURVIVAL, false);
        for (int y : new int[]{65, 64, 63, 62, 60, 10, -63}) {
            assertFalse(abbauGesperrt(spieler, 5, y, 5), "Y=" + y);
        }
    }

    @Test
    void schuetztDieUntersteSchichtAuchVorAdmins() {
        assertTrue(abbauGesperrt(spieler(besitzer, GameMode.SURVIVAL, false), 5, -64, 5));
        assertTrue(abbauGesperrt(spieler(besitzer, GameMode.CREATIVE, true), 5, -64, 5));
        assertFalse(abbauGesperrt(spieler(besitzer, GameMode.CREATIVE, true), 5, -63, 5));
    }

    @Test
    void schuetztFremdePlotsUndOeffentlicheWege() {
        Player spieler = spieler(besitzer, GameMode.SURVIVAL, false);
        for (int y : new int[]{65, 63, 10, -63}) {
            assertTrue(abbauGesperrt(spieler, 40, y, 5));
            assertTrue(abbauGesperrt(spieler, 35, y, 5));
        }
    }

    @Test
    void laesstStrassenbodenUndUnbeanspruchtenBodenAuchFuerAdminsGesperrt() {
        Player admin = spieler(besitzer, GameMode.CREATIVE, true);
        assertFalse(abbauGesperrt(admin, 5, 63, 5));
        assertFalse(abbauGesperrt(admin, 40, -63, 5));
        assertTrue(abbauGesperrt(admin, 35, 63, 5));
        assertTrue(abbauGesperrt(admin, 35, 10, 5));
        assertTrue(abbauGesperrt(admin, 80, 63, 5));
        assertFalse(abbauGesperrt(admin, 35, 64, 5));
    }

    @Test
    void erlaubtTrustedSpielernDenUntergrund() throws IOException {
        UUID vertrauter = UUID.randomUUID();
        plots.rolleAendern(id, besitzer, vertrauter, Plot.RollenAktion.TRUST);
        Player spieler = spieler(vertrauter, GameMode.SURVIVAL, false);
        assertFalse(abbauGesperrt(spieler, 5, 63, 5));
        assertFalse(abbauGesperrt(spieler, 5, -63, 5));
    }

    @Test
    void gibtDenUntergrundZusammengelegterInnenwegeFrei() throws IOException {
        var nachbar = new PlotId(0, 1);
        plots.claim(nachbar, besitzer, 3);
        plots.zusammenlegen(id, nachbar, besitzer, plot -> false, true);
        Player spieler = spieler(besitzer, GameMode.SURVIVAL, false);
        assertFalse(abbauGesperrt(spieler, 5, 63, 35));
        assertFalse(abbauGesperrt(spieler, 5, -63, 35));
        assertTrue(abbauGesperrt(spieler, 5, -64, 35));
    }

    @Test
    void sperrtWettbewerbsplotsAuchImUntergrund() throws IOException {
        wettbewerbe.starten("Unterirdische Stadt");
        wettbewerbe.einreichen(id, besitzer);
        assertTrue(abbauGesperrt(spieler(besitzer, GameMode.SURVIVAL, false), 5, 63, 5));
        assertTrue(abbauGesperrt(spieler(besitzer, GameMode.SURVIVAL, false), 5, -63, 5));
        assertTrue(abbauGesperrt(spieler(besitzer, GameMode.CREATIVE, true), 5, -63, 5));
    }

    @Test
    void begrenztMechanikenUnterDerOberflaecheAufDenEigenenPlot() {
        var quelle = new Location(plotwelt, 5, 10, 5);
        assertTrue(schutz.selberClaim(quelle, new Location(plotwelt, 5, -63, 6)));
        assertFalse(schutz.selberClaim(quelle, new Location(plotwelt, 40, 10, 5)));
        assertFalse(schutz.selberClaim(quelle, new Location(plotwelt, 35, 10, 5)));
        assertFalse(schutz.selberClaim(quelle, new Location(plotwelt, 5, -64, 5)));
        assertTrue(schutz.blockImClaim(block(5, 10, 5)));
        assertFalse(schutz.blockImClaim(block(5, -64, 5)));
    }

    @Test
    void lehntKreativmodusOhneAdminrechtWeiterhinAb() {
        assertTrue(abbauGesperrt(spieler(besitzer, GameMode.CREATIVE, false), 5, 64, 5));
        assertTrue(abbauGesperrt(spieler(besitzer, GameMode.CREATIVE, false), 5, 63, 5));
    }

    @Test
    void prueftBeiDoppelkistenBeideHaelftenStattNurDenInventarmittelpunkt() {
        var spieler = spieler(besitzer, GameMode.SURVIVAL, false);
        var links = mock(BlockInventoryHolder.class);
        var rechts = mock(BlockInventoryHolder.class);
        when(links.getBlock()).thenReturn(block(5, 64, 5));
        when(rechts.getBlock()).thenReturn(block(35, 64, 5));
        var kiste = mock(DoubleChest.class);
        when(kiste.getLeftSide(false)).thenReturn(links);
        when(kiste.getRightSide(false)).thenReturn(rechts);
        var inventar = mock(Inventory.class);
        when(inventar.getHolder(false)).thenReturn(kiste);
        when(inventar.getLocation()).thenReturn(new Location(plotwelt, 5, 64, 5));
        var event = mock(InventoryOpenEvent.class);
        when(event.getPlayer()).thenReturn(spieler);
        when(event.getInventory()).thenReturn(inventar);
        listener.inventarOeffnen(event);
        verify(event).setCancelled(true);
    }

    @Test
    void begrenztFluessigkeitenUndTrichterAufDenEigenenClaim() {
        var mechanik = new PlotMechanikListener(welt, plots, wettbewerbe);
        var innen = new BlockFromToEvent(block(5, 10, 5), block(6, 10, 5));
        mechanik.fluessigkeit(innen);
        assertFalse(innen.isCancelled());
        var grenze = new BlockFromToEvent(block(31, 10, 5), block(32, 10, 5));
        mechanik.fluessigkeit(grenze);
        assertTrue(grenze.isCancelled());
        var quelle = mock(Inventory.class);
        var ziel = mock(Inventory.class);
        when(quelle.getLocation()).thenReturn(new Location(plotwelt, 5, 10, 5));
        when(ziel.getLocation()).thenReturn(new Location(plotwelt, 40, 10, 5));
        var transfer = new InventoryMoveItemEvent(quelle,
                mock(ItemStack.class), ziel, true);
        mechanik.transfer(transfer);
        assertTrue(transfer.isCancelled());
    }

    @Test
    void sperrtMechanikenAufWettbewerbsplotsUndBehandeltInventareOhneStandort() throws IOException {
        var mechanik = new PlotMechanikListener(welt, plots, wettbewerbe);
        assertFalse(schutz.selberClaim((Location) null, new Location(plotwelt, 5, 64, 5)));
        assertFalse(schutz.selberClaim(new Location(plotwelt, 5, 64, 5), (Location) null));
        wettbewerbe.starten("Museum");
        wettbewerbe.einreichen(id, besitzer);
        var fluss = new BlockFromToEvent(block(5, 10, 5), block(6, 10, 5));
        mechanik.fluessigkeit(fluss);
        assertTrue(fluss.isCancelled());
        var redstone = new BlockRedstoneEvent(block(5, 10, 5), 0, 15);
        mechanik.redstone(redstone);
        assertEquals(0, redstone.getNewCurrent());
    }

    private boolean abbauGesperrt(Player spieler, int x, int y, int z) {
        var event = new BlockBreakEvent(block(x, y, z), spieler);
        listener.abbauen(event);
        return event.isCancelled();
    }

    private Block block(int x, int y, int z) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, methode, argumente) -> switch (methode.getName()) {
                    case "getLocation" -> new Location(plotwelt, x, y, z);
                    case "getWorld" -> plotwelt;
                    case "getY" -> y;
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
    }

    private static Player spieler(UUID uuid, GameMode modus, boolean admin) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, methode, argumente) -> switch (methode.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getGameMode" -> modus;
                    case "hasPermission" -> admin && "simpleplots.admin".equals(argumente[0]);
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
    }

    private static World welt(NamespacedKey key) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, methode, argumente) -> switch (methode.getName()) {
                    case "getKey" -> key;
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
    }
}
