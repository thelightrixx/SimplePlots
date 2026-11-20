package de.lightrixx.plot.paper.listener;

import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;

public final class PlotMechanikListener implements Listener {
    private final PlotWelt welt;
    private final PlotSchutz schutz;

    public PlotMechanikListener(PlotWelt welt, PlotDienst plots, WettbewerbDienst wettbewerbe) {
        this.welt = welt;
        this.schutz = new PlotSchutz(welt, plots, wettbewerbe);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fluessigkeit(BlockFromToEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.selberClaim(event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void schwamm(SpongeAbsorbEvent event) {
        Block schwamm = event.getBlock();
        if (schutz.inPlotwelt(schwamm) && event.getBlocks().stream()
                .anyMatch(block -> !schutz.selberClaim(schwamm.getLocation(), block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void kolbenAusfahren(BlockPistonExtendEvent event) {
        Block kolben = event.getBlock();
        BlockFace richtung = event.getDirection();
        if (schutz.inPlotwelt(kolben) && kolbenGesperrt(kolben, richtung, richtung, event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void kolbenEinfahren(BlockPistonRetractEvent event) {
        Block kolben = event.getBlock();
        if (schutz.inPlotwelt(kolben) && kolben.getBlockData() instanceof Directional ausrichtung) {
            BlockFace kopf = ausrichtung.getFacing();
            if (kolbenGesperrt(kolben, kopf, kopf.getOppositeFace(), event.getBlocks())) {
                event.setCancelled(true);
            }
        }
    }

    private boolean kolbenGesperrt(Block kolben, BlockFace kopf, BlockFace bewegung, List<Block> bloecke) {
        return !schutz.selberClaim(kolben, kolben.getRelative(kopf)) || bloecke.stream().anyMatch(block ->
                !schutz.selberClaim(kolben, block) || !schutz.selberClaim(kolben, block.getRelative(bewegung)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void auswerfen(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (schutz.inPlotwelt(block) && block.getBlockData() instanceof Directional ausrichtung
                && !schutz.selberClaim(block, block.getRelative(ausrichtung.getFacing()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void transfer(InventoryMoveItemEvent event) {
        var quelle = event.getSource().getLocation();
        var ziel = event.getDestination().getLocation();
        if ((schutz.inPlotwelt(quelle) || schutz.inPlotwelt(ziel))
                && (!schutz.inventarImClaim(quelle, event.getSource())
                || !schutz.inventarImClaim(quelle, event.getDestination()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void trichter(InventoryPickupItemEvent event) {
        var item = event.getItem().getLocation();
        if ((schutz.inPlotwelt(item) || schutz.inPlotwelt(event.getInventory().getLocation()))
                && !schutz.inventarImClaim(item, event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void strukturWachsen(StructureGrowEvent event) {
        var quelle = event.getLocation();
        if (schutz.inPlotwelt(quelle) && event.getBlocks().stream()
                .anyMatch(block -> !schutz.selberClaim(quelle, block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void duengen(BlockFertilizeEvent event) {
        Block quelle = event.getBlock();
        if (schutz.inPlotwelt(quelle) && event.getBlocks().stream()
                .anyMatch(block -> !schutz.selberClaim(quelle.getLocation(), block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ausbreiten(BlockSpreadEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.selberClaim(event.getSource(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bilden(BlockFormEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.blockImClaim(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void wachsen(BlockGrowEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.blockImClaim(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void laubVergehen(LeavesDecayEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.blockImClaim(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void feuchtigkeitAendern(MoistureChangeEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.blockImClaim(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityInteragiert(EntityInteractEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.blockImClaim(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void redstone(BlockRedstoneEvent event) {
        if (schutz.wettbewerbGesperrt(event.getBlock())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bildenDurchEntity(EntityBlockFormEvent event) {
        bilden(event);
        if (event.getEntity() instanceof Player spieler
                && !schutz.darfBlockAendern(spieler, event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void vergehen(BlockFadeEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.blockImClaim(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entzuenden(BlockIgniteEvent event) {
        Block ziel = event.getBlock();
        if (!schutz.inPlotwelt(ziel)) {
            return;
        }
        boolean erlaubt = event.getPlayer() != null ? schutz.darfBlockAendern(event.getPlayer(), ziel.getLocation())
                : schutz.selberClaim(event.getIgnitingBlock(), ziel);
        if (!erlaubt) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void brennen(BlockBurnEvent event) {
        if (schutz.inPlotwelt(event.getBlock()) && !schutz.selberClaim(event.getIgnitingBlock(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityAendertBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (!schutz.inPlotwelt(block)) {
            return;
        }
        boolean erlaubt = switch (event.getEntity()) {
            case Player spieler -> schutz.darfBlockAendern(spieler, block.getLocation());
            case FallingBlock fallend -> {
                Location ursprung = fallend.getOrigin();
                yield schutz.selberClaim(ursprung == null ? fallend.getLocation() : ursprung, block.getLocation());
            }
            case Projectile projektil -> schutz.darfProjektilNutzen(projektil.getShooter(), block.getLocation());
            case null, default -> schutz.blockImClaim(block);
        };
        if (!erlaubt) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(EntityExplodeEvent event) {
        if (schutz.inPlotwelt(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosionVorbereiten(ExplosionPrimeEvent event) {
        if (schutz.inPlotwelt(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosionsschaden(EntityDamageEvent event) {
        if ((event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
                && schutz.inPlotwelt(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosionsRueckstoss(EntityKnockbackEvent event) {
        if (event.getCause() == EntityKnockbackEvent.Cause.EXPLOSION
                && schutz.inPlotwelt(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosionEntferntDekoration(HangingBreakEvent event) {
        if (event.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION
                && schutz.inPlotwelt(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explosion(BlockExplodeEvent event) {
        if (schutz.inPlotwelt(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portalErzeugen(PortalCreateEvent event) {
        if (welt.istPlotWelt(event.getWorld())) {
            event.setCancelled(true);
        }
    }
}
