package globe.world.topology;

import com.mojang.datafixers.util.Either;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class TopologicalRaycasts {
    private static final int DEFAULT_ALIAS_TILE_RADIUS = 0;
    private static final double LINE_OF_SIGHT_LIMIT = 128.0D;

    private TopologicalRaycasts() {
    }

    public static BlockTraceResult topologicalClip(Level level, Vec3 from, Vec3 to, BlockTraceOptions options) {
        TopologyContext context = TopologyContexts.forLevel(level);
        ClipContext clipContext = options.toClipContext(from, to);
        BlockHitResult visibleHit = options.includeWorldBorder()
                ? level.clipIncludingBorder(clipContext)
                : level.clip(clipContext);
        return new BlockTraceResult(
                visibleHit,
                canonicalBlockHit(context, visibleHit),
                from.distanceToSqr(visibleHit.getLocation())
        );
    }

    public static List<EntitySweepHit> topologicalEntitySweep(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB searchBox,
            Predicate<Entity> matching,
            EntitySweepOptions options) {
        TopologyContext context = TopologyContexts.forLevel(level);
        List<EntitySweepHit> hits = new ArrayList<>();
        Set<Entity> candidates = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        for (Entity entity : TopologicalEntityQueries.entities(level, source, searchBox, matching)) {
            if (candidates.add(entity)) {
                addEntityHits(level, context, source, from, to, searchBox, matching, options, hits, entity);
            }
        }

        hits.sort(Comparator.comparingDouble(EntitySweepHit::distanceSqr));
        return hits;
    }

    public static boolean topologicalLineOfSight(LivingEntity actor, Entity target) {
        if (actor.level() != target.level()) {
            return false;
        }

        Level level = actor.level();
        TopologyContext context = TopologyContexts.forLevel(level);
        Vec3 from = new Vec3(actor.getX(), actor.getEyeY(), actor.getZ());
        Vec3 canonicalTarget = new Vec3(
                context.canonicalBlockX(target.getX()),
                target.getEyeY(),
                context.canonicalBlockX(target.getZ())
        );
        Vec3 to = context.virtualBlockForViewer(canonicalTarget, from);
        if (to.distanceTo(from) > LINE_OF_SIGHT_LIMIT) {
            return false;
        }

        return topologicalClip(
                level,
                from,
                to,
                new BlockTraceOptions(ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor, false)
        ).visibleHit().getType() == HitResult.Type.MISS;
    }

    public static HitResult topologicalViewVector(
            Entity source,
            Predicate<Entity> matching,
            double distance) {
        Vec3 delta = source.getViewVector(0.0F).scale(distance);
        Vec3 from = source.getEyePosition();
        Vec3 to = from.add(delta);
        Level level = source.level();
        BlockTraceResult blockHit = topologicalClip(
                level,
                from,
                to,
                new BlockTraceOptions(ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source, true)
        );
        Vec3 entityTo = blockHit.visibleHit().getType() == HitResult.Type.MISS
                ? to
                : blockHit.visibleHit().getLocation();
        List<EntitySweepHit> entityHits = topologicalEntitySweep(
                level,
                source,
                from,
                entityTo,
                source.getBoundingBox().expandTowards(delta).inflate(1.0D),
                matching,
                new EntitySweepOptions(ClipContext.Block.COLLIDER, 0.0F, false, DEFAULT_ALIAS_TILE_RADIUS)
        );
        return entityHits.isEmpty() ? blockHit.visibleHitWithCanonicalBlock() : entityHits.getFirst().visibleHit();
    }

    public static Either<BlockHitResult, Collection<EntityHitResult>> topologicalHitEntitiesAlong(
            Entity attacker,
            AttackRange attackRange,
            Predicate<Entity> matching,
            ClipContext.Block blockClipType) {
        Vec3 look = attacker.getHeadLookAngle();
        Vec3 eyePosition = attacker.getEyePosition();
        Vec3 from = eyePosition.add(look.scale(attackRange.effectiveMinRange(attacker)));
        double movementComponent = attacker.getKnownMovement().dot(look);
        Vec3 to = eyePosition.add(look.scale(attackRange.effectiveMaxRange(attacker) + Math.max(0.0D, movementComponent)));
        return topologicalHitEntitiesAlong(
                attacker,
                eyePosition,
                from,
                matching,
                to,
                attackRange.hitboxMargin(),
                blockClipType
        );
    }

    public static ProjectileMoveResult topologicalProjectileMove(
            Entity projectile,
            Vec3 nextPosition,
            Predicate<Entity> matching,
            ClipContext.Block blockClipType) {
        Vec3 from = projectile.position();
        Level level = projectile.level();
        BlockTraceResult blockHit = topologicalClip(
                level,
                from,
                nextPosition,
                new BlockTraceOptions(blockClipType, ClipContext.Fluid.NONE, projectile, true)
        );
        Vec3 entityTo = blockHit.visibleHit().getType() == HitResult.Type.MISS
                ? nextPosition
                : blockHit.visibleHit().getLocation();
        AABB searchBox = projectile.getBoundingBox().expandTowards(nextPosition.subtract(from)).inflate(1.0D);
        List<EntitySweepHit> entityHits = topologicalEntitySweep(
                level,
                projectile,
                from,
                entityTo,
                searchBox,
                matching,
                EntitySweepOptions.projectile(projectile, blockClipType, false)
        );
        HitResult firstHit = entityHits.isEmpty() ? blockHit.visibleHitWithCanonicalBlock() : entityHits.getFirst().visibleHit();
        return new ProjectileMoveResult(from, nextPosition, blockHit, entityHits, firstHit);
    }

    private static Either<BlockHitResult, Collection<EntityHitResult>> topologicalHitEntitiesAlong(
            Entity source,
            Vec3 origin,
            Vec3 from,
            Predicate<Entity> matching,
            Vec3 to,
            float entityMargin,
            ClipContext.Block blockClipType) {
        Level level = source.level();
        BlockTraceResult blockHit = topologicalClip(
                level,
                origin,
                to,
                new BlockTraceOptions(blockClipType, ClipContext.Fluid.NONE, source, true)
        );
        BlockHitResult visibleBlockHit = blockHit.visibleHitWithCanonicalBlock();
        if (visibleBlockHit.getType() != HitResult.Type.MISS) {
            to = visibleBlockHit.getLocation();
            if (origin.distanceToSqr(to) < origin.distanceToSqr(from)) {
                return Either.left(visibleBlockHit);
            }
        }

        AABB searchArea = AABB.ofSize(from, entityMargin, entityMargin, entityMargin)
                .expandTowards(to.subtract(from))
                .inflate(1.0D);
        List<EntityHitResult> entityHits = topologicalEntitySweep(
                level,
                source,
                from,
                to,
                searchArea,
                matching,
                new EntitySweepOptions(blockClipType, entityMargin, true, DEFAULT_ALIAS_TILE_RADIUS)
        ).stream().map(EntitySweepHit::visibleHit).toList();
        return entityHits.isEmpty() ? Either.left(visibleBlockHit) : Either.right(entityHits);
    }

    private static void addEntityHits(
            Level level,
            TopologyContext context,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB searchBox,
            Predicate<Entity> matching,
            EntitySweepOptions options,
            List<EntitySweepHit> hits,
            Entity entity) {
        if (entity == source || !matching.test(entity)) {
            return;
        }

        AABB canonicalBox = context.canonicalBox(entity.getBoundingBox());
        EntitySweepHit nearest = null;
        for (AABB visibleBox : visibleBoxes(context, canonicalBox, from, searchBox, options.entityMargin(), options.aliasTileRadius())) {
            EntityHitResult hit = findEntityHit(level, source, from, to, visibleBox, entity, options);
            if (hit != null) {
                EntitySweepHit candidate = new EntitySweepHit(
                        entity,
                        hit,
                        canonicalLocation(context, hit.getLocation()),
                        canonicalBox,
                        visibleBox,
                        from.distanceToSqr(hit.getLocation())
                );
                if (nearest == null || candidate.distanceSqr() < nearest.distanceSqr()) {
                    nearest = candidate;
                }
            }
        }
        if (nearest != null) {
            hits.add(nearest);
        }
    }

    private static List<AABB> visibleBoxes(
            TopologyContext context,
            AABB canonicalBox,
            Vec3 from,
            AABB searchBox,
            float entityMargin,
            int aliasTileRadius) {
        if (!context.enabled()) {
            return List.of(canonicalBox);
        }

        int radius = Math.max(0, aliasTileRadius);
        DimensionTiling tiling = context.tiling();
        int tileSize = tiling.tileSizeBlocks();
        double centerX = (canonicalBox.minX + canonicalBox.maxX) * 0.5D;
        double centerZ = (canonicalBox.minZ + canonicalBox.maxZ) * 0.5D;
        int baseTileX = CoordUtil.virtualBlockTileOffset(tiling, centerX, from.x());
        int baseTileZ = CoordUtil.virtualBlockTileOffset(tiling, centerZ, from.z());
        double padding = Math.max(ProjectileUtil.DEFAULT_ENTITY_HIT_RESULT_MARGIN, entityMargin) + 1.0E-7D;
        AABB paddedSearchBox = searchBox.inflate(padding);
        List<AABB> boxes = new ArrayList<>();

        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                AABB box = canonicalBox.move((baseTileX + offsetX) * (double) tileSize, 0.0D, (baseTileZ + offsetZ) * (double) tileSize);
                if (box.contains(from) || box.intersects(paddedSearchBox)) {
                    boxes.add(box);
                }
            }
        }

        return boxes;
    }

    private static EntityHitResult findEntityHit(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB visibleBox,
            Entity entity,
            EntitySweepOptions options) {
        if (options.includeFromEntity() && visibleBox.contains(from)) {
            return new EntityHitResult(entity, from);
        }

        return visibleBox.clip(from, to)
                .map(location -> new EntityHitResult(entity, location))
                .orElseGet(() -> findMarginEntityHit(level, source, from, to, visibleBox, entity, options));
    }

    private static EntityHitResult findMarginEntityHit(
            Level level,
            Entity source,
            Vec3 from,
            Vec3 to,
            AABB visibleBox,
            Entity entity,
            EntitySweepOptions options) {
        if (options.entityMargin() <= 0.0F) {
            return null;
        }

        return visibleBox.inflate(options.entityMargin()).clip(from, to)
                .flatMap(outsideHitPosition -> {
                    Vec3 towardsTarget = visibleBox.getCenter();
                    BlockTraceResult blockHit = topologicalClip(
                            level,
                            outsideHitPosition,
                            towardsTarget,
                            new BlockTraceOptions(options.blockClipType(), ClipContext.Fluid.NONE, source, true)
                    );
                    if (blockHit.visibleHit().getType() != HitResult.Type.MISS) {
                        towardsTarget = blockHit.visibleHit().getLocation();
                    }
                    return visibleBox.clip(outsideHitPosition, towardsTarget);
                })
                .map(location -> new EntityHitResult(entity, location))
                .orElse(null);
    }

    private static BlockHitResult canonicalBlockHit(TopologyContext context, BlockHitResult visibleHit) {
        Vec3 canonicalLocation = canonicalLocation(context, visibleHit.getLocation());
        BlockPos canonicalPos = context.canonicalBlock(visibleHit.getBlockPos());
        if (visibleHit.getType() == HitResult.Type.MISS) {
            return BlockHitResult.miss(canonicalLocation, visibleHit.getDirection(), canonicalPos);
        }
        return new BlockHitResult(
                canonicalLocation,
                visibleHit.getDirection(),
                canonicalPos,
                visibleHit.isInside(),
                visibleHit.isWorldBorderHit()
        );
    }

    private static Vec3 canonicalLocation(TopologyContext context, Vec3 visibleLocation) {
        if (!context.enabled()) {
            return visibleLocation;
        }
        return new Vec3(
                context.canonicalBlockX(visibleLocation.x()),
                visibleLocation.y(),
                context.canonicalBlockX(visibleLocation.z())
        );
    }

    public record BlockTraceOptions(
            ClipContext.Block blockClipType,
            ClipContext.Fluid fluidClipType,
            Entity source,
            boolean includeWorldBorder) {
        private ClipContext toClipContext(Vec3 from, Vec3 to) {
            CollisionContext collision = source == null ? CollisionContext.empty() : CollisionContext.of(source);
            return new ClipContext(from, to, blockClipType, fluidClipType, collision);
        }
    }

    public record EntitySweepOptions(
            ClipContext.Block blockClipType,
            float entityMargin,
            boolean includeFromEntity,
            int aliasTileRadius) {
        public static EntitySweepOptions projectile(Entity source, ClipContext.Block blockClipType, boolean includeFromEntity) {
            return new EntitySweepOptions(blockClipType, ProjectileUtil.computeMargin(source), includeFromEntity, DEFAULT_ALIAS_TILE_RADIUS);
        }

        public EntitySweepOptions withAliasTileRadius(int aliasTileRadius) {
            return new EntitySweepOptions(blockClipType, entityMargin, includeFromEntity, aliasTileRadius);
        }
    }

    public record BlockTraceResult(BlockHitResult visibleHit, BlockHitResult canonicalHit, double distanceSqr) {
        public BlockHitResult visibleHitWithCanonicalBlock() {
            if (visibleHit.getType() != HitResult.Type.BLOCK || visibleHit.isWorldBorderHit()) {
                return visibleHit;
            }
            return new BlockHitResult(
                    visibleHit.getLocation(),
                    visibleHit.getDirection(),
                    canonicalHit.getBlockPos(),
                    visibleHit.isInside()
            );
        }
    }

    public record EntitySweepHit(
            Entity entity,
            EntityHitResult visibleHit,
            Vec3 canonicalHitLocation,
            AABB canonicalBox,
            AABB visibleBox,
            double distanceSqr) {
    }

    public record ProjectileMoveResult(
            Vec3 from,
            Vec3 requestedTo,
            BlockTraceResult blockHit,
            List<EntitySweepHit> entityHits,
            HitResult firstVisibleHit) {
    }
}
