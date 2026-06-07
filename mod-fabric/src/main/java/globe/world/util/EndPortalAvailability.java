package globe.world.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EndPortalAvailability {
    private EndPortalAvailability() {
    }

    public static Report classify(ServerLevel level) {
        return debugReport(level, false);
    }

    public static Report debugReport(ServerLevel level, boolean validateStarts) {
        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return Report.disabled(level, tiling, "not_overworld");
        }
        if (!tiling.enabled()) {
            return Report.disabled(level, tiling, "tiling_disabled");
        }

        boolean generateStructures = level.getServer().getWorldGenSettings().options().generateStructures();
        if (!generateStructures) {
            return Report.fallback(level, tiling, generateStructures, "structures_disabled");
        }

        Holder.Reference<Structure> stronghold = strongholdHolder(level);
        if (stronghold == null) {
            return Report.fallback(level, tiling, generateStructures, "stronghold_missing");
        }
        if (!stronghold.is(StructureTags.EYE_OF_ENDER_LOCATED)) {
            return Report.fallback(level, tiling, generateStructures, "stronghold_not_eye_located");
        }

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        List<StructurePlacement> placements = state.getPlacementsForStructure(stronghold);
        int placementCount = 0;
        int rawCandidateCount = 0;
        int canonicalCandidateCount = 0;
        int validatedStartCount = 0;
        int validStartCount = 0;
        int validatedForcedStartCount = 0;
        int validForcedStartCount = 0;
        Set<ChunkPos> wrappedAliases = new HashSet<>();
        Set<ChunkPos> canonicalCandidates = new HashSet<>();

        for (StructurePlacement placement : placements) {
            if (!(placement instanceof ConcentricRingsStructurePlacement rings)) {
                continue;
            }

            placementCount++;
            List<ChunkPos> ringPositions = state.getRingPositionsFor(rings);
            if (ringPositions == null) {
                continue;
            }

            for (ChunkPos rawPos : ringPositions) {
                rawCandidateCount++;
                ChunkPos wrapped = CoordUtil.wrapChunkPos(tiling, rawPos);
                wrappedAliases.add(wrapped);
                if (!wrapped.equals(rawPos)) {
                    continue;
                }

                canonicalCandidateCount++;
                canonicalCandidates.add(rawPos);
            }
        }

        if (validateStarts) {
            for (ChunkPos candidate : canonicalCandidates) {
                validatedStartCount++;
                ChunkAccess chunk = level.getChunk(candidate.x(), candidate.z(), ChunkStatus.STRUCTURE_STARTS, true);
                StructureStart start = level.structureManager().getStartForStructure(
                        SectionPos.bottomOf(chunk),
                        stronghold.value(),
                        chunk
                );
                if (start != null && start.isValid()) {
                    validStartCount++;
                }
            }
        }

        BlockPos forcedStrongholdTarget = canonicalCandidateCount == 0
                ? ForcedProgressionStructures.forcedOverworldStrongholdTarget(level).orElse(null)
                : null;
        if (validateStarts && forcedStrongholdTarget != null) {
            validatedForcedStartCount++;
            if (ForcedProgressionStructures.validatedForcedOverworldStrongholdTarget(level).isPresent()) {
                validForcedStartCount++;
            }
        }

        Status status;
        String reason;
        if (canonicalCandidateCount > 0) {
            status = Status.VANILLA_STRONGHOLD_PRESENT;
            reason = "canonical_ring_candidate";
        } else if (forcedStrongholdTarget != null && (!validateStarts || validForcedStartCount > 0)) {
            status = Status.FORCED_STRONGHOLD_AVAILABLE;
            reason = validateStarts ? "forced_stronghold_start_valid" : "forced_stronghold_configured";
        } else {
            status = Status.FALLBACK_PORTAL_REQUIRED;
            reason = forcedStrongholdTarget == null ? "no_canonical_ring_candidate" : "forced_stronghold_start_invalid";
        }
        return new Report(
                status,
                reason,
                level.dimension().identifier().toString(),
                tiling.enabled(),
                tiling.tileSizeChunks(),
                tiling.tileSizeBlocks(),
                generateStructures,
                placementCount,
                rawCandidateCount,
                canonicalCandidateCount,
                wrappedAliases.size(),
                forcedStrongholdTarget,
                validateStarts,
                validatedStartCount,
                validStartCount,
                validatedForcedStartCount,
                validForcedStartCount
        );
    }

    private static Holder.Reference<Structure> strongholdHolder(ServerLevel level) {
        Registry<Structure> structures = level.registryAccess().lookup(Registries.STRUCTURE).orElse(null);
        if (structures == null) {
            return null;
        }
        return structures.get(BuiltinStructures.STRONGHOLD.identifier()).orElse(null);
    }

    public enum Status {
        DISABLED,
        VANILLA_STRONGHOLD_PRESENT,
        FORCED_STRONGHOLD_AVAILABLE,
        FALLBACK_PORTAL_REQUIRED
    }

    public record Report(
            Status status,
            String reason,
            String dimension,
            boolean tilingEnabled,
            int tileSizeChunks,
            int tileSizeBlocks,
            boolean generateStructures,
            int strongholdPlacementCount,
            int rawCandidateCount,
            int canonicalCandidateCount,
            int distinctWrappedAliasCount,
            BlockPos forcedStrongholdTarget,
            boolean validatedStarts,
            int validatedStartCount,
            int validStartCount,
            int validatedForcedStartCount,
            int validForcedStartCount
    ) {
        private static Report disabled(ServerLevel level, DimensionTiling tiling, String reason) {
            return new Report(
                    Status.DISABLED,
                    reason,
                    level.dimension().identifier().toString(),
                    tiling.enabled(),
                    tiling.tileSizeChunks(),
                    tiling.tileSizeBlocks(),
                    false,
                    0,
                    0,
                    0,
                    0,
                    null,
                    false,
                    0,
                    0,
                    0,
                    0
            );
        }

        private static Report fallback(
                ServerLevel level,
                DimensionTiling tiling,
                boolean generateStructures,
                String reason) {
            return new Report(
                    Status.FALLBACK_PORTAL_REQUIRED,
                    reason,
                    level.dimension().identifier().toString(),
                    tiling.enabled(),
                    tiling.tileSizeChunks(),
                    tiling.tileSizeBlocks(),
                    generateStructures,
                    0,
                    0,
                    0,
                    0,
                    null,
                    false,
                    0,
                    0,
                    0,
                    0
            );
        }

        public String validationSummary() {
            if (!validatedStarts) {
                return "not_run";
            }
            return validStartCount + "/" + validatedStartCount + " canonical starts valid, "
                    + validForcedStartCount + "/" + validatedForcedStartCount + " forced starts valid";
        }
    }
}
