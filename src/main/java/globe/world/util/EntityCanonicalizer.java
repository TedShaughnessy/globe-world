package globe.world.util;

import net.minecraft.world.entity.Entity;

public class EntityCanonicalizer {
    public static boolean canonicalize(Entity entity) {
        double x = CoordUtil.wrapBlock(entity.level(), entity.getX());
        double z = CoordUtil.wrapBlock(entity.level(), entity.getZ());
        if (x == entity.getX() && z == entity.getZ()) {
            return false;
        }

        entity.snapTo(x, entity.getY(), z, entity.getYRot(), entity.getXRot());
        entity.syncPacketPositionCodec(x, entity.getY(), z);
        return true;
    }
}
