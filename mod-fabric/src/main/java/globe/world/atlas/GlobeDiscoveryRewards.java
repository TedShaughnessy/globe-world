package globe.world.atlas;

import globe.world.map.GlobeMapSavedData;
import globe.world.util.DimensionTiling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public record GlobeDiscoveryRewards(
        int discoveredPixels,
        double discoveredPercent,
        double discoveredAreaBlocks,
        int totalPoints,
        int radiusCap,
        boolean complete) {
    public static final GlobeDiscoveryRewards EMPTY = new GlobeDiscoveryRewards(0, 0.0D, 0.0D, 0, 0, false);

    public static GlobeDiscoveryRewards get(final ServerLevel level) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return EMPTY;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (!tiling.enabled()) {
            return EMPTY;
        }

        GlobeMapSavedData data = GlobeMapSavedData.getIfPresent(level, tiling);
        if (data == null) {
            return EMPTY;
        }

        int discoveredPixels = data.discoveredPixels();
        double discoveredPercent = data.discoveredPercent();
        double discoveredAreaBlocks = data.discoveredAreaBlocks();
        boolean complete = data.complete();
        int tileSizeChunks = tiling.tileSizeChunks();
        int totalPoints;
        int radiusCap;

        if (tileSizeChunks <= 16) {
            totalPoints = complete ? 4 : 0;
            radiusCap = complete ? 32 : 0;
        } else {
            int basePoints = Math.min((int)Math.floor(Math.sqrt(discoveredAreaBlocks) / 512.0D), 16);
            int percentPoints = Math.min((int)Math.floor(discoveredPercent / 12.5D), 8);
            int worldPoints = Math.max(basePoints, percentPoints);
            if (tileSizeChunks <= 64 && discoveredPercent < 50.0D) {
                worldPoints = 0;
            }

            int completionBonus = complete ? Math.max(4, Mth.positiveCeilDiv(worldPoints, 2)) : 0;
            totalPoints = worldPoints + completionBonus;
            if (totalPoints == 0) {
                radiusCap = 0;
            } else {
                int baseRadius = 16 * (1 << Math.min(totalPoints / 4, 4));
                radiusCap = Mth.clamp(baseRadius, 32, 256);
                if (complete) {
                    radiusCap = Mth.clamp(radiusCap * 2, 64, 512);
                }
            }
        }

        return new GlobeDiscoveryRewards(
                discoveredPixels,
                discoveredPercent,
                discoveredAreaBlocks,
                totalPoints,
                radiusCap,
                complete);
    }
}
