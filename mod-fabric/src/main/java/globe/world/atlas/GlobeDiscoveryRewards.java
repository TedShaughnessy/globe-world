package globe.world.atlas;

import globe.world.map.GlobeMapSavedData;
import globe.world.topology.AtlasTorusProjection;
import globe.world.util.DimensionTiling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record GlobeDiscoveryRewards(
        int discoveredPixels,
        double discoveredPercent,
        double discoveredAreaBlocks,
        boolean surveyMode,
        int biomesVisited,
        int visitedChunks,
        int targetChunks,
        int totalPoints,
        int radiusCap,
        boolean complete,
        boolean travelUnlocked,
        List<Integer> milestoneTenths) {
    public static final GlobeDiscoveryRewards EMPTY = new GlobeDiscoveryRewards(
            0, 0.0D, 0.0D, false, 0, 0, 0, 0, 0, false, false, List.of());
    private static final int LARGE_CHUNKS_PER_POINT = 64;
    private static final int LARGE_BIOMES_PER_POINT = 10;
    private static final int LARGE_COMPLETED_BIOME_BONUS = 2;
    private static final int LARGE_PROGRESS_MILESTONES = 10;

    public static GlobeDiscoveryRewards get(final ServerLevel level) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return EMPTY;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (!tiling.enabled()) {
            return EMPTY;
        }

        AtlasTorusProjection projection = AtlasTorusProjection.create(tiling);
        int effectiveTileSizeChunks = Mth.ceil(Math.sqrt(projection.canonicalChunkCount()));
        if (GlobeAtlasSurvey.surveyMode(tiling)) {
            return largeTileRewards(level, tiling);
        }

        GlobeMapSavedData data = GlobeMapSavedData.getIfPresent(level, tiling);
        if (data == null) {
            return EMPTY;
        }

        int discoveredPixels = data.discoveredPixels();
        double discoveredPercent = data.discoveredPercent();
        double discoveredAreaBlocks = data.discoveredAreaBlocks(tiling);
        boolean complete = data.complete();
        int totalPoints;
        int radiusCap;

        if (effectiveTileSizeChunks <= 16) {
            totalPoints = complete ? 4 : 0;
            radiusCap = complete ? 32 : 0;
        } else {
            int basePoints = Math.min((int)Math.floor(Math.sqrt(discoveredAreaBlocks) / 512.0D), 16);
            int percentPoints = Math.min((int)Math.floor(discoveredPercent / 12.5D), 8);
            int worldPoints = Math.max(basePoints, percentPoints);
            if (effectiveTileSizeChunks <= 64 && discoveredPercent < 50.0D) {
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
                false,
                0,
                0,
                0,
                totalPoints,
                radiusCap,
                complete,
                complete,
                milestoneTenths(
                        effectiveTileSizeChunks,
                        Math.sqrt(projection.canonicalBlockArea())));
    }

    private static GlobeDiscoveryRewards largeTileRewards(
            final ServerLevel level,
            final DimensionTiling tiling) {
        GlobeAtlasSurveyState survey = GlobeAtlasSurveyState.getIfPresent(level, tiling).orElse(null);
        int biomesVisited = survey == null ? 0 : survey.biomeCount();
        int visitedChunks = survey == null ? 0 : survey.visitedChunks();
        int targetChunks = GlobeAtlasSurvey.travelChunks(tiling);
        double visitedChunkPercent = Math.min(100.0D, visitedChunks * 100.0D / targetChunks);
        double visitedAreaBlocks = visitedChunks * 16.0D * 16.0D;

        int biomePoints = biomesVisited / LARGE_BIOMES_PER_POINT;
        if (survey != null && survey.completedBiomes()) {
            biomePoints += LARGE_COMPLETED_BIOME_BONUS;
        }
        int chunkPoints = Math.min(visitedChunks / LARGE_CHUNKS_PER_POINT, 16);
        int totalPoints = biomePoints + chunkPoints;
        int radiusCap = 0;
        if (totalPoints > 0) {
            radiusCap = 64;
            if (totalPoints >= 8) {
                radiusCap = 128;
            }
            if (totalPoints >= 16) {
                radiusCap = 256;
            }
            if (totalPoints >= 24) {
                radiusCap = 512;
            }
        }

        boolean travelUnlocked = visitedChunks >= targetChunks;
        return new GlobeDiscoveryRewards(
                visitedChunks,
                visitedChunkPercent,
                visitedAreaBlocks,
                true,
                biomesVisited,
                visitedChunks,
                targetChunks,
                totalPoints,
                radiusCap,
                false,
                travelUnlocked,
                largeTileMilestoneTenths());
    }

    private static List<Integer> largeTileMilestoneTenths() {
        List<Integer> milestones = new ArrayList<>();
        for (int step = 1; step <= LARGE_PROGRESS_MILESTONES; step++) {
            addMilestone(milestones, step * 1000 / LARGE_PROGRESS_MILESTONES);
        }
        return List.copyOf(milestones);
    }

    private static List<Integer> milestoneTenths(final int tileSizeChunks, final double effectiveTileSizeBlocks) {
        List<Integer> milestones = new ArrayList<>();
        if (tileSizeChunks > GlobeAtlasSurvey.LARGE_TILE_CUTOFF_CHUNKS) {
            return largeTileMilestoneTenths();
        }

        if (tileSizeChunks <= 16) {
            addMilestone(milestones, 990);
            return List.copyOf(milestones);
        }

        int firstPercentPoint = tileSizeChunks <= 64 ? 4 : 1;
        for (int point = firstPercentPoint; point <= 8; point++) {
            addMilestone(milestones, point * 125);
        }

        if (tileSizeChunks > 64) {
            for (int point = 1; point <= 16; point++) {
                double threshold = Math.pow(point * 512.0D / effectiveTileSizeBlocks, 2.0D) * 1000.0D;
                int tenths = Mth.ceil(threshold);
                if (tenths > 0 && tenths < 990) {
                    addMilestone(milestones, tenths);
                }
            }
        }

        addMilestone(milestones, 990);
        milestones.sort(Integer::compareTo);
        return List.copyOf(milestones);
    }

    private static void addMilestone(final List<Integer> milestones, final int tenths) {
        int clamped = Mth.clamp(tenths, 0, 1000);
        if (!milestones.contains(clamped)) {
            milestones.add(clamped);
        }
    }
}
