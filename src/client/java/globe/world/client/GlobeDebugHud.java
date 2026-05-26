package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

public final class GlobeDebugHud {
    private GlobeDebugHud() {
    }

    public static void appendLines(List<String> lines) {
        if (!GlobeConfig.enabled() && !GlobeConfig.netherEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getCameraEntity() == null) {
            return;
        }

        DimensionTiling currentTiling = DimensionTiling.forLevel(minecraft.level);
        DimensionTiling overworldTiling = DimensionTiling.forDimension(Level.OVERWORLD);
        DimensionTiling netherTiling = DimensionTiling.forDimension(Level.NETHER);
        BlockPos pos = minecraft.getCameraEntity().blockPosition();
        ChunkPos chunk = ChunkPos.containing(pos);
        int canonBlockX = CoordUtil.wrapBlock(currentTiling, pos.getX());
        int canonBlockZ = CoordUtil.wrapBlock(currentTiling, pos.getZ());
        int canonChunkX = CoordUtil.wrapChunk(currentTiling, chunk.x());
        int canonChunkZ = CoordUtil.wrapChunk(currentTiling, chunk.z());
        boolean canonChunkLoaded = minecraft.level.getChunkSource().getChunk(canonChunkX, canonChunkZ, false) != null;

        lines.add("");
        lines.add("[Globe World]");
        lines.add(String.format(Locale.ROOT, "Current dimension: %s tile %s",
                minecraft.level.dimension().identifier(), tileSummary(currentTiling)));
        lines.add(String.format(Locale.ROOT, "Overworld tile: %s", tileSummary(overworldTiling)));
        lines.add(String.format(Locale.ROOT, "Nether tile: %s", tileSummary(netherTiling)));
        lines.add(String.format(Locale.ROOT, "Nether 1/8 requested/effective: %s/%s",
                yesNo(GlobeConfig.netherOneEighthOverworldSize()),
                yesNo(GlobeConfig.effectiveNetherOneEighthOverworldSize())));
        lines.add(String.format(Locale.ROOT, "World block: %d %d", pos.getX(), pos.getZ()));
        lines.add(String.format(Locale.ROOT, "Canon block: %d %d", canonBlockX, canonBlockZ));
        lines.add(String.format(Locale.ROOT, "World chunk: %d %d", chunk.x(), chunk.z()));
        lines.add(String.format(Locale.ROOT, "Canon chunk: %d %d", canonChunkX, canonChunkZ));
        lines.add(String.format(Locale.ROOT, "Tile alias: %+d %+d",
                CoordUtil.tileAliasChunk(currentTiling, chunk.x()),
                CoordUtil.tileAliasChunk(currentTiling, chunk.z())));
        lines.add("In canon tile: " + yesNo(CoordUtil.isInCanonicalTile(currentTiling, pos)));
        lines.add("Canon chunk loaded client-side: " + yesNo(canonChunkLoaded));
    }

    private static String tileSummary(DimensionTiling tiling) {
        if (!tiling.enabled()) {
            return "disabled";
        }
        return String.format(Locale.ROOT, "%d chunks / %d blocks", tiling.tileSizeChunks(), tiling.tileSizeBlocks());
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
