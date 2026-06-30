package globe.world.util;

import globe.world.entity.ActorLocalTargets;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
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
            return ActorLocalTargets.nearestAliasPosition(victim, directEntity);
        }

        TopologyContext topology = TopologyContexts.forLevel(victim.level());
        return topology.virtualBlockForViewer(topology.canonicalBlock(sourcePosition), victim.position());
    }
}
