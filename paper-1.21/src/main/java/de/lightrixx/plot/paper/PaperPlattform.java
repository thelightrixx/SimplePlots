package de.lightrixx.plot.paper;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Explosive;
import org.bukkit.event.vehicle.VehicleDamageEvent;

/** Paper 1.21.8 hat weder die neue GameRules-Registry noch Fahrzeug-DamageSource. */
public final class PaperPlattform {
    private PaperPlattform() {}

    public static void konfiguriere(World world) {
        world.setPVP(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.TNT_EXPLODES, false);
        world.setGameRule(GameRule.PROJECTILES_CAN_BREAK_BLOCKS, false);
        // Netherportale werden auf dieser API durch die bestehenden Portal-Listener gesperrt.
    }

    public static boolean istExplosion(VehicleDamageEvent event) {
        var attacker = event.getAttacker();
        // Alte APIs nennen bei Bett-/Ankerexplosionen keinen Angreifer.
        return attacker == null || attacker instanceof Explosive || attacker instanceof EnderCrystal;
    }
}
