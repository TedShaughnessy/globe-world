package globe.world.atlas;

import globe.world.map.GlobeMapSavedData;
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
        int visitedCells,
        int totalCells,
        int totalPoints,
        int radiusCap,
        boolean complete,
        boolean travelUnlocked,
        List<Integer> milestoneTenths) {
    public static final GlobeDiscoveryRewards EMPTY = new GlobeDiscoveryRewards(
            0, 0.0D, 0.0D, false, 0, 0, 0, 0, 0, false, false, List.of());
    private static final int LARGE_TRAVEL_BIOMES = 8;
    private static final int LARGE_TRAVEL_CELLS = 16;

    public static GlobeDiscoveryRewards get(final ServerLevel level) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return EMPTY;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (!tiling.enabled()) {
            return EMPTY;
        }

        int tileSizeChunks = tiling.tileSizeChunks();
        if (GlobeAtlasSurvey.surveyMode(tiling)) {
            return largeTileRewards(level, tiling, tileSizeChunks);
        }

        GlobeMapSavedData data = GlobeMapSavedData.getIfPresent(level, tiling);
        if (data == null) {
            return EMPTY;
        }

        int discoveredPixels = data.discoveredPixels();
        double discoveredPercent = data.discoveredPercent();
        double discoveredAreaBlocks = data.discoveredAreaBlocks();
        boolean complete = data.complete();
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
                false,
                0,
                0,
                0,
                totalPoints,
                radiusCap,
                complete,
                complete,
                milestoneTenths(tileSizeChunks, tiling.tileSizeBlocks()));
    }

    private static GlobeDiscoveryRewards largeTileRewards(
            final ServerLevel level,
            final DimensionTiling tiling,
            final int tileSizeChunks) {
        GlobeAtlasSurveyState survey = GlobeAtlasSurveyState.getIfPresent(level, tiling).orElse(null);
        int biomesVisited = survey == null ? 0 : survey.biomeCount();
        int visitedCells = survey == null ? 0 : survey.visitedCells();
        int totalCells = survey == null ? GlobeAtlasSurvey.COVERAGE_CELL_COUNT : survey.totalCells();
        double visitedCellPercent = totalCells <= 0 ? 0.0D : visitedCells * 100.0D / totalCells;
        double visitedAreaBlocks = visitedCellPercent / 100.0D * tiling.tileSizeBlocks() * (double)tiling.tileSizeBlocks();

        int biomePoints = Math.min(biomesVisited, 16);
        int cellPoints = Math.min(visitedCells / 4, 16);
        int totalPoints = biomePoints + cellPoints;
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

        boolean travelUnlocked = biomesVisited >= LARGE_TRAVEL_BIOMES && visitedCells >= LARGE_TRAVEL_CELLS;
        return new GlobeDiscoveryRewards(
                visitedCells,
                visitedCellPercent,
                visitedAreaBlocks,
                true,
                biomesVisited,
                visitedCells,
                totalCells,
                totalPoints,
                radiusCap,
                false,
                travelUnlocked,
                milestoneTenths(tileSizeChunks, tiling.tileSizeBlocks()));
    }

    private static List<Integer> milestoneTenths(final int tileSizeChunks, final int tileSizeBlocks) {
        List<Integer> milestones = new ArrayList<>();
        if (tileSizeChunks > GlobeAtlasSurvey.LARGE_TILE_CUTOFF_CHUNKS) {
            for (int point = 1; point <= 8; point++) {
                addMilestone(milestones, point * 125);
            }
            return List.copyOf(milestones);
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
                double threshold = Math.pow(point * 512.0D / tileSizeBlocks, 2.0D) * 1000.0D;
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
