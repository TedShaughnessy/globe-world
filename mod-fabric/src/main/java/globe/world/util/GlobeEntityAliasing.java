package globe.world.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class GlobeEntityAliasing {
    private static GlobeEntityAliasMode mode = GlobeEntityAliasMode.AUTO;
    private static final int[] RING_LIMIT_CYCLE = {1, 2, 3, 4, 8, Integer.MAX_VALUE};
    private static int maxAliasRings = 1;

    private GlobeEntityAliasing() {
    }

    public static GlobeEntityAliasMode mode() {
        return mode;
    }

    public static GlobeEntityAliasMode cycleMode() {
        mode = mode.next();
        return mode;
    }

    public static int maxAliasRings() {
        return maxAliasRings;
    }

    public static String maxAliasRingsDisplayName() {
        return maxAliasRings == Integer.MAX_VALUE ? "unlimited" : Integer.toString(maxAliasRings);
    }

    public static int cycleMaxAliasRings() {
        for (int i = 0; i < RING_LIMIT_CYCLE.length; i++) {
            if (RING_LIMIT_CYCLE[i] == maxAliasRings) {
                maxAliasRings = RING_LIMIT_CYCLE[(i + 1) % RING_LIMIT_CYCLE.length];
                return maxAliasRings;
            }
        }
        maxAliasRings = 1;
        return maxAliasRings;
    }

    public static boolean aliasesEnabled(Level level, double renderRadius) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        return aliasesEnabled(tiling, renderRadius);
    }

    public static boolean aliasesEnabled(DimensionTiling tiling, double renderRadius) {
        if (mode == GlobeEntityAliasMode.OFF || !tiling.enabled()) {
            return false;
        }
        return mode == GlobeEntityAliasMode.FORCE_DEBUG || renderRadius >= tiling.tileSizeBlocks();
    }

    public static boolean disabledByAutoGate(Level level, double renderRadius) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        return mode == GlobeEntityAliasMode.AUTO && tiling.enabled() && renderRadius < tiling.tileSizeBlocks();
    }

    public static boolean canAlias(Entity entity) {
        if (entity instanceof Player || hasPlayerInStack(entity)) {
            return false;
        }
        return !(entity instanceof Leashable leashable) || leashable.getLeashHolder() == null;
    }

    public static boolean hasPlayerInStack(Entity entity) {
        Entity root = entity.getRootVehicle();
        if (root instanceof Player) {
            return true;
        }
        for (Entity passenger : root.getIndirectPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    public static double vanillaEntityRenderRadius(Entity entity) {
        double size = entity.getBoundingBox().getSize();
        if (Double.isNaN(size)) {
            size = 1.0D;
        }
        return size * 64.0D * Entity.getViewScale();
    }

    public static double visualAliasRenderRadius(Entity entity) {
        Entity root = aliasOffsetSource(entity);
        double renderRadius = vanillaEntityRenderRadius(root);
        if (root != entity) {
            renderRadius = Math.max(renderRadius, vanillaEntityRenderRadius(entity));
        }
        for (Entity passenger : root.getIndirectPassengers()) {
            renderRadius = Math.max(renderRadius, vanillaEntityRenderRadius(passenger));
        }
        return renderRadius;
    }

    public static List<AliasOffset> visualOffsets(Entity entity, Vec3 cameraPos) {
        double renderRadius = visualAliasRenderRadius(entity);
        return visualOffsets(entity, cameraPos, renderRadius, true);
    }

    public static List<AliasOffset> visualOffsets(Entity entity, Vec3 cameraPos, double renderRadius, boolean applyAutoGate) {
        if (!canAlias(entity)) {
            return List.of();
        }

        Level level = entity.level();
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (mode == GlobeEntityAliasMode.OFF || !tiling.enabled()) {
            return List.of();
        }
        if (applyAutoGate && mode == GlobeEntityAliasMode.AUTO && renderRadius < tiling.tileSizeBlocks()) {
            return List.of();
        }

        int tileWidth = tiling.tileSizeBlocks();
        Entity offsetSource = aliasOffsetSource(entity);
        AABB sourceBox = offsetSource.getBoundingBox();
        AABB entityBox = entity.getBoundingBox();
        double padding = Math.max(sourceBox.getSize(), entityBox.getSize()) * 0.5D;
        int maxOffset = (int) Math.ceil((renderRadius + padding) / tileWidth);
        if (maxOffset <= 0) {
            return List.of();
        }

        int minOffsetX = -maxOffset;
        int maxOffsetX = maxOffset;
        int minOffsetZ = -maxOffset;
        int maxOffsetZ = maxOffset;
        if (maxAliasRings != Integer.MAX_VALUE) {
            int cameraTileX = tileAliasBlock(tiling, cameraPos.x);
            int cameraTileZ = tileAliasBlock(tiling, cameraPos.z);
            int entityTileX = tileAliasBlock(tiling, (sourceBox.minX + sourceBox.maxX) * 0.5D);
            int entityTileZ = tileAliasBlock(tiling, (sourceBox.minZ + sourceBox.maxZ) * 0.5D);
            minOffsetX = Math.max(minOffsetX, cameraTileX - maxAliasRings - entityTileX);
            maxOffsetX = Math.min(maxOffsetX, cameraTileX + maxAliasRings - entityTileX);
            minOffsetZ = Math.max(minOffsetZ, cameraTileZ - maxAliasRings - entityTileZ);
            maxOffsetZ = Math.min(maxOffsetZ, cameraTileZ + maxAliasRings - entityTileZ);
        }

        double renderRadiusSqr = renderRadius * renderRadius;
        List<AliasOffset> offsets = new ArrayList<>();
        for (int tileX = minOffsetX; tileX <= maxOffsetX; tileX++) {
            for (int tileZ = minOffsetZ; tileZ <= maxOffsetZ; tileZ++) {
                if (tileX == 0 && tileZ == 0) {
                    continue;
                }

                double dx = tileX * (double) tileWidth;
                double dz = tileZ * (double) tileWidth;
                AABB sourceAliasBox = sourceBox.move(dx, 0.0D, dz);
                if (isWithinCameraTileRings(tiling, cameraPos, sourceAliasBox) && distanceToBoxSqr(cameraPos, sourceAliasBox) <= renderRadiusSqr) {
                    offsets.add(new AliasOffset(tileX, tileZ, dx, dz, entityBox.move(dx, 0.0D, dz)));
                }
            }
        }
        return offsets;
    }

    public static boolean isWholeTileRebase(Level level, Vec3 oldPos, Vec3 newPos) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return false;
        }

        int tileWidth = tiling.tileSizeBlocks();
        if (Math.abs(newPos.y - oldPos.y) > 16.0D) {
            return false;
        }

        double rawDx = newPos.x - oldPos.x;
        double rawDz = newPos.z - oldPos.z;
        int tileDx = (int) Math.rint(rawDx / tileWidth);
        int tileDz = (int) Math.rint(rawDz / tileWidth);
        if (tileDx == 0 && tileDz == 0) {
            return false;
        }

        double residualX = rawDx - tileDx * (double) tileWidth;
        double residualZ = rawDz - tileDz * (double) tileWidth;
        double residualSqr = residualX * residualX + residualZ * residualZ;
        double rawHorizontalSqr = rawDx * rawDx + rawDz * rawDz;
        return residualSqr <= 16.0D && residualSqr * 16.0D < rawHorizontalSqr;
    }

    private static Entity aliasOffsetSource(Entity entity) {
        Entity root = entity.getRootVehicle();
        return root instanceof Player ? entity : root;
    }

    public static double distanceToBoxSqr(Vec3 point, AABB box) {
        double x = distanceOutside(point.x, box.minX, box.maxX);
        double y = distanceOutside(point.y, box.minY, box.maxY);
        double z = distanceOutside(point.z, box.minZ, box.maxZ);
        return x * x + y * y + z * z;
    }

    private static double distanceOutside(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0D;
    }

    private static boolean isWithinCameraTileRings(DimensionTiling tiling, Vec3 cameraPos, AABB aliasBox) {
        if (maxAliasRings == Integer.MAX_VALUE) {
            return true;
        }

        int cameraTileX = tileAliasBlock(tiling, cameraPos.x);
        int cameraTileZ = tileAliasBlock(tiling, cameraPos.z);
        int aliasTileX = tileAliasBlock(tiling, (aliasBox.minX + aliasBox.maxX) * 0.5D);
        int aliasTileZ = tileAliasBlock(tiling, (aliasBox.minZ + aliasBox.maxZ) * 0.5D);
        return Math.abs(aliasTileX - cameraTileX) <= maxAliasRings
                && Math.abs(aliasTileZ - cameraTileZ) <= maxAliasRings;
    }

    private static int tileAliasBlock(DimensionTiling tiling, double coordinate) {
        return CoordUtil.tileAliasBlock(tiling, (int) Math.floor(coordinate));
    }

    public record AliasOffset(int tileX, int tileZ, double dx, double dz, AABB box) {
    }
}
