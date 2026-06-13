package globe.world.topology;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TopologicalExplosions {
    private static final float MIN_EXPLOSION_RADIUS = 1.0E-5F;

    private TopologicalExplosions() {
    }

    public static List<BlockPos> canonicalAffectedBlocks(ServerLevel level, Vec3 center, List<BlockPos> rawTargets) {
        TopologyContext context = TopologyContexts.forLevel(level);
        if (!context.enabled()) {
            return rawTargets;
        }

        Set<BlockPos> canonicalTargets = new LinkedHashSet<>(rawTargets.size());
        for (BlockPos rawTarget : rawTargets) {
            canonicalTargets.add(context.canonicalBlock(rawTarget));
        }
        return new ArrayList<>(canonicalTargets);
    }

    public static List<Entity> affectedEntities(ServerLevel level, @Nullable Entity source, Vec3 center, float radius) {
        float doubleRadius = radius * 2.0F;
        int x0 = Mth.floor(center.x - doubleRadius - 1.0D);
        int x1 = Mth.floor(center.x + doubleRadius + 1.0D);
        int y0 = Mth.floor(center.y - doubleRadius - 1.0D);
        int y1 = Mth.floor(center.y + doubleRadius + 1.0D);
        int z0 = Mth.floor(center.z - doubleRadius - 1.0D);
        int z1 = Mth.floor(center.z + doubleRadius + 1.0D);
        return TopologicalEntityQueries.entities(level, source, new AABB(x0, y0, z0, x1, y1, z1));
    }

    public static boolean hurtEntities(
            ServerExplosion explosion,
            ServerLevel level,
            @Nullable Entity source,
            DamageSource damageSource,
            ExplosionDamageCalculator damageCalculator,
            Vec3 center,
            float radius,
            Map<Player, Vec3> hitPlayers) {
        TopologyContext context = TopologyContexts.forLevel(level);
        if (!context.enabled()) {
            return false;
        }

        if (radius < MIN_EXPLOSION_RADIUS) {
            return true;
        }

        for (Entity entity : affectedEntities(level, source, center, radius)) {
            if (entity.ignoreExplosion(explosion)) {
                continue;
            }

            EntityView view = entityView(level, center, entity, radius);
            if (view.normalizedDistance() > 1.0D) {
                continue;
            }

            boolean shouldDamageEntity = damageCalculator.shouldDamageEntity(explosion, entity);
            float knockbackMultiplier = damageCalculator.getKnockbackMultiplier(entity);
            float exposure = !shouldDamageEntity && knockbackMultiplier == 0.0F
                    ? 0.0F
                    : seenPercent(level, center, entity, view.visibleBox());
            if (shouldDamageEntity) {
                // Vanilla's calculator derives entity damage from raw entity-center distance.
                // Use the same formula with the visible-frame distance so seam hits are balanced.
                entity.hurtServer(level, damageSource, damageAmount(radius, view.normalizedDistance(), exposure));
            }

            double knockbackResistance = entity instanceof LivingEntity livingEntity
                    ? livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
                    : 0.0D;
            double knockbackPower = (1.0D - view.normalizedDistance()) * exposure * knockbackMultiplier * (1.0D - knockbackResistance);
            Vec3 knockback = view.direction().scale(knockbackPower);
            entity.push(knockback);
            if (entity.is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile) {
                projectile.setOwner(damageSource.getEntity());
            } else if (entity instanceof Player player && !player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                hitPlayers.put(player, knockback);
            }

            entity.onExplosionHit(source);
        }

        return true;
    }

    public static float seenPercent(ServerLevel level, Vec3 visibleCenter, Entity entity, AABB visibleEntityBox) {
        double xs = 1.0D / ((visibleEntityBox.maxX - visibleEntityBox.minX) * 2.0D + 1.0D);
        double ys = 1.0D / ((visibleEntityBox.maxY - visibleEntityBox.minY) * 2.0D + 1.0D);
        double zs = 1.0D / ((visibleEntityBox.maxZ - visibleEntityBox.minZ) * 2.0D + 1.0D);
        double xOffset = (1.0D - Math.floor(1.0D / xs) * xs) / 2.0D;
        double zOffset = (1.0D - Math.floor(1.0D / zs) * zs) / 2.0D;
        if (xs < 0.0D || ys < 0.0D || zs < 0.0D) {
            return 0.0F;
        }

        int hits = 0;
        int count = 0;
        for (double xx = 0.0D; xx <= 1.0D; xx += xs) {
            for (double yy = 0.0D; yy <= 1.0D; yy += ys) {
                for (double zz = 0.0D; zz <= 1.0D; zz += zs) {
                    double x = Mth.lerp(xx, visibleEntityBox.minX, visibleEntityBox.maxX);
                    double y = Mth.lerp(yy, visibleEntityBox.minY, visibleEntityBox.maxY);
                    double z = Mth.lerp(zz, visibleEntityBox.minZ, visibleEntityBox.maxZ);
                    Vec3 from = new Vec3(x + xOffset, y, z + zOffset);
                    if (level.clip(new ClipContext(from, visibleCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS) {
                        hits++;
                    }
                    count++;
                }
            }
        }
        return (float) hits / count;
    }

    public static EntityView entityView(ServerLevel level, Vec3 center, Entity entity, float radius) {
        TopologyContext context = TopologyContexts.forLevel(level);
        AABB canonicalBox = context.canonicalBox(entity.getBoundingBox());
        AABB visibleBox = context.virtualBoxForViewer(canonicalBox, center);
        Vec3 canonicalOrigin = context.canonicalBlock(entity instanceof PrimedTnt ? entity.position() : entity.getEyePosition());
        Vec3 visibleOrigin = context.virtualBlockForViewer(canonicalOrigin, center);
        Vec3 offset = visibleOrigin.subtract(center);
        float doubleRadius = radius * 2.0F;
        double normalizedDistance = doubleRadius == 0.0F ? Double.POSITIVE_INFINITY : offset.length() / doubleRadius;
        return new EntityView(
                entity,
                canonicalBox,
                visibleBox,
                visibleOrigin,
                offset.normalize(),
                normalizedDistance
        );
    }

    private static float damageAmount(float radius, double normalizedDistance, float exposure) {
        float doubleRadius = radius * 2.0F;
        double power = (1.0D - normalizedDistance) * exposure;
        return (float) ((power * power + power) / 2.0D * 7.0D * doubleRadius + 1.0D);
    }

    public record EntityView(
            Entity entity,
            AABB canonicalBox,
            AABB visibleBox,
            Vec3 visibleOrigin,
            Vec3 direction,
            double normalizedDistance) {
    }
}
