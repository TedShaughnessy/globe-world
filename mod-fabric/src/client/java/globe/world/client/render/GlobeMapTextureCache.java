package globe.world.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import globe.world.GlobeWorld;
import globe.world.network.GlobeMapSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;

public final class GlobeMapTextureCache {
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "dynamic/globe_map");
    private static final Identifier HELD_TEXTURE_ID = Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "dynamic/globe_map_held");
    private static final Identifier HELD_VIEWPORT_TEXTURE_ID = Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "dynamic/globe_map_held_viewport");
    private static final int HELD_VIEWPORT_RESOLUTION = 160;
    private static final int UNKNOWN_COLOR = 0x00000000;
    private static final int HELD_UNKNOWN_COLOR = 0x30385A66;
    private static final int HELD_DISCOVERED_ALPHA = 0xD8;
    private static final int EMPTY_DISCOVERED_COLOR = 0xFF6C6047;
    private static final int HELD_GRID_COLOR = 0x226DC7D8;
    private static final int HELD_CENTER_GRID_COLOR = 0x55D8F8FF;
    private static final int CHUNK_SIZE_BLOCKS = 16;

    private static Identifier dimension;
    private static int resolution;
    private static int revision = -1;
    private static boolean surveyMode;
    private static DynamicTexture texture;
    private static DynamicTexture heldTexture;
    private static DynamicTexture heldViewportTexture;

    private GlobeMapTextureCache() {
    }

    public static void applySnapshot(final GlobeMapSnapshotPayload payload) {
        if (payload.resolution() <= 0
                || payload.colors().length != payload.resolution() * payload.resolution()
                || payload.discovered().length != (payload.colors().length + 7) / 8) {
            return;
        }

        if (texture == null || heldTexture == null || resolution != payload.resolution()) {
            release();
            texture = new DynamicTexture(() -> "Globe map", payload.resolution(), payload.resolution(), false);
            heldTexture = new DynamicTexture(() -> "Held globe map", payload.resolution(), payload.resolution(), false);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
            Minecraft.getInstance().getTextureManager().register(HELD_TEXTURE_ID, heldTexture);
        }

        dimension = payload.dimension();
        resolution = payload.resolution();
        revision = payload.revision();

        NativeImage pixels = texture.getPixels();
        NativeImage heldPixels = heldTexture.getPixels();
        surveyMode = payload.surveyMode();
        if (surveyMode) {
            fillTransparent(pixels);
            fillTransparent(heldPixels);
        } else {
            for (int z = 0; z < resolution; z++) {
                for (int x = 0; x < resolution; x++) {
                    int index = x + z * resolution;
                    pixels.setPixel(x, z, color(payload, index, false));
                    heldPixels.setPixel(x, z, color(payload, index, true));
                }
            }
        }
        texture.upload();
        heldTexture.upload();
    }

    public static Identifier textureForCurrentDimension() {
        return textureForCurrentDimension(texture, TEXTURE_ID);
    }

    public static Identifier heldTextureForCurrentDimension() {
        return textureForCurrentDimension(heldTexture, HELD_TEXTURE_ID);
    }

    public static Identifier updateHeldViewportForCurrentDimension(
            final double centerX,
            final double centerZ,
            final int spanBlocks,
            final int tileSizeBlocks) {
        Identifier source = heldTextureForCurrentDimension();
        if (source == null || heldTexture == null || resolution <= 0) {
            return null;
        }
        if (surveyMode) {
            return null;
        }

        if (heldViewportTexture == null) {
            heldViewportTexture = new DynamicTexture(
                    () -> "Held globe map viewport",
                    HELD_VIEWPORT_RESOLUTION,
                    HELD_VIEWPORT_RESOLUTION,
                    false);
            Minecraft.getInstance().getTextureManager().register(HELD_VIEWPORT_TEXTURE_ID, heldViewportTexture);
        }

        NativeImage sourcePixels = heldTexture.getPixels();
        NativeImage targetPixels = heldViewportTexture.getPixels();
        fillHeldViewport(sourcePixels, targetPixels, centerX, centerZ, spanBlocks, tileSizeBlocks);
        heldViewportTexture.upload();
        return HELD_VIEWPORT_TEXTURE_ID;
    }

    private static Identifier textureForCurrentDimension(final DynamicTexture candidate, final Identifier id) {
        Minecraft client = Minecraft.getInstance();
        if (candidate == null || client.level == null || dimension == null) {
            return null;
        }
        return client.level.dimension().identifier().equals(dimension) ? id : null;
    }

    public static int revision() {
        return revision;
    }

    public static void reset() {
        release();
        dimension = null;
        resolution = 0;
        revision = -1;
        surveyMode = false;
    }

    private static void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
            texture = null;
        }
        if (heldTexture != null) {
            Minecraft.getInstance().getTextureManager().release(HELD_TEXTURE_ID);
            heldTexture = null;
        }
        if (heldViewportTexture != null) {
            Minecraft.getInstance().getTextureManager().release(HELD_VIEWPORT_TEXTURE_ID);
            heldViewportTexture = null;
        }
    }

    private static void fillHeldViewport(
            final NativeImage sourcePixels,
            final NativeImage targetPixels,
            final double centerX,
            final double centerZ,
            final int spanBlocks,
            final int tileSizeBlocks) {
        double blockRadius = spanBlocks * 0.5D;
        double pixelCenter = HELD_VIEWPORT_RESOLUTION * 0.5D;
        double fadeStart = 0.86D;
        int gridSpacingBlocks = heldGridSpacingBlocks(spanBlocks);

        for (int y = 0; y < HELD_VIEWPORT_RESOLUTION; y++) {
            double normalizedForward = (pixelCenter - (y + 0.5D)) / pixelCenter;
            for (int x = 0; x < HELD_VIEWPORT_RESOLUTION; x++) {
                double normalizedRight = (x + 0.5D - pixelCenter) / pixelCenter;
                double radius = Math.sqrt(normalizedRight * normalizedRight + normalizedForward * normalizedForward);
                if (radius >= 1.0D) {
                    targetPixels.setPixel(x, y, 0);
                    continue;
                }

                double blockRight = normalizedRight * blockRadius;
                double blockForward = normalizedForward * blockRadius;
                double blockX = centerX - blockRight;
                double blockZ = centerZ + blockForward;
                int sourceX = pixelForBlock(blockX, tileSizeBlocks);
                int sourceZ = pixelForBlock(blockZ, tileSizeBlocks);
                int color = sourcePixels.getPixel(sourceX, sourceZ);
                color = withAlpha(color, Math.round(alpha(color) * edgeFade(radius, fadeStart)));
                color = applyHeldGrid(color, blockX, blockZ, normalizedRight, normalizedForward, gridSpacingBlocks, spanBlocks);
                targetPixels.setPixel(x, y, color);
            }
        }
    }

    private static void fillTransparent(final NativeImage pixels) {
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                pixels.setPixel(x, z, 0);
            }
        }
    }

    private static int pixelForBlock(final double block, final int tileSizeBlocks) {
        double wrapped = positiveModulo(block + tileSizeBlocks / 2.0D, tileSizeBlocks);
        return Math.floorMod((int)Math.floor(wrapped / tileSizeBlocks * resolution), resolution);
    }

    private static double positiveModulo(final double value, final double modulus) {
        return value - Math.floor(value / modulus) * modulus;
    }

    private static int heldGridSpacingBlocks(final int spanBlocks) {
        int visibleChunks = Math.max(1, (int)Math.ceil(spanBlocks / (double)CHUNK_SIZE_BLOCKS));
        int spacingChunks = 1;
        while (visibleChunks / spacingChunks > 16) {
            spacingChunks *= 2;
        }
        return spacingChunks * CHUNK_SIZE_BLOCKS;
    }

    private static int applyHeldGrid(
            final int color,
            final double blockX,
            final double blockZ,
            final double normalizedRight,
            final double normalizedForward,
            final int gridSpacingBlocks,
            final int spanBlocks) {
        double pixelBlocks = spanBlocks / (double)HELD_VIEWPORT_RESOLUTION;
        if (Math.abs(normalizedRight) <= 1.0D / HELD_VIEWPORT_RESOLUTION
                || Math.abs(normalizedForward) <= 1.0D / HELD_VIEWPORT_RESOLUTION) {
            return blend(color, HELD_CENTER_GRID_COLOR);
        }
        if (nearGridLine(blockX, gridSpacingBlocks, pixelBlocks)
                || nearGridLine(blockZ, gridSpacingBlocks, pixelBlocks)) {
            return blend(color, HELD_GRID_COLOR);
        }
        return color;
    }

    private static boolean nearGridLine(final double block, final int spacingBlocks, final double pixelBlocks) {
        double wrapped = positiveModulo(block + spacingBlocks * 0.5D, spacingBlocks) - spacingBlocks * 0.5D;
        return Math.abs(wrapped) <= Math.max(0.5D, pixelBlocks * 0.45D);
    }

    private static float edgeFade(final double radius, final double fadeStart) {
        if (radius <= fadeStart) {
            return 1.0F;
        }
        double t = (radius - fadeStart) / (1.0D - fadeStart);
        return (float)(1.0D - t * t * (3.0D - 2.0D * t));
    }

    private static int alpha(final int color) {
        return color >>> 24;
    }

    private static int color(final GlobeMapSnapshotPayload payload, final int index, final boolean held) {
        if (!isDiscovered(payload.discovered(), index)) {
            return held ? HELD_UNKNOWN_COLOR : UNKNOWN_COLOR;
        }

        int color = MapColor.getColorFromPackedId(payload.colors()[index]);
        if (color == 0) {
            color = EMPTY_DISCOVERED_COLOR;
        }
        return held ? withAlpha(color, HELD_DISCOVERED_ALPHA) : color;
    }

    private static int withAlpha(final int color, final int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int blend(final int base, final int overlay) {
        int alpha = overlay >>> 24;
        int inverse = 255 - alpha;
        int r = (((base >> 16) & 0xFF) * inverse + ((overlay >> 16) & 0xFF) * alpha) / 255;
        int g = (((base >> 8) & 0xFF) * inverse + ((overlay >> 8) & 0xFF) * alpha) / 255;
        int b = ((base & 0xFF) * inverse + (overlay & 0xFF) * alpha) / 255;
        return (base & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static boolean isDiscovered(final byte[] discovered, final int index) {
        return (discovered[index >> 3] & (1 << (index & 7))) != 0;
    }
}
