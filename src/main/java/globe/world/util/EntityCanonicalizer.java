package globe.world.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class EntityCanonicalizer {
    private EntityCanonicalizer() {
    }

    public static boolean shouldCanonicalizeContinuously(Entity entity) {
        return entity.level() instanceof ServerLevel
                && !entity.level().isClientSide()
                && DimensionTiling.forLevel(entity.level()).enabled()
                && !entity.isRemoved()
                && !(entity instanceof ServerPlayer);
    }

    public static boolean canonicalize(Entity entity) {
        return canonicalizeSingle(entity);
    }

    public static boolean canonicalizeForStorage(Entity entity) {
        if (!shouldCanonicalizeContinuously(entity)) {
            return false;
        }
        return canonicalizeRootStack(entity.getRootVehicle());
    }

    public static boolean canonicalizeAfterTick(Entity entity) {
        if (entity.isPassenger()) {
            return false;
        }
        if (!shouldCanonicalizeContinuously(entity)) {
            return false;
        }
        return canonicalizeRootStack(entity);
    }

    public static boolean canonicalizeAfterTeleport(Entity entity) {
        if (entity.isPassenger()) {
            return false;
        }
        if (!shouldCanonicalizeContinuously(entity)) {
            return false;
        }
        return canonicalizeRootStack(entity);
    }

    public static boolean canonicalizeRootStack(Entity root) {
        if (!shouldCanonicalizeContinuously(root)) {
            return false;
        }

        double x = CoordUtil.wrapBlock(root.level(), root.getX());
        double z = CoordUtil.wrapBlock(root.level(), root.getZ());
        double dx = x - root.getX();
        double dz = z - root.getZ();
        if (dx == 0.0 && dz == 0.0) {
            return false;
        }

        snapAndSync(root, x, root.getY(), z);
        MobNavigationAliasUtil.resetAfterAliasCanonicalization(root);
        for (Entity passenger : root.getIndirectPassengers()) {
            if (!passenger.isRemoved() && !(passenger instanceof ServerPlayer)) {
                snapAndSync(passenger, passenger.getX() + dx, passenger.getY(), passenger.getZ() + dz);
                MobNavigationAliasUtil.resetAfterAliasCanonicalization(passenger);
            }
        }
        return true;
    }

    private static boolean canonicalizeSingle(Entity entity) {
        double x = CoordUtil.wrapBlock(entity.level(), entity.getX());
        double z = CoordUtil.wrapBlock(entity.level(), entity.getZ());
        if (x == entity.getX() && z == entity.getZ()) {
            return false;
        }

        snapAndSync(entity, x, entity.getY(), z);
        MobNavigationAliasUtil.resetAfterAliasCanonicalization(entity);
        return true;
    }

    private static void snapAndSync(Entity entity, double x, double y, double z) {
        entity.snapTo(x, y, z, entity.getYRot(), entity.getXRot());
        entity.syncPacketPositionCodec(x, y, z);
    }
}
