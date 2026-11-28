package de.lightrixx.plot.paper.listener;

import de.lightrixx.plot.model.PlotRechte.BesucherRecht;
import de.lightrixx.plot.paper.world.PlotWelt;
import de.lightrixx.plot.service.PlotDienst;
import de.lightrixx.plot.service.WettbewerbDienst;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import de.lightrixx.plot.paper.PaperPlattform;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Set;

public final class PlotBauListener implements Listener {
    private static final Set<NamespacedKey> EXPLOSIONEN = Set.of(NamespacedKey.minecraft("explosion"),
            NamespacedKey.minecraft("player_explosion"), NamespacedKey.minecraft("bad_respawn_point"));
    private final PlotSchutz schutz;

    public PlotBauListener(PlotWelt welt, PlotDienst plots, WettbewerbDienst wettbewerbe) {
        this.schutz = new PlotSchutz(welt, plots, wettbewerbe);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void abbauen(BlockBreakEvent event) {
        if (!schutz.darfBlockAendern(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void platzieren(BlockPlaceEvent event) {
        if (!schutz.inPlotwelt(event.getBlock())) {
            return;
        }
        Player spieler = event.getPlayer();
        if (!schutz.darfBlockAendern(spieler, event.getBlock().getLocation())
                || (event instanceof BlockMultiPlaceEvent mehrfach && mehrfach.getReplacedBlockStates().stream()
                .anyMatch(block -> !schutz.darfBlockAendern(spieler, block.getLocation())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interagieren(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (block == null || !schutz.inPlotwelt(block)) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getType() == Material.RESPAWN_ANCHOR
                && block.getBlockData() instanceof RespawnAnchor anker
                && anker.getCharges() > 0 && !(event.getItem() != null && event.getItem().getType() == Material.GLOWSTONE
                && anker.getCharges() < anker.getMaximumCharges())) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
        if (!schutz.darfBenutzen(event.getPlayer(), block.getLocation())) {
            if (!gastInteraktion(event)) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
            event.setUseItemInHand(Event.Result.DENY);
        } else if (!schutz.darfBlockAendern(event.getPlayer(), block.getLocation()) && event.getItem() != null) {
            var material = event.getItem().getType();
            if (Tag.ITEMS_HOES.isTagged(material) || Tag.ITEMS_SHOVELS.isTagged(material)) {
                event.setUseItemInHand(Event.Result.DENY);
            }
        }
    }

    private boolean gastInteraktion(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        var material = block.getType();
        Player spieler = event.getPlayer();
        if (event.getAction() == Action.PHYSICAL && Tag.PRESSURE_PLATES.isTagged(material)) {
            return schutz.hatBesucherRecht(spieler, block, BesucherRecht.SWITCHES);
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        if (Tag.DOORS.isTagged(material) || Tag.TRAPDOORS.isTagged(material) || Tag.FENCE_GATES.isTagged(material)) {
            return schutz.hatBesucherRecht(spieler, block, BesucherRecht.DOORS);
        }
        if (Tag.BUTTONS.isTagged(material) || material == Material.LEVER) {
            return schutz.hatBesucherRecht(spieler, block, BesucherRecht.SWITCHES);
        }
        return (material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL
                || Tag.SHULKER_BOXES.isTagged(material)) && schutz.darfLagerNutzen(spieler, block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void eimerLeeren(PlayerBucketEmptyEvent event) {
        if (!schutz.darfBlockAendern(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void eimerFuellen(PlayerBucketFillEvent event) {
        if (!schutz.darfBlockAendern(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void schildBearbeiten(SignChangeEvent event) {
        if (!schutz.darfBlockAendern(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void inventarOeffnen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player spieler
                && !darfInventarNutzen(spieler, event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void imInventarKlicken(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player spieler && !darfInventarNutzen(spieler, event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void imInventarZiehen(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player spieler && !darfInventarNutzen(spieler, event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    private boolean darfInventarNutzen(Player spieler, Inventory inventar) {
        var holder = inventar.getHolder(false);
        if (holder instanceof DoubleChest kiste) {
            return darfKistenhaelfteNutzen(spieler, kiste.getLeftSide(false))
                    && darfKistenhaelfteNutzen(spieler, kiste.getRightSide(false));
        }
        if (!(holder instanceof BlockInventoryHolder || holder instanceof DoubleChest
                || holder instanceof Entity && !(holder instanceof HumanEntity))) {
            return true;
        }
        Location ort = inventar.getLocation();
        if (ort == null) {
            return false;
        }
        return schutz.darfBenutzen(spieler, ort) || switch (inventar.getType()) {
            case CHEST, BARREL, SHULKER_BOX -> !(holder instanceof Entity) && schutz.darfLagerNutzen(spieler, ort);
            default -> false;
        };
    }

    private boolean darfKistenhaelfteNutzen(Player spieler, InventoryHolder haelfte) {
        if (!(haelfte instanceof BlockInventoryHolder block)) {
            return false;
        }
        Location ort = block.getBlock().getLocation();
        return schutz.darfBenutzen(spieler, ort) || schutz.darfLagerNutzen(spieler, ort);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void itemAufheben(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player spieler)) {
            return;
        }
        var item = event.getItem();
        if (!spieler.getUniqueId().equals(item.getThrower()) && !spieler.getUniqueId().equals(item.getOwner())
                && schutz.beanspruchterPlot(item.getLocation())
                .isPresent() && !schutz.darfBenutzen(spieler, item.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityBenutzen(PlayerInteractEntityEvent event) {
        if (!schutz.darfBenutzen(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aufsitzen(EntityMountEvent event) {
        if (event.getEntity() instanceof Player spieler
                && !schutz.darfBenutzen(spieler, event.getMount().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void anleinen(PlayerLeashEntityEvent event) {
        if (!schutz.darfBenutzen(event.getPlayer(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ableinen(PlayerUnleashEntityEvent event) {
        if (!schutz.darfBenutzen(event.getPlayer(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lesepultLeeren(PlayerTakeLecternBookEvent event) {
        if (!schutz.darfBenutzen(event.getPlayer(), event.getLectern().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ruestungsstaenderBenutzen(PlayerArmorStandManipulateEvent event) {
        entityBenutzen(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPlatzieren(EntityPlaceEvent event) {
        if (!schutz.darfEntityPlatzieren(event.getPlayer(), event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aufhaengen(HangingPlaceEvent event) {
        if (!schutz.darfBenutzen(event.getPlayer(), event.getBlock().getLocation())
                || !schutz.darfEntityPlatzieren(event.getPlayer(), event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void abhaengen(HangingBreakByEntityEvent event) {
        if (angriffGesperrt(event.getRemover(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityAngreifen(EntityDamageByEntityEvent event) {
        Entity quelle = event.getDamageSource().getCausingEntity();
        if (angriffGesperrt(quelle == null ? event.getDamager() : quelle, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityAnzuenden(EntityCombustByEntityEvent event) {
        if (angriffGesperrt(event.getCombuster(), event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void trankWerfen(PotionSplashEvent event) {
        var quelle = event.getPotion().getShooter();
        for (var ziel : event.getAffectedEntities()) {
            if (quelle != ziel && !schutz.darfProjektilNutzen(quelle, ziel.getLocation())) {
                event.setIntensity(ziel, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void trankWolke(AreaEffectCloudApplyEvent event) {
        var quelle = event.getEntity().getSource();
        event.getAffectedEntities().removeIf(ziel -> quelle != ziel
                && !schutz.darfProjektilNutzen(quelle, ziel.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fahrzeugAngreifen(VehicleDamageEvent event) {
        Location ort = event.getVehicle().getLocation();
        if (!schutz.inPlotwelt(ort)) {
            return;
        }
        if (PaperPlattform.istExplosion(event) || angriffGesperrt(event.getAttacker(), ort)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void angeln(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() != null
                && schutz.inPlotwelt(event.getCaught().getLocation())
                && (event.getCaught() instanceof Player
                || !schutz.darfBenutzen(event.getPlayer(), event.getCaught().getLocation()))) {
            event.setCancelled(true);
        }
    }

    private boolean angriffGesperrt(Entity quelle, Location ziel) {
        if (!schutz.inPlotwelt(ziel)) {
            return false;
        }
        if (quelle instanceof Projectile projektil) {
            return !schutz.darfProjektilNutzen(projektil.getShooter(), ziel);
        }
        return quelle instanceof Player spieler && !schutz.darfBenutzen(spieler, ziel);
    }
}
