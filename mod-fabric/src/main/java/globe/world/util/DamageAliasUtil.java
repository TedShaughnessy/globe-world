package globe.world.util;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class DamageAliasUtil {
    private DamageAliasUtil() {
    }

    public static Vec3 sourcePositionForVictim(LivingEntity victim, DamageSource source, Vec3 sourcePosition) {
        if (sourcePosition == null || !DimensionTiling.forLevel(victim.level()).enabled()) {
            return sourcePosition;
        }

        Entity directEntity = source.getDirectEntity();
        if (directEntity != null && directEntity.level() == victim.level()) {
            return AiAliasUtil.nearestAliasPosition(victim, directEntity);
        }

        double x = CoordUtil.virtualBlock(
                victim.level(),
                CoordUtil.wrapBlock(victim.level(), sourcePosition.x),
                victim.getX());
        double z = CoordUtil.virtualBlock(
                victim.level(),
                CoordUtil.wrapBlock(victim.level(), sourcePosition.z),
                victim.getZ());
        return new Vec3(x, sourcePosition.y, z);
    }
}
