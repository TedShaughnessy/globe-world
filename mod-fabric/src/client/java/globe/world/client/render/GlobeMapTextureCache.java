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
    private static final int UNKNOWN_COLOR = 0x00000000;
    private static final int EMPTY_DISCOVERED_COLOR = 0xFF6C6047;

    private static Identifier dimension;
    private static int resolution;
    private static int revision = -1;
    private static DynamicTexture texture;

    private GlobeMapTextureCache() {
    }

    public static void applySnapshot(final GlobeMapSnapshotPayload payload) {
        if (payload.resolution() <= 0
                || payload.colors().length != payload.resolution() * payload.resolution()
                || payload.discovered().length != (payload.colors().length + 7) / 8) {
            return;
        }

        if (texture == null || resolution != payload.resolution()) {
            release();
            texture = new DynamicTexture(() -> "Globe map", payload.resolution(), payload.resolution(), false);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
        }

        dimension = payload.dimension();
        resolution = payload.resolution();
        revision = payload.revision();

        NativeImage pixels = texture.getPixels();
        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                int index = x + z * resolution;
                pixels.setPixel(x, z, color(payload, index));
            }
        }
        texture.upload();
    }

    public static Identifier textureForCurrentDimension() {
        Minecraft client = Minecraft.getInstance();
        if (texture == null || client.level == null || dimension == null) {
            return null;
        }
        return client.level.dimension().identifier().equals(dimension) ? TEXTURE_ID : null;
    }

    public static int revision() {
        return revision;
    }

    public static void reset() {
        release();
        dimension = null;
        resolution = 0;
        revision = -1;
    }

    private static void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
            texture = null;
        }
    }

    private static int color(final GlobeMapSnapshotPayload payload, final int index) {
        if (!isDiscovered(payload.discovered(), index)) {
            return UNKNOWN_COLOR;
        }

        int color = MapColor.getColorFromPackedId(payload.colors()[index]);
        return color == 0 ? EMPTY_DISCOVERED_COLOR : color;
    }

    private static boolean isDiscovered(final byte[] discovered, final int index) {
        return (discovered[index >> 3] & (1 << (index & 7))) != 0;
    }
}
