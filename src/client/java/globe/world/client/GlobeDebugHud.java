package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.util.CoordUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Locale;

public final class GlobeDebugHud {
    private GlobeDebugHud() {
    }

    public static void appendLines(List<String> lines) {
        if (!GlobeConfig.enabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getCameraEntity() == null) {
            return;
        }

        BlockPos pos = minecraft.getCameraEntity().blockPosition();
        ChunkPos chunk = ChunkPos.containing(pos);
        int canonBlockX = CoordUtil.wrapBlock(pos.getX());
        int canonBlockZ = CoordUtil.wrapBlock(pos.getZ());
        int canonChunkX = CoordUtil.wrapChunk(chunk.x());
        int canonChunkZ = CoordUtil.wrapChunk(chunk.z());
        boolean canonChunkLoaded = minecraft.level.getChunkSource().getChunk(canonChunkX, canonChunkZ, false) != null;

        lines.add("");
        lines.add("[Globe World]");
        lines.add(String.format(Locale.ROOT, "World block: %d %d", pos.getX(), pos.getZ()));
        lines.add(String.format(Locale.ROOT, "Canon block: %d %d", canonBlockX, canonBlockZ));
        lines.add(String.format(Locale.ROOT, "World chunk: %d %d", chunk.x(), chunk.z()));
        lines.add(String.format(Locale.ROOT, "Canon chunk: %d %d", canonChunkX, canonChunkZ));
        lines.add(String.format(Locale.ROOT, "Tile alias: %+d %+d", CoordUtil.tileAliasChunk(chunk.x()), CoordUtil.tileAliasChunk(chunk.z())));
        lines.add("In canon tile: " + yesNo(CoordUtil.isInCanonicalTile(pos)));
        lines.add("Canon chunk loaded client-side: " + yesNo(canonChunkLoaded));
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
