package globe.world.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import globe.world.GlobeWorld;
import globe.world.network.GlobeAtlasSurveyWindowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class GlobeAtlasSurveyTextureCache {
    private static final int MAX_TEXTURES = 8;
    private static final Identifier HELD_TEXTURE_ID = Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "dynamic/atlas_survey_held");
    private static final int UNDISCOVERED_COLOR = 0x4A162533;
    private static final int UNKNOWN_BIOME_COLOR = 0xB8667480;
    private static final int GRID_COLOR = 0x226DC7D8;
    private static final int CENTER_GRID_COLOR = 0x55D8F8FF;
    private static final double HELD_FADE_START = 0.86D;

    private static final Map<Key, Entry> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
    private static DynamicTexture heldTexture;
    private static Key heldSourceKey;
    private static int nextUploadSerial;
    private static int heldUploadSerial = -1;
    private static int heldSize;

    private GlobeAtlasSurveyTextureCache() {
    }

    public static void applySurveyWindow(final GlobeAtlasSurveyWindowPayload payload) {
        int windowChunks = payload.windowChunks();
        if (windowChunks <= 0 || windowChunks > GlobeAtlasSurveyWindowPayload.MAX_WINDOW_CHUNKS) {
            return;
        }

        int cells = windowChunks * windowChunks;
        if (payload.discovered().length != (cells + 7) / 8
                || payload.biomeIndexes().length != cells) {
            return;
        }

        Key key = new Key(payload.dimension(), payload.centerChunkX(), payload.centerChunkZ(), windowChunks);
        Entry entry = TEXTURES.get(key);
        if (entry == null) {
            Identifier id = textureId(key);
            DynamicTexture texture = new DynamicTexture(
                    () -> "Globe atlas survey " + key.centerChunkX + "," + key.centerChunkZ,
                    windowChunks,
                    windowChunks,
                    false);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            entry = new Entry(id, texture);
            TEXTURES.put(key, entry);
            evictOldTextures();
        }
        NativeImage pixels = entry.texture.getPixels();
        for (int z = 0; z < windowChunks; z++) {
            for (int x = 0; x < windowChunks; x++) {
                int index = x + z * windowChunks;
                int color = baseColor(payload, index);
                color = applyGrid(color, x, z, windowChunks);
                pixels.setPixel(x, z, color);
            }
        }
        for (GlobeAtlasSurveyWindowPayload.Marker marker : payload.markers()) {
            drawMarker(pixels, marker, windowChunks);
        }
        entry.texture.upload();
        entry.uploadSerial = ++nextUploadSerial;
    }

    public static Identifier textureForCurrentDimension(
            final int centerChunkX,
            final int centerChunkZ,
            final int windowChunks) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }

        Entry entry = TEXTURES.get(new Key(client.level.dimension().identifier(), centerChunkX, centerChunkZ, windowChunks));
        return entry == null ? null : entry.id;
    }

    public static Identifier heldTextureForCurrentDimension(
            final int centerChunkX,
            final int centerChunkZ,
            final int windowChunks) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return null;
        }

        Key key = new Key(client.level.dimension().identifier(), centerChunkX, centerChunkZ, windowChunks);
        Entry entry = TEXTURES.get(key);
        if (entry == null) {
            return heldTextureFallback(key);
        }

        if (heldTexture == null || heldSize != windowChunks) {
            releaseHeldTexture();
            heldTexture = new DynamicTexture(() -> "Held globe atlas survey", windowChunks, windowChunks, false);
            Minecraft.getInstance().getTextureManager().register(HELD_TEXTURE_ID, heldTexture);
            heldSize = windowChunks;
            heldSourceKey = null;
            heldUploadSerial = -1;
        }

        if (!key.equals(heldSourceKey) || heldUploadSerial != entry.uploadSerial) {
            fillHeldTexture(entry.texture.getPixels(), heldTexture.getPixels(), windowChunks);
            heldTexture.upload();
            heldSourceKey = key;
            heldUploadSerial = entry.uploadSerial;
        }
        return HELD_TEXTURE_ID;
    }

    public static void reset() {
        for (Entry entry : TEXTURES.values()) {
            Minecraft.getInstance().getTextureManager().release(entry.id);
        }
        TEXTURES.clear();
        releaseHeldTexture();
    }

    private static void evictOldTextures() {
        while (TEXTURES.size() > MAX_TEXTURES) {
            Key oldest = TEXTURES.keySet().iterator().next();
            Entry removed = TEXTURES.remove(oldest);
            if (removed != null) {
                Minecraft.getInstance().getTextureManager().release(removed.id);
            }
        }
    }

    private static Identifier heldTextureFallback(final Key requestedKey) {
        if (heldTexture == null || heldSourceKey == null || heldSize != requestedKey.windowChunks) {
            return null;
        }
        return heldSourceKey.dimension.equals(requestedKey.dimension) ? HELD_TEXTURE_ID : null;
    }

    private static void releaseHeldTexture() {
        if (heldTexture != null) {
            Minecraft.getInstance().getTextureManager().release(HELD_TEXTURE_ID);
            heldTexture = null;
        }
        heldSourceKey = null;
        heldUploadSerial = -1;
        heldSize = 0;
    }

    private static int baseColor(final GlobeAtlasSurveyWindowPayload payload, final int index) {
        if (!isDiscovered(payload.discovered(), index)) {
            return UNDISCOVERED_COLOR;
        }

        int paletteIndex = Byte.toUnsignedInt(payload.biomeIndexes()[index]);
        if (paletteIndex <= 0 || paletteIndex > payload.biomePalette().size()) {
            return UNKNOWN_BIOME_COLOR;
        }
        return biomeColor(payload.biomePalette().get(paletteIndex - 1));
    }

    private static int biomeColor(final Identifier biome) {
        String id = (biome.getNamespace() + ":" + biome.getPath()).toLowerCase(Locale.ROOT);
        if (id.contains("deep_dark")) return 0xD8262D46;
        if (id.contains("mushroom")) return 0xD8B06EB5;
        if (id.contains("badlands") || id.contains("mesa")) return 0xD8C76D3C;
        if (id.contains("desert")) return 0xD8D6B66A;
        if (id.contains("savanna")) return 0xD8B9A34C;
        if (id.contains("jungle")) return 0xD82E8E4D;
        if (id.contains("swamp") || id.contains("mangrove")) return 0xD84D7356;
        if (id.contains("taiga")) return 0xD8427465;
        if (id.contains("snow") || id.contains("frozen") || id.contains("ice")) return 0xD8C8DFE7;
        if (id.contains("forest") || id.contains("grove")) return 0xD83F8A4B;
        if (id.contains("river")) return 0xD84E8ECF;
        if (id.contains("ocean")) return 0xD8305F9E;
        if (id.contains("beach")) return 0xD8D9C982;
        if (id.contains("plains") || id.contains("meadow")) return 0xD87EB85C;
        if (id.contains("cave") || id.contains("dripstone") || id.contains("lush")) return 0xD86E7C64;
        return stableColor(biome);
    }

    private static int stableColor(final Identifier biome) {
        int hash = biome.toString().hashCode();
        int r = 74 + Math.floorMod(hash, 110);
        int g = 74 + Math.floorMod(hash >>> 8, 110);
        int b = 74 + Math.floorMod(hash >>> 16, 110);
        return 0xD8000000 | (r << 16) | (g << 8) | b;
    }

    private static int applyGrid(final int color, final int x, final int z, final int size) {
        if (x == size / 2 || z == size / 2) {
            return blend(color, CENTER_GRID_COLOR);
        }
        return (x & 15) == 0 || (z & 15) == 0 ? blend(color, GRID_COLOR) : color;
    }

    private static void drawMarker(final NativeImage pixels, final GlobeAtlasSurveyWindowPayload.Marker marker, final int size) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) {
                    continue;
                }
                int x = marker.x() + dx;
                int z = marker.z() + dz;
                if (x >= 0 && x < size && z >= 0 && z < size) {
                    pixels.setPixel(x, z, marker.color());
                }
            }
        }
    }

    private static void fillHeldTexture(final NativeImage source, final NativeImage target, final int size) {
        double center = size * 0.5D;
        for (int z = 0; z < size; z++) {
            double normalizedForward = (center - (z + 0.5D)) / center;
            for (int x = 0; x < size; x++) {
                double normalizedRight = (x + 0.5D - center) / center;
                double radius = Math.sqrt(normalizedRight * normalizedRight + normalizedForward * normalizedForward);
                if (radius >= 1.0D) {
                    target.setPixel(x, z, 0);
                    continue;
                }

                int color = source.getPixel(x, z);
                target.setPixel(x, z, withAlpha(color, Math.round(alpha(color) * edgeFade(radius))));
            }
        }
    }

    private static float edgeFade(final double radius) {
        if (radius <= HELD_FADE_START) {
            return 1.0F;
        }
        double t = (radius - HELD_FADE_START) / (1.0D - HELD_FADE_START);
        return (float)(1.0D - t * t * (3.0D - 2.0D * t));
    }

    private static int alpha(final int color) {
        return color >>> 24;
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

    private static Identifier textureId(final Key key) {
        return Identifier.fromNamespaceAndPath(
                GlobeWorld.MOD_ID,
                "dynamic/atlas_survey/" + sanitize(key.dimension) + "/" + key.centerChunkX + "_" + key.centerChunkZ + "_" + key.windowChunks);
    }

    private static String sanitize(final Identifier id) {
        return (id.getNamespace() + "/" + id.getPath()).replace(':', '/');
    }

    private record Key(Identifier dimension, int centerChunkX, int centerChunkZ, int windowChunks) {
    }

    private static final class Entry {
        private final Identifier id;
        private final DynamicTexture texture;
        private int uploadSerial;

        private Entry(final Identifier id, final DynamicTexture texture) {
            this.id = id;
            this.texture = texture;
        }
    }
}
