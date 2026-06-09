package globe.world.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record ActorLocalTargetView(
        Entity actor,
        Entity target,
        Vec3 canonicalPosition,
        Vec3 actorLocalPosition,
        AABB canonicalBox,
        AABB actorLocalBox,
        double wrappedDistanceSqr,
        double wrappedHorizontalDistanceSqr,
        boolean sameLevel,
        boolean aliasingEnabled) {
}
