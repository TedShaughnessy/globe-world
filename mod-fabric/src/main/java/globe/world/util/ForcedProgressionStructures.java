package globe.world.util;

import globe.world.GlobeWorld;
import globe.world.config.GlobeConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Optional;

public final class ForcedProgressionStructures {
    private static final long FORCED_STRONGHOLD_SALT = 0x2F1D_5A7C_3E91_4B68L;
    private static final long FORCED_PORTAL_ROOM_SALT = 0x73B8_1C40_D5E2_A916L;
    private static final int PORTAL_ROOM_ONLY_TILE_MAX_CHUNKS = 32;

    private ForcedProgressionStructures() {
    }

    public static void maybeForceOverworldStronghold(
            ServerLevel level,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            StructureManager structureManager,
            ChunkAccess centerChunk,
            ChunkGenerator generator,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> levelKey) {
        if (!Level.OVERWORLD.equals(levelKey)
                || !GlobeConfig.forceMissingStronghold()
                || SharedConstants.DEBUG_DISABLE_STRUCTURES
                || !structureManager.shouldGenerateStructures()) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled() || !isCanonical(tiling, centerChunk.getPos())) {
            return;
        }

        Holder.Reference<Structure> stronghold = strongholdHolder(registryAccess);
        if (stronghold == null || hasCanonicalVanillaStrongholdCandidate(state, stronghold, tiling)) {
            return;
        }

        ChunkPos forcedChunk = forcedStrongholdChunk(state.getLevelSeed(), tiling);
        if (!centerChunk.getPos().equals(forcedChunk)) {
            return;
        }

        SectionPos sectionPos = SectionPos.bottomOf(centerChunk);
        Structure structure = stronghold.value();
        StructureStart existing = structureManager.getStartForStructure(sectionPos, structure, centerChunk);
        if (existing != null && existing.isValid()) {
            return;
        }

        int references = existing != null ? existing.getReferences() : 0;
        StructureStart forcedStart = tiling.tileSizeChunks() <= PORTAL_ROOM_ONLY_TILE_MAX_CHUNKS
                ? portalRoomOnlyStrongholdStart(
                        structure,
                        forcedChunk,
                        references,
                        generator,
                        centerChunk,
                        state.getLevelSeed(),
                        tiling
                )
                : structure.generate(
                        stronghold,
                        levelKey,
                        registryAccess,
                        generator,
                        generator.getBiomeSource(),
                        state.randomState(),
                        structureTemplateManager,
                        state.getLevelSeed(),
                        forcedChunk,
                        references,
                        centerChunk,
                        structure.biomes()::contains
                );
        if (!forcedStart.isValid()) {
            GlobeWorld.LOGGER.warn(
                    "Failed to force missing Overworld stronghold at chunk {} {}",
                    forcedChunk.x(),
                    forcedChunk.z()
            );
            return;
        }

