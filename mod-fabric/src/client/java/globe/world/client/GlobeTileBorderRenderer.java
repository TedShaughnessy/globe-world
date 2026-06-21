package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public class GlobeTileBorderRenderer implements net.minecraft.client.renderer.debug.DebugRenderer.SimpleDebugRenderer {
    private static final int CANONICAL_TILE_COLOR = ARGB.color(255, 40, 230, 80);
    private static final int ALIAS_TILE_COLOR = ARGB.color(210, 80, 170, 255);
    private static final int WORLD_SPAWN_COLOR = ARGB.color(255, 255, 70, 70);
    private static final int WORLD_SPAWN_ALLOWED_COLOR = ARGB.color(170, 255, 150, 80);
    private static final int WORLD_SPAWN_RADIUS_FILL = ARGB.color(35, 255, 70, 70);
    private static final float CORNER_POST_WIDTH = 5.0F;
    private static final float PLAYER_HEIGHT_BORDER_WIDTH = 4.0F;
    private static final float WORLD_SPAWN_MARKER_WIDTH = 6.0F;
    private static final int WORLD_SPAWN_EXCLUSION_BLOCKS = 24;

    private final Minecraft minecraft;

    public GlobeTileBorderRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues, Frustum frustum, float partialTicks) {
        if (!GlobeDebugState.tileBordersEnabled() || this.minecraft.level == null) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forLevel(this.minecraft.level);
        if (!tiling.enabled()) {
            return;
        }

        Entity cameraEntity = this.minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return;
        }

        int tileBlocks = tiling.tileSizeBlocks();
        int canonicalMin = -(tiling.tileSizeChunks() / 2) * 16;
        int currentTileX = Math.floorDiv(cameraEntity.blockPosition().getX() - canonicalMin, tileBlocks);
        int currentTileZ = Math.floorDiv(cameraEntity.blockPosition().getZ() - canonicalMin, tileBlocks);
        int minY = this.minecraft.level.getMinY();
        int maxY = this.minecraft.level.getMaxY() + 1;
        double borderY = Math.clamp(Math.round(cameraEntity.getY()), minY + 0.05D, maxY - 0.05D);

        for (int tileX = currentTileX - 1; tileX <= currentTileX + 1; tileX++) {
            for (int tileZ = currentTileZ - 1; tileZ <= currentTileZ + 1; tileZ++) {
                if (tileX != 0 || tileZ != 0) {
                    drawTile(canonicalMin, tileBlocks, tileX, tileZ, minY, maxY, borderY, ALIAS_TILE_COLOR, true);
                }
            }
        }
        drawTile(canonicalMin, tileBlocks, 0, 0, minY, maxY, borderY, CANONICAL_TILE_COLOR, false);

        drawWorldSpawnMarker(cameraEntity, tiling, tileBlocks, minY, maxY);
    }

    private static void drawTile(
            int canonicalMin,
            int tileBlocks,
            int tileX,
            int tileZ,
            int minY,
            int maxY,
            double borderY,
            int color,
            boolean skipCanonicalOverlap) {
        int x0 = canonicalMin + tileX * tileBlocks;
        int z0 = canonicalMin + tileZ * tileBlocks;
        int x1 = x0 + tileBlocks;
        int z1 = z0 + tileBlocks;
        int canonicalMax = canonicalMin + tileBlocks;

        cornerPost(x0, z0, minY, maxY, color, skipCanonicalOverlap, canonicalMin, canonicalMax);
        cornerPost(x1, z0, minY, maxY, color, skipCanonicalOverlap, canonicalMin, canonicalMax);
        cornerPost(x1, z1, minY, maxY, color, skipCanonicalOverlap, canonicalMin, canonicalMax);
        cornerPost(x0, z1, minY, maxY, color, skipCanonicalOverlap, canonicalMin, canonicalMax);

        borderLine(x0, z0, x1, z0, borderY, color, skipCanonicalOverlap, canonicalMin, canonicalMax);
        borderLine(x1, z0, x1, z1, borderY, color, skipCanonicalOverlap, canonicalMin, canonicalMax);
        borderLine(x1, z1, x0, z1, borderY, color, skipCanonicalOverlap, canonicalMin, canonicalMax);
        borderLine(x0, z1, x0, z0, borderY, color, skipCanonicalOverlap, canonicalMin, canonicalMax);
    }

    private static void cornerPost(
            int x,
            int z,
            int minY,
            int maxY,
            int color,
            boolean skipCanonicalOverlap,
            int canonicalMin,
            int canonicalMax) {
        if (skipCanonicalOverlap && isCanonicalCorner(x, z, canonicalMin, canonicalMax)) {
            return;
        }
        line(x, minY, z, x, maxY, z, color, CORNER_POST_WIDTH);
    }

    private static void borderLine(
            int x0,
            int z0,
            int x1,
            int z1,
            double y,
            int color,
            boolean skipCanonicalOverlap,
            int canonicalMin,
            int canonicalMax) {
        if (skipCanonicalOverlap && isCanonicalBorder(x0, z0, x1, z1, canonicalMin, canonicalMax)) {
            return;
        }
        line(x0, y, z0, x1, y, z1, color, PLAYER_HEIGHT_BORDER_WIDTH);
    }

    private static boolean isCanonicalCorner(int x, int z, int canonicalMin, int canonicalMax) {
        return (x == canonicalMin || x == canonicalMax) && (z == canonicalMin || z == canonicalMax);
    }

    private static boolean isCanonicalBorder(int x0, int z0, int x1, int z1, int canonicalMin, int canonicalMax) {
        if (z0 == z1 && (z0 == canonicalMin || z0 == canonicalMax)) {
            return spansCanonicalRange(x0, x1, canonicalMin, canonicalMax);
        }
        if (x0 == x1 && (x0 == canonicalMin || x0 == canonicalMax)) {
            return spansCanonicalRange(z0, z1, canonicalMin, canonicalMax);
        }
        return false;
    }

    private static boolean spansCanonicalRange(int a, int b, int canonicalMin, int canonicalMax) {
        return Math.min(a, b) == canonicalMin && Math.max(a, b) == canonicalMax;
    }

    private void drawWorldSpawnMarker(Entity cameraEntity, DimensionTiling tiling, int tileBlocks, int minY, int maxY) {
        LevelData.RespawnData respawnData = this.minecraft.level.getRespawnData();
        if (!respawnData.dimension().equals(this.minecraft.level.dimension())) {
            return;
        }

        BlockPos spawn = respawnData.pos();
        double canonicalX = CoordUtil.wrapBlock(tiling, spawn.getX()) + 0.5D;
        double canonicalZ = CoordUtil.wrapBlock(tiling, spawn.getZ()) + 0.5D;
        double aliasX = nearestAlias(canonicalX, cameraEntity.getX(), tileBlocks);
        double aliasZ = nearestAlias(canonicalZ, cameraEntity.getZ(), tileBlocks);
        int color = GlobeConfig.allowMobsAtWorldSpawn() ? WORLD_SPAWN_ALLOWED_COLOR : WORLD_SPAWN_COLOR;
        line(aliasX, minY, aliasZ, aliasX, maxY, aliasZ, color, WORLD_SPAWN_MARKER_WIDTH);

        double markerY = Math.clamp(spawn.getY() + 0.05D, minY + 0.05D, maxY - 0.05D);
        if (!GlobeConfig.allowMobsAtWorldSpawn()) {
            Gizmos.circle(
                    new Vec3(aliasX, markerY, aliasZ),
                    WORLD_SPAWN_EXCLUSION_BLOCKS,
                    GizmoStyle.strokeAndFill(WORLD_SPAWN_COLOR, 3.0F, WORLD_SPAWN_RADIUS_FILL));
        }

        Gizmos.billboardText(
                String.format(Locale.ROOT, "World spawn %d %d %d", spawn.getX(), spawn.getY(), spawn.getZ()),
                new Vec3(aliasX, Math.min(maxY - 1.0D, markerY + 2.0D), aliasZ),
                TextGizmo.Style.forColorAndCentered(color).withScale(0.28F)).setAlwaysOnTop();
    }

    private static double nearestAlias(double canonicalCoordinate, double cameraCoordinate, int tileBlocks) {
        return canonicalCoordinate + (double) Math.round((cameraCoordinate - canonicalCoordinate) / (double) tileBlocks) * (double) tileBlocks;
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1, int color, float width) {
        Gizmos.line(new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), color, width);
    }
}
