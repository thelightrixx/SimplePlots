package de.lightrixx.plot.paper.listener;

import de.lightrixx.plot.model.Plot;
import de.lightrixx.plot.model.PlotId;
import de.lightrixx.plot.model.PlotRaster;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PlotMechanikListenerTest {
    private final World plotwelt = mock(World.class);
    private final PlotWelt welt = mock(PlotWelt.class);
    private final PlotDienst plots = mock(PlotDienst.class);
    private final WettbewerbDienst wettbewerbe = mock(WettbewerbDienst.class);
    private final PlotId id = new PlotId(0, 0);
    private final PlotMechanikListener listener = new PlotMechanikListener(welt, plots, wettbewerbe);

    @BeforeEach
    void vorbereiten() {
        var config = mock(de.lightrixx.plot.paper.config.PlotKonfiguration.class);
        when(config.aktiv(anyString())).thenReturn(true);
        when(welt.konfiguration()).thenReturn(config);
        when(plotwelt.getMinHeight()).thenReturn(-64);
        when(welt.istPlotWelt(any())).thenAnswer(aufruf -> aufruf.getArgument(0) == plotwelt);
        var raster = new PlotRaster(32, 7);
        when(welt.plotBei(any())).thenAnswer(aufruf -> {
            Location ort = aufruf.getArgument(0);
            return ort != null && ort.getWorld() == plotwelt ? raster.plotBei(ort.getBlockX(), ort.getBlockZ()) : Optional.empty();
        });
        var plot = Plot.neu(id, UUID.randomUUID());
        when(plots.finden(id)).thenReturn(Optional.of(plot));
    }

    @Test
    void prueftKolbenkopfUndJedenVerschobenenBlockAnDerGrenze() {
        var innen = new BlockPistonExtendEvent(block(5), List.of(block(6)), BlockFace.EAST);
        listener.kolbenAusfahren(innen);
        assertFalse(innen.isCancelled());
        var grenze = new BlockPistonExtendEvent(block(30), List.of(block(31)), BlockFace.EAST);
        listener.kolbenAusfahren(grenze);
        assertTrue(grenze.isCancelled());
        var zurueck = new BlockPistonRetractEvent(block(30), List.of(block(32)), BlockFace.WEST);
        listener.kolbenEinfahren(zurueck);
        assertTrue(zurueck.isCancelled());
    }

    @Test
    void deaktiviertNatuerlicheFeuerentstehungUndErlaubtBerechtigtenSpielernDasEntzuenden() {
        when(welt.konfiguration().aktiv("protection.feuerausbreitung")).thenReturn(false);
        Block ziel = block(5);
        Block quelle = block(6);
        UUID besitzer = plots.finden(id).orElseThrow().besitzer();
        var natuerlich = mock(BlockIgniteEvent.class);
        when(natuerlich.getBlock()).thenReturn(ziel);
        when(natuerlich.getIgnitingBlock()).thenReturn(quelle);
        listener.entzuenden(natuerlich);
        verify(natuerlich).setCancelled(true);
        var spieler = mock(Player.class);
        when(spieler.getUniqueId()).thenReturn(besitzer);
        when(spieler.getGameMode()).thenReturn(GameMode.SURVIVAL);
        var manuell = mock(BlockIgniteEvent.class);
        when(manuell.getBlock()).thenReturn(ziel);
        when(manuell.getPlayer()).thenReturn(spieler);
        listener.entzuenden(manuell);
        verify(manuell, never()).setCancelled(true);
    }

    @Test
    void verhindertDispenserSchwammUndDuengerUeberPlotgrenzen() {
        var auswerfen = mock(BlockDispenseEvent.class);
        doReturn(block(31)).when(auswerfen).getBlock();
        listener.auswerfen(auswerfen);
        verify(auswerfen).setCancelled(true);
        var ziel = mock(BlockState.class);
        when(ziel.getLocation()).thenReturn(new Location(plotwelt, 32, 10, 5));
        var schwamm = new SpongeAbsorbEvent(block(31), List.of(ziel));
        listener.schwamm(schwamm);
        assertTrue(schwamm.isCancelled());
        var duenger = mock(BlockFertilizeEvent.class);
        doReturn(block(31)).when(duenger).getBlock();
        when(duenger.getBlocks()).thenReturn(List.of(ziel));
        listener.duengen(duenger);
        verify(duenger).setCancelled(true);
    }

    @Test
    void verhindertAusbreitungUndFeuerUeberDieGrenze() {
        var ausbreiten = mock(BlockSpreadEvent.class);
        doReturn(block(31)).when(ausbreiten).getSource();
        doReturn(block(32)).when(ausbreiten).getBlock();
        listener.ausbreiten(ausbreiten);
        verify(ausbreiten).setCancelled(true);
        var feuer = mock(BlockIgniteEvent.class);
        doReturn(block(32)).when(feuer).getBlock();
        doReturn(block(31)).when(feuer).getIgnitingBlock();
        listener.entzuenden(feuer);
        verify(feuer).setCancelled(true);
        var brand = mock(BlockBurnEvent.class);
        doReturn(block(32)).when(brand).getBlock();
        doReturn(block(31)).when(brand).getIgnitingBlock();
        listener.brennen(brand);
        verify(brand).setCancelled(true);
    }

    @Test
    void friertPassiveAenderungenAufWegenUndWettbewerbsflaechenEin() {
        var bilden = mock(BlockFormEvent.class);
        doReturn(block(35)).when(bilden).getBlock();
        listener.bilden(bilden);
        verify(bilden).setCancelled(true);
        var vergehen = mock(BlockFadeEvent.class);
        doReturn(block(35)).when(vergehen).getBlock();
        listener.vergehen(vergehen);
        verify(vergehen).setCancelled(true);
        var entity = mock(EntityChangeBlockEvent.class);
        doReturn(block(35)).when(entity).getBlock();
        listener.entityAendertBlock(entity);
        verify(entity).setCancelled(true);
        when(wettbewerbe.hatGesperrtePlots()).thenReturn(true);
        when(wettbewerbe.gesperrt(id)).thenReturn(true);
        var wachsen = mock(BlockGrowEvent.class);
        doReturn(block(5)).when(wachsen).getBlock();
        listener.wachsen(wachsen);
        verify(wachsen).setCancelled(true);
    }

    @Test
    void verhindertExplosionenUndPortaleNurInDerPlotwelt() {
        var explosion = mock(EntityExplodeEvent.class);
        when(explosion.getLocation()).thenReturn(new Location(plotwelt, 5, 10, 5));
        listener.explosion(explosion);
        verify(explosion).setCancelled(true);
        var blockExplosion = mock(BlockExplodeEvent.class);
        doReturn(block(5)).when(blockExplosion).getBlock();
        listener.explosion(blockExplosion);
        verify(blockExplosion).setCancelled(true);
        var portal = mock(PortalCreateEvent.class);
        when(portal.getWorld()).thenReturn(plotwelt);
        listener.portalErzeugen(portal);
        verify(portal).setCancelled(true);
        var fremdesPortal = mock(PortalCreateEvent.class);
        when(fremdesPortal.getWorld()).thenReturn(mock(World.class));
        listener.portalErzeugen(fremdesPortal);
        verify(fremdesPortal, never()).setCancelled(true);
    }

    @Test
    void prueftSpielerAuchBeiIndirektenBlockaenderungen() {
        var spieler = mock(Player.class);
        when(spieler.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spieler.getGameMode()).thenReturn(GameMode.SURVIVAL);
        var event = mock(EntityChangeBlockEvent.class);
        doReturn(block(5)).when(event).getBlock();
        when(event.getEntity()).thenReturn(spieler);
        listener.entityAendertBlock(event);
        verify(event).setCancelled(true);
    }

    @Test
    void verhindertFallendeBloeckeAusEinemAnderenPlot() {
        var sand = mock(FallingBlock.class);
        when(sand.getOrigin()).thenReturn(new Location(plotwelt, 40, 20, 5));
        var event = mock(EntityChangeBlockEvent.class);
        doReturn(block(5)).when(event).getBlock();
        when(event.getEntity()).thenReturn(sand);
        listener.entityAendertBlock(event);
        verify(event).setCancelled(true);
    }

    @Test
    void erlaubtDasEntstehenUndLandenFallenderBloeckeImEigenenClaim() {
        var sand = mock(FallingBlock.class);
        when(sand.getLocation()).thenReturn(new Location(plotwelt, 5, 20, 5));
        var event = mock(EntityChangeBlockEvent.class);
        doReturn(block(5)).when(event).getBlock();
        when(event.getEntity()).thenReturn(sand);
        listener.entityAendertBlock(event);
        verify(event, never()).setCancelled(true);
        when(sand.getOrigin()).thenReturn(new Location(plotwelt, 6, 20, 5));
        listener.entityAendertBlock(event);
        verify(event, never()).setCancelled(true);
    }

    @Test
    void friertLaubFeuchtigkeitUndEntityInteraktionenAufWettbewerbsplotsEin() {
        when(wettbewerbe.gesperrt(id)).thenReturn(true);
        var laub = new LeavesDecayEvent(block(5));
        listener.laubVergehen(laub);
        assertTrue(laub.isCancelled());
        var feuchtigkeit = new MoistureChangeEvent(block(5), mock(BlockState.class));
        listener.feuchtigkeitAendern(feuchtigkeit);
        assertTrue(feuchtigkeit.isCancelled());
        var interaktion = new EntityInteractEvent(mock(Entity.class), block(5));
        listener.entityInteragiert(interaktion);
        assertTrue(interaktion.isCancelled());
    }

    @Test
    void laesstPassiveVorgaengeAufFreienClaimsWeiterlaufen() {
        var laub = new LeavesDecayEvent(block(5));
        listener.laubVergehen(laub);
        assertFalse(laub.isCancelled());
        var feuchtigkeit = new MoistureChangeEvent(block(5), mock(BlockState.class));
        listener.feuchtigkeitAendern(feuchtigkeit);
        assertFalse(feuchtigkeit.isCancelled());
        var interaktion = new EntityInteractEvent(mock(Entity.class), block(5));
        listener.entityInteragiert(interaktion);
        assertFalse(interaktion.isCancelled());
    }

    @Test
    void verhindertExplosionenSchonVorDemSchadenUndSchuetztDekoration() {
        var entity = mock(Entity.class);
        when(entity.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        var prime = new ExplosionPrimeEvent(entity, 4, false);
        listener.explosionVorbereiten(prime);
        assertTrue(prime.isCancelled());
        for (var ursache : List.of(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) {
            var schaden = mock(EntityDamageEvent.class);
            when(schaden.getCause()).thenReturn(ursache);
            when(schaden.getEntity()).thenReturn(entity);
            listener.explosionsschaden(schaden);
            verify(schaden).setCancelled(true);
        }
        var bild = mock(Hanging.class);
        when(bild.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        var abhaengen = new HangingBreakEvent(bild, HangingBreakEvent.RemoveCause.EXPLOSION);
        listener.explosionEntferntDekoration(abhaengen);
        assertTrue(abhaengen.isCancelled());
        var rueckstoss = new EntityKnockbackEvent(entity, EntityKnockbackEvent.Cause.EXPLOSION, new Vector(1, 1, 1));
        listener.explosionsRueckstoss(rueckstoss);
        assertTrue(rueckstoss.isCancelled());
    }

    @Test
    void laesstAndereSchadensursachenUndExplosionenInDerHauptweltUnveraendert() {
        var entity = mock(Entity.class);
        when(entity.getLocation()).thenReturn(new Location(mock(World.class), 5, 65, 5));
        var prime = new ExplosionPrimeEvent(entity, 4, false);
        listener.explosionVorbereiten(prime);
        assertFalse(prime.isCancelled());
        var schaden = mock(EntityDamageEvent.class);
        when(schaden.getEntity()).thenReturn(entity);
        when(schaden.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);
        listener.explosionsschaden(schaden);
        verify(schaden, never()).setCancelled(true);
        when(entity.getLocation()).thenReturn(new Location(plotwelt, 5, 65, 5));
        when(schaden.getCause()).thenReturn(EntityDamageEvent.DamageCause.FALL);
        listener.explosionsschaden(schaden);
        verify(schaden, never()).setCancelled(true);
    }

    private Block block(int x) {
        var block = mock(Block.class);
        when(block.getWorld()).thenReturn(plotwelt);
        when(block.getLocation()).thenReturn(new Location(plotwelt, x, 10, 5));
        when(block.getRelative(any(BlockFace.class))).thenAnswer(aufruf -> {
            BlockFace richtung = aufruf.getArgument(0);
            return block(x + richtung.getModX());
        });
        var richtung = mock(Directional.class);
        when(richtung.getFacing()).thenReturn(BlockFace.EAST);
        when(block.getBlockData()).thenReturn(richtung);
        return block;
    }
}
