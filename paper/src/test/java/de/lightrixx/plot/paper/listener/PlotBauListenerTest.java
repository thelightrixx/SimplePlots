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
import org.bukkit.block.Lectern;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        Files.writeString(ordner.resolve("welt.yml"), "format: 2\nplotgroesse: 32\nwegbreite: 7\n");
        plots = new PlotDienst(new PlotYamlSpeicher(ordner.resolve("plots.yml")), PlotKauf.kostenlos());
        plots.claim(id, besitzer, 3);
        plots.claim(new PlotId(1, 0), UUID.randomUUID(), 3);
        var plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        welt = new PlotWelt(plugin, new PlotKonfiguration(config, material -> true), plots, ordner.resolve("welt.yml"));
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

    @Test
    void verhindertAutomatischeTransfersUeberEineFremdeDoppelkistenhaelfte() {
        var mechanik = new PlotMechanikListener(welt, plots, wettbewerbe);
        var links = mock(BlockInventoryHolder.class);
        var rechts = mock(BlockInventoryHolder.class);
        when(links.getBlock()).thenReturn(block(31, 64, 5));
        when(rechts.getBlock()).thenReturn(block(32, 64, 5));
        var kiste = mock(DoubleChest.class);
        when(kiste.getLeftSide(false)).thenReturn(links);
        when(kiste.getRightSide(false)).thenReturn(rechts);
        var quelle = mock(Inventory.class);
        when(quelle.getHolder(false)).thenReturn(kiste);
        when(quelle.getLocation()).thenReturn(new Location(plotwelt, 31.5, 64, 5));
        var ziel = mock(Inventory.class);
        when(ziel.getLocation()).thenReturn(new Location(plotwelt, 30, 64, 5));
        var event = new InventoryMoveItemEvent(quelle, mock(ItemStack.class), ziel, true);
        mechanik.transfer(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void verweigertInventarzugriffOhneStandortStattIhnUngeprueftFreizugeben() {
        var inventar = mock(Inventory.class);
        when(inventar.getHolder(false)).thenReturn(mock(BlockInventoryHolder.class));
        var event = mock(InventoryOpenEvent.class);
        when(event.getPlayer()).thenReturn(spieler(besitzer, GameMode.SURVIVAL, false));
        when(event.getInventory()).thenReturn(inventar);
        listener.inventarOeffnen(event);
        verify(event).setCancelled(true);
    }

    @Test
    void prueftDieGesamteEntityFlaecheBeimPlatzieren() {
        var spieler = spieler(besitzer, GameMode.SURVIVAL, false);
        var entity = mock(Entity.class);
        when(entity.getLocation()).thenReturn(new Location(plotwelt, 31, 65, 5));
        when(entity.getBoundingBox()).thenReturn(new BoundingBox(30, 64, 4, 33, 68, 6));
        assertFalse(schutz.darfEntityPlatzieren(spieler, entity));
        when(entity.getBoundingBox()).thenReturn(new BoundingBox(30, 64, 4, 32, 68, 6));
        assertTrue(schutz.darfEntityPlatzieren(spieler, entity));
    }

    @Test
    void sperrtWurftraenkeAufFremdenPlotsUndErlaubtEigeneUndSelbstanwendung() {
        var spieler = spieler(besitzer, GameMode.SURVIVAL, false);
        var eigenesTier = mock(LivingEntity.class);
        var fremdesTier = mock(LivingEntity.class);
        when(eigenesTier.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        when(fremdesTier.getLocation()).thenReturn(new Location(plotwelt, 40, 65, 5));
        var trank = mock(ThrownPotion.class);
        when(trank.getShooter()).thenReturn(spieler);
        var event = mock(PotionSplashEvent.class);
        when(event.getPotion()).thenReturn(trank);
        when(event.getAffectedEntities()).thenReturn(List.of(eigenesTier, fremdesTier, spieler));
        listener.trankWerfen(event);
        verify(event).setIntensity(fremdesTier, 0);
        verify(event, never()).setIntensity(eq(eigenesTier), anyDouble());
        verify(event, never()).setIntensity(eq(spieler), anyDouble());
    }

    @Test
    void sperrtVerweiltraenkeAufFremdenPlotsUndWettbewerbsflaechen() throws IOException {
        var tier = mock(LivingEntity.class);
        when(tier.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        var wolke = mock(AreaEffectCloud.class);
        when(wolke.getSource()).thenReturn(spieler(besitzer, GameMode.SURVIVAL, false));
        var event = mock(AreaEffectCloudApplyEvent.class);
        when(event.getEntity()).thenReturn(wolke);
        var ziele = new ArrayList<>(List.of(tier));
        when(event.getAffectedEntities()).thenReturn(ziele);
        listener.trankWolke(event);
        assertEquals(List.of(tier), ziele);
        wettbewerbe.starten("Tiere");
        wettbewerbe.einreichen(id, besitzer);
        listener.trankWolke(event);
        assertTrue(ziele.isEmpty());
    }

    @Test
    void begrenztDispenserProjektileUndFeuerAufDenEigenenClaim() {
        var quelle = mock(BlockProjectileSource.class);
        when(quelle.getBlock()).thenReturn(block(5, 65, 5));
        var pfeil = mock(Projectile.class);
        when(pfeil.getShooter()).thenReturn(quelle);
        var tier = mock(Entity.class);
        when(tier.getLocation()).thenReturn(new Location(plotwelt, 40, 65, 5));
        var event = mock(EntityCombustByEntityEvent.class);
        when(event.getCombuster()).thenReturn(pfeil);
        when(event.getEntity()).thenReturn(tier);
        listener.entityAnzuenden(event);
        verify(event).setCancelled(true);
        assertTrue(schutz.darfProjektilNutzen(quelle, new Location(plotwelt, 6, 65, 5)));
        assertFalse(schutz.darfProjektilNutzen(quelle, tier.getLocation()));
    }

    @Test
    void prueftAufsitzenAnleinenUndAbleinenAufFremdenPlots() {
        var spieler = spieler(UUID.randomUUID(), GameMode.SURVIVAL, false);
        var tier = mock(Entity.class);
        when(tier.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        var aufsitzen = new EntityMountEvent(spieler, tier);
        listener.aufsitzen(aufsitzen);
        assertTrue(aufsitzen.isCancelled());
        var anleinen = mock(PlayerLeashEntityEvent.class);
        when(anleinen.getPlayer()).thenReturn(spieler);
        when(anleinen.getEntity()).thenReturn(tier);
        listener.anleinen(anleinen);
        verify(anleinen).setCancelled(true);
        var ableinen = mock(PlayerUnleashEntityEvent.class);
        when(ableinen.getPlayer()).thenReturn(spieler);
        when(ableinen.getEntity()).thenReturn(tier);
        listener.ableinen(ableinen);
        verify(ableinen).setCancelled(true);
    }

    @Test
    void prueftLesepultBuchentnahmeErneutNachRechteentzug() throws IOException {
        UUID gast = UUID.randomUUID();
        var spieler = spieler(gast, GameMode.SURVIVAL, false);
        var lesepult = mock(Lectern.class);
        when(lesepult.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        var event = mock(PlayerTakeLecternBookEvent.class);
        when(event.getPlayer()).thenReturn(spieler);
        when(event.getLectern()).thenReturn(lesepult);
        plots.rolleAendern(id, besitzer, gast, Plot.RollenAktion.TRUST);
        listener.lesepultLeeren(event);
        verify(event, never()).setCancelled(true);
        plots.rolleAendern(id, besitzer, gast, Plot.RollenAktion.UNTRUST);
        listener.lesepultLeeren(event);
        verify(event).setCancelled(true);
    }

    @Test
    void verhindertExplosionsschadenAnFahrzeugenAuchOhneAngreifer() {
        var fahrzeug = mock(Vehicle.class);
        when(fahrzeug.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        for (String ursache : List.of("explosion", "player_explosion", "bad_respawn_point")) {
            var typ = (DamageType) Proxy.newProxyInstance(DamageType.class.getClassLoader(), new Class<?>[]{DamageType.class},
                    (proxy, methode, argumente) -> NamespacedKey.minecraft(ursache));
            var quelle = mock(DamageSource.class);
            when(quelle.getDamageType()).thenReturn(typ);
            var event = new VehicleDamageEvent(fahrzeug, quelle, null, 20);
            listener.fahrzeugAngreifen(event);
            assertTrue(event.isCancelled(), ursache);
        }
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
                    case "getPersistentDataContainer" -> mock(org.bukkit.persistence.PersistentDataContainer.class);
                    case "hasPermission" -> admin && java.util.Set.of("simpleplots.admin", "simpleplots.admin.bypass").contains(argumente[0]);
                    case "equals" -> proxy == argumente[0];
                    case "hashCode" -> uuid.hashCode();
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
