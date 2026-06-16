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
    private static final int CANONICAL_COLOR = ARGB.color(255, 0, 220, 180);
    private static final int CURRENT_ALIAS_COLOR = ARGB.color(255, 255, 220, 0);
    private static final int OTHER_ALIAS_COLOR = ARGB.color(150, 120, 160, 255);
    private static final int WORLD_SPAWN_COLOR = ARGB.color(255, 255, 70, 70);
    private static final int WORLD_SPAWN_ALLOWED_COLOR = ARGB.color(170, 255, 150, 80);
    private static final int WORLD_SPAWN_RADIUS_FILL = ARGB.color(35, 255, 70, 70);
    private static final float CURRENT_TILE_WIDTH = 10.0F;
    private static final float OTHER_TILE_WIDTH = 5.0F;
    private static final float CHUNK_GRID_WIDTH = 2.5F;
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

        for (int tileX = currentTileX - 2; tileX <= currentTileX + 2; tileX++) {
            for (int tileZ = currentTileZ - 2; tileZ <= currentTileZ + 2; tileZ++) {
                int x0 = canonicalMin + tileX * tileBlocks;
                int z0 = canonicalMin + tileZ * tileBlocks;
                int x1 = x0 + tileBlocks;
                int z1 = z0 + tileBlocks;
                int color = colorFor(tileX, tileZ, currentTileX, currentTileZ);
                float width = tileX == currentTileX && tileZ == currentTileZ ? CURRENT_TILE_WIDTH : OTHER_TILE_WIDTH;
                drawTile(x0, z0, x1, z1, minY, maxY, color, width);
            }
        }

        drawWorldSpawnMarker(cameraEntity, tiling, tileBlocks, minY, maxY);
    }

    private static int colorFor(int tileX, int tileZ, int currentTileX, int currentTileZ) {
        if (tileX == 0 && tileZ == 0) {
            return CANONICAL_COLOR;
        }
        if (tileX == currentTileX && tileZ == currentTileZ) {
            return CURRENT_ALIAS_COLOR;
        }
        return OTHER_ALIAS_COLOR;
    }

    private static void drawTile(int x0, int z0, int x1, int z1, int minY, int maxY, int color, float width) {
        line(x0, minY, z0, x1, minY, z0, color, width);
        line(x1, minY, z0, x1, minY, z1, color, width);
        line(x1, minY, z1, x0, minY, z1, color, width);
        line(x0, minY, z1, x0, minY, z0, color, width);

        line(x0, maxY, z0, x1, maxY, z0, color, width);
        line(x1, maxY, z0, x1, maxY, z1, color, width);
        line(x1, maxY, z1, x0, maxY, z1, color, width);
        line(x0, maxY, z1, x0, maxY, z0, color, width);

        line(x0, minY, z0, x0, maxY, z0, color, width);
        line(x1, minY, z0, x1, maxY, z0, color, width);
        line(x1, minY, z1, x1, maxY, z1, color, width);
        line(x0, minY, z1, x0, maxY, z1, color, width);

        for (int x = x0 + 16; x < x1; x += 16) {
            line(x, minY, z0, x, maxY, z0, color, CHUNK_GRID_WIDTH);
            line(x, minY, z1, x, maxY, z1, color, CHUNK_GRID_WIDTH);
        }

        for (int z = z0 + 16; z < z1; z += 16) {
            line(x0, minY, z, x0, maxY, z, color, CHUNK_GRID_WIDTH);
            line(x1, minY, z, x1, maxY, z, color, CHUNK_GRID_WIDTH);
        }
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