        structureManager.setStartForStructure(sectionPos, structure, forcedStart, centerChunk);
        GlobeWorld.LOGGER.info(
                "Forced missing Overworld stronghold at canonical chunk {} {} using {}",
                forcedChunk.x(),
                forcedChunk.z(),
                tiling.tileSizeChunks() <= PORTAL_ROOM_ONLY_TILE_MAX_CHUNKS ? "portal room only" : "vanilla layout"
        );
    }

    public static Optional<BlockPos> forcedOverworldStrongholdTarget(ServerLevel level) {
        return forcedOverworldStrongholdChunk(level)
                .map(ForcedProgressionStructures::strongholdLocateTarget);
    }

    public static Optional<BlockPos> validatedForcedOverworldStrongholdTarget(ServerLevel level) {
        Optional<ChunkPos> forcedChunk = forcedOverworldStrongholdChunk(level);
        if (forcedChunk.isEmpty()) {
            return Optional.empty();
        }

        Holder.Reference<Structure> stronghold = strongholdHolder(level.registryAccess());
        if (stronghold == null) {
            return Optional.empty();
        }

        ChunkPos chunkPos = forcedChunk.get();
        ChunkAccess chunk = level.getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.STRUCTURE_STARTS, true);
        StructureStart start = level.structureManager().getStartForStructure(
                SectionPos.bottomOf(chunk),
                stronghold.value(),
                chunk
        );
        if (start == null || !start.isValid()) {
            return Optional.empty();
        }
        Vec3i center = start.getBoundingBox().getCenter();
        return Optional.of(new BlockPos(center.getX(), center.getY(), center.getZ()));
    }

    private static Optional<ChunkPos> forcedOverworldStrongholdChunk(ServerLevel level) {
        if (!Level.OVERWORLD.equals(level.dimension())
                || !GlobeConfig.forceMissingStronghold()
                || SharedConstants.DEBUG_DISABLE_STRUCTURES
                || !level.structureManager().shouldGenerateStructures()) {
            return Optional.empty();
        }

        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled()) {
            return Optional.empty();
        }

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Holder.Reference<Structure> stronghold = strongholdHolder(level.registryAccess());
        if (stronghold == null || hasCanonicalVanillaStrongholdCandidate(state, stronghold, tiling)) {
            return Optional.empty();
        }
        return Optional.of(forcedStrongholdChunk(state.getLevelSeed(), tiling));
    }

    private static BlockPos strongholdLocateTarget(ChunkPos chunkPos) {
        return new BlockPos(chunkPos.getBlockX(8), 32, chunkPos.getBlockZ(8));
    }

    private static StructureStart portalRoomOnlyStrongholdStart(
            Structure structure,
            ChunkPos forcedChunk,
            int references,
            ChunkGenerator generator,
            ChunkAccess centerChunk,
            long seed,
            DimensionTiling tiling) {
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        int preferredDirection = (int) Math.floorMod(mix(seed ^ FORCED_PORTAL_ROOM_SALT), 4);
        StrongholdPieces.PortalRoom portalRoom = null;
        for (int offset = 0; offset < 4 && portalRoom == null; offset++) {
            portalRoom = fittedPortalRoom(
                    builder,
                    forcedChunk,
                    generator,
                    centerChunk,
                    Direction.from2DDataValue((preferredDirection + offset) & 3),
                    tiling
            );
        }
        if (portalRoom == null) {
            return StructureStart.INVALID_START;
        }
        return new StructureStart(structure, forcedChunk, references, new PiecesContainer(List.of(portalRoom)));
    }

    private static StrongholdPieces.PortalRoom fittedPortalRoom(
            StructurePiecesBuilder builder,
            ChunkPos forcedChunk,
            ChunkGenerator generator,
            ChunkAccess centerChunk,
            Direction direction,
            DimensionTiling tiling) {
        StrongholdPieces.PortalRoom portalRoom = StrongholdPieces.PortalRoom.createPiece(
                builder,
                forcedChunk.getBlockX(8),
                portalRoomY(generator, centerChunk),
                forcedChunk.getBlockZ(8),
                direction,
                0
        );
        if (portalRoom == null) {
            return null;
        }

        BoundingBox box = portalRoom.getBoundingBox();
        int dx = fitDelta(box.minX(), box.maxX(), tiling);
        int dz = fitDelta(box.minZ(), box.maxZ(), tiling);
        if (dx != 0 || dz != 0) {
            portalRoom.move(dx, 0, dz);
        }
        return portalRoom;
    }

    private static int fitDelta(int min, int max, DimensionTiling tiling) {
        int tileSize = tiling.tileSizeBlocks();
        int tileMin = -tileSize / 2;
        int tileMax = tileMin + tileSize - 1;
        if (max - min + 1 > tileSize) {
            return 0;
        }

        int delta = 0;
        if (min < tileMin) {
            delta = tileMin - min;
        }
        if (max + delta > tileMax) {
            delta = tileMax - max;
        }
        return delta;
    }

    private static int portalRoomY(ChunkGenerator generator, ChunkAccess centerChunk) {
        int low = centerChunk.getMinY() + 16;
        int high = Math.max(low, centerChunk.getMaxY() - 8);
        return Math.clamp(generator.getSeaLevel() - 24, low, high);
    }

    private static Holder.Reference<Structure> strongholdHolder(RegistryAccess registryAccess) {
        Registry<Structure> structures = registryAccess.lookup(Registries.STRUCTURE).orElse(null);
        if (structures == null) {
            return null;
        }
        return structures.get(BuiltinStructures.STRONGHOLD.identifier()).orElse(null);
    }

    private static boolean hasCanonicalVanillaStrongholdCandidate(
            ChunkGeneratorStructureState state,
            Holder.Reference<Structure> stronghold,
            DimensionTiling tiling) {
        for (StructurePlacement placement : state.getPlacementsForStructure(stronghold)) {
            if (!(placement instanceof ConcentricRingsStructurePlacement rings)) {
                continue;
            }

            List<ChunkPos> ringPositions = state.getRingPositionsFor(rings);
            if (ringPositions == null) {
                continue;
            }

            for (ChunkPos rawPos : ringPositions) {
                if (rawPos.equals(CoordUtil.wrapChunkPos(tiling, rawPos))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ChunkPos forcedStrongholdChunk(long seed, DimensionTiling tiling) {
        int tileSize = tiling.tileSizeChunks();
        int min = -tileSize / 2;
        int max = min + tileSize - 1;
        long mixed = mix(seed ^ FORCED_STRONGHOLD_SALT ^ tileSize);
        int side = (int) Math.floorMod(mixed, 4);
        int inset = forcedStrongholdInset(tileSize);
        int low = Math.min(max, min + inset);
        int high = Math.max(low, max - inset);
        int along = low + (int) Math.floorMod(mix(mixed), high - low + 1);
        int edge = switch (side) {
            case 0 -> low;
            case 1 -> high;
            default -> along;
        };
        int other = switch (side) {
            case 2 -> low;
            case 3 -> high;
            default -> along;
        };
        return new ChunkPos(CoordUtil.wrapChunk(tiling, edge), CoordUtil.wrapChunk(tiling, other));
    }

    private static int forcedStrongholdInset(int tileSize) {
        if (tileSize <= 8) {
            return 0;
        }
        return Math.clamp(tileSize / 8, 1, 16);
    }

    private static boolean isCanonical(DimensionTiling tiling, ChunkPos pos) {
        return CoordUtil.wrapChunk(tiling, pos.x()) == pos.x()
                && CoordUtil.wrapChunk(tiling, pos.z()) == pos.z();
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
