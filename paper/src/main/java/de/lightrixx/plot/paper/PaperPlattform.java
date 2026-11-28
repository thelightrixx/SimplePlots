package de.lightrixx.plot.paper;

import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import java.util.Set;

/** Die wenigen API-Unterschiede der Paper-Varianten bleiben hier begrenzt. */
public final class PaperPlattform {
    private static final Set<NamespacedKey> EXPLOSIONS = Set.of(NamespacedKey.minecraft("explosion"),
            NamespacedKey.minecraft("player_explosion"), NamespacedKey.minecraft("bad_respawn_point"));
    private PaperPlattform() {}

    public static void konfiguriere(World world) {
        world.setGameRule(GameRules.PVP, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.TNT_EXPLODES, false);
        world.setGameRule(GameRules.PROJECTILES_CAN_BREAK_BLOCKS, false);
        world.setGameRule(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
    }

    public static boolean istExplosion(VehicleDamageEvent event) {
        var source = event.getDamageSource();
        return source != null && EXPLOSIONS.contains(source.getDamageType().getKey());
    }
}
