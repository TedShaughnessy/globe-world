package globe.world.util;

import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.function.BooleanSupplier;

public final class GlobeInteractionPermissions {
    private GlobeInteractionPermissions() {
    }

    public static boolean mayInteract(
            ServerLevel level,
            Entity entity,
            BlockPos rawPos,
            BooleanSupplier vanillaMayInteract) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled() || !(entity instanceof Player)) {
            return vanillaMayInteract.getAsBoolean();
        }

        return inspect(level, entity, rawPos).allowed();
    }

    public static PermissionView inspect(ServerLevel level, Entity entity, BlockPos rawPos) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        BlockPos canonicalPos = topology.canonicalBlock(rawPos);
        boolean rawInsideWorldBorder = level.getWorldBorder().isWithinBounds(rawPos);
        boolean spawnProtectedAtRaw = false;
        boolean spawnProtectedAtCanonical = false;
        boolean allowed = true;

        if (entity instanceof Player player) {
            spawnProtectedAtRaw = level.getServer().isUnderSpawnProtection(level, rawPos, player);
            spawnProtectedAtCanonical = level.getServer().isUnderSpawnProtection(level, canonicalPos, player);
            allowed = rawInsideWorldBorder
                    && !(topology.enabled() ? spawnProtectedAtCanonical : spawnProtectedAtRaw);
        }

        return new PermissionView(
                rawPos,
                canonicalPos,
                topology.enabled(),
                spawnProtectedAtRaw,
                spawnProtectedAtCanonical,
                rawInsideWorldBorder,
                allowed);
    }

    public record PermissionView(
            BlockPos rawPos,
            BlockPos canonicalPos,
            boolean topologyEnabled,
            boolean spawnProtectedAtRaw,
            boolean spawnProtectedAtCanonical,
            boolean rawInsideWorldBorder,
            boolean allowed) {
    }
}
