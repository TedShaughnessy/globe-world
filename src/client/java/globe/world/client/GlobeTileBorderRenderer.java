package globe.world.client;

import globe.world.config.GlobeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class GlobeTileBorderRenderer implements net.minecraft.client.renderer.debug.DebugRenderer.SimpleDebugRenderer {
    private static final int CANONICAL_COLOR = ARGB.color(255, 0, 220, 180);
    private static final int CURRENT_ALIAS_COLOR = ARGB.color(255, 255, 220, 0);
    private static final int OTHER_ALIAS_COLOR = ARGB.color(150, 120, 160, 255);

    private final Minecraft minecraft;

    public GlobeTileBorderRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues, Frustum frustum, float partialTicks) {
        if (!GlobeDebugState.tileBordersEnabled() || !GlobeConfig.enabled() || this.minecraft.level == null) {
            return;
        }

        Entity cameraEntity = this.minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return;
        }

        int tileBlocks = GlobeConfig.tileSizeBlocks();
        int canonicalMin = -(GlobeConfig.tileSizeChunks() / 2) * 16;
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
                float width = tileX == currentTileX && tileZ == currentTileZ ? 4.0F : 2.0F;
                drawTile(x0, z0, x1, z1, minY, maxY, color, width);
            }
        }
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
            line(x, minY, z0, x, maxY, z0, color, 1.0F);
            line(x, minY, z1, x, maxY, z1, color, 1.0F);
        }

        for (int z = z0 + 16; z < z1; z += 16) {
            line(x0, minY, z, x0, maxY, z, color, 1.0F);
            line(x1, minY, z, x1, maxY, z, color, 1.0F);
        }
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1, int color, float width) {
        Gizmos.line(new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), color, width);
    }
}
