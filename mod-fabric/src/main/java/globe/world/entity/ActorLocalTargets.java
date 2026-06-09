package globe.world.entity;

import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import globe.world.util.AiAliasUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public final class ActorLocalTargets {
    private ActorLocalTargets() {
    }

    public static ActorLocalTargetView view(Entity actor, Entity target) {
        boolean sameLevel = actor.level() == target.level();
        boolean aliasingEnabled = AiAliasUtil.canAlias(actor, target);
        TopologyContext context = TopologyContexts.forLevel(target.level());
        Vec3 canonicalPosition = new Vec3(
                context.canonicalBlockX(target.getX()),
                target.getY(),
                context.canonicalBlockX(target.getZ())
        );
        AABB canonicalBox = context.canonicalBox(target.getBoundingBox());
        return new ActorLocalTargetView(
                actor,
                target,
                canonicalPosition,
                position(actor, target),
                canonicalBox,
                box(actor, target),
                distanceToSqr(actor, target),
                AiAliasUtil.horizontalDistanceToSqr(actor, target),
                sameLevel,
                aliasingEnabled
        );
    }

    public static Vec3 position(Entity actor, Entity target) {
        return AiAliasUtil.nearestAliasPosition(actor, target);
    }

    public static Vec3 eyePosition(LivingEntity actor, Entity target) {
        return AiAliasUtil.nearestAliasEyePosition(actor, target);
    }

    public static AABB box(Entity actor, Entity target) {
        return AiAliasUtil.nearestAliasBoundingBox(actor, target);
    }

    public static double distanceToSqr(Entity actor, Entity target) {
        return AiAliasUtil.distanceToSqr(actor, target);
    }

    public static boolean hasLineOfSight(LivingEntity actor, Entity target) {
        return !AiAliasUtil.canAlias(actor, target)
                ? actor.hasLineOfSight(target)
                : AiAliasUtil.aliasLineOfSight(actor, target);
    }

    public static Set<BlockPos> pathTargets(Mob actor, Entity target) {
        return AiAliasUtil.pathTargetBlockPositions(actor, target);
    }
}
