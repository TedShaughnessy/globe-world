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
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ForcedProgressionStructures {
    private static final long FORCED_STRONGHOLD_SALT = 0x2F1D_5A7C_3E91_4B68L;
    private static final long FORCED_PORTAL_ROOM_SALT = 0x73B8_1C40_D5E2_A916L;
    private static final long FORCED_NETHER_FORTRESS_SALT = 0x6C39_EA82_4B15_D7F0L;
    private static final long FORCED_TINY_FORTRESS_SALT = 0x165E_9DF3_02B4_C8A1L;
    private static final int PORTAL_ROOM_ONLY_TILE_MAX_CHUNKS = 32;
    private static final int TINY_NETHER_STRUCTURE_TILE_MAX_CHUNKS = 32;

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

    public static void maybeForceNetherProgressionStructures(
            ServerLevel level,
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            StructureManager structureManager,
            ChunkAccess centerChunk,
            ChunkGenerator generator,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> levelKey) {
        if (!Level.NETHER.equals(levelKey)
                || SharedConstants.DEBUG_DISABLE_STRUCTURES
                || !structureManager.shouldGenerateStructures()) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forLevel(level);
        if (!tiling.enabled() || !isCanonical(tiling, centerChunk.getPos())) {
            return;
        }

        maybeForceNetherFortress(
                registryAccess,
                state,
                structureManager,
                centerChunk,
                generator,
                structureTemplateManager,
                levelKey,
                tiling,
                GlobeConfig.forceMissingNetherFortress()
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

    private static void maybeForceNetherFortress(
            RegistryAccess registryAccess,
            ChunkGeneratorStructureState state,
            StructureManager structureManager,
            ChunkAccess centerChunk,
            ChunkGenerator generator,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> levelKey,
            DimensionTiling tiling,
            boolean forceMissingFortress) {
        if (!forceMissingFortress) {
            return;
        }

        Holder.Reference<Structure> fortress = structureHolder(registryAccess, BuiltinStructures.FORTRESS);
        if (fortress == null) {
            return;
        }

        if (hasCanonicalVanillaRandomSpreadCandidate(state, fortress, tiling)) {
            return;
        }

        ChunkPos forcedChunk = forcedNetherStructureChunk(state.getLevelSeed(), tiling, FORCED_NETHER_FORTRESS_SALT);
        if (!centerChunk.getPos().equals(forcedChunk)) {
            return;
        }

        SectionPos sectionPos = SectionPos.bottomOf(centerChunk);
        Structure structure = fortress.value();
        StructureStart existing = structureManager.getStartForStructure(sectionPos, structure, centerChunk);
        if (existing != null && existing.isValid()) {
            return;
        }

        int references = existing != null ? existing.getReferences() : 0;
        boolean tinyLayout = tiling.tileSizeChunks() <= TINY_NETHER_STRUCTURE_TILE_MAX_CHUNKS;
        StructureStart forcedStart = tinyLayout
                ? tinyNetherStructureStart(
                        structure,
                        forcedChunk,
                        references,
                        generator,
                        centerChunk,
                        state.getLevelSeed(),
                        tiling
                )
                : structure.generate(
                        fortress,
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
        if (!tinyLayout && forcedStart.isValid()) {
            forcedStart = withUpgradeChest(structure, forcedStart, forcedChunk, generator, centerChunk, tiling);
        }
        if (!forcedStart.isValid()) {
            GlobeWorld.LOGGER.warn(
                    "Failed to force missing Nether fortress progression at canonical chunk {} {}",
                    forcedChunk.x(),
                    forcedChunk.z()
            );
            return;
        }

        structureManager.setStartForStructure(sectionPos, structure, forcedStart, centerChunk);
        GlobeWorld.LOGGER.info(
                "Forced missing Nether fortress progression at canonical chunk {} {} using {} with upgrade chest",
                forcedChunk.x(),
                forcedChunk.z(),
                tinyLayout ? "tiny essential layout" : "vanilla layout"
        );
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

    private static StructureStart tinyNetherStructureStart(
            Structure structure,
            ChunkPos forcedChunk,
            int references,
            ChunkGenerator generator,
            ChunkAccess centerChunk,
            long seed,
            DimensionTiling tiling) {
        return tinyFortressStart(structure, forcedChunk, references, generator, centerChunk, seed, tiling);
    }

    private static StructureStart tinyFortressStart(
            Structure structure,
            ChunkPos forcedChunk,
            int references,
            ChunkGenerator generator,
            ChunkAccess centerChunk,
            long seed,
            DimensionTiling tiling) {
        int preferredDirection = (int) Math.floorMod(mix(seed ^ FORCED_TINY_FORTRESS_SALT), 4);
        for (int offset = 0; offset < 4; offset++) {
            StructurePiecesBuilder builder = new StructurePiecesBuilder();
            Direction direction = Direction.from2DDataValue((preferredDirection + offset) & 3);
            int y = fortressPieceY(generator, centerChunk);
            NetherFortressPieces.CastleStalkRoom stalkRoom = fittedFortressStalkRoom(
                    builder,
                    forcedChunk,
                    y,
                    direction,
                    tiling
            );
            if (stalkRoom == null) {
                continue;
            }
            builder.addPiece(stalkRoom);

            NetherFortressPieces.MonsterThrone throne = fittedFortressThrone(
                    builder,
                    offsetFortressChunkAnchor(forcedChunk, direction, 14),
                    y,
                    direction,
                    tiling
            );
            if (throne == null) {
                continue;
            }
            builder.addPiece(throne);

            ForcedFortressProgressionChestPiece chest = fittedUpgradeChestPiece(
                    forcedChunk,
                    y,
                    direction,
                    tiling,
                    List.of(stalkRoom, throne)
            );
            if (chest == null) {
                continue;
            }

            return new StructureStart(
                    structure,
                    forcedChunk,
                    references,
                    new PiecesContainer(List.of(stalkRoom, throne, chest))
            );
        }
        return StructureStart.INVALID_START;
    }

    private static NetherFortressPieces.CastleStalkRoom fittedFortressStalkRoom(
            StructurePiecesBuilder builder,
            ChunkPos forcedChunk,
            int y,
            Direction direction,
            DimensionTiling tiling) {
        NetherFortressPieces.CastleStalkRoom stalkRoom = NetherFortressPieces.CastleStalkRoom.createPiece(
                builder,
                forcedChunk.getBlockX(8),
                y,
                forcedChunk.getBlockZ(8),
                direction,
                0
        );
        if (stalkRoom == null) {
            return null;
        }

        if (!fitInsideTile(stalkRoom.getBoundingBox(), stalkRoom, tiling)) {
            return null;
        }
        return stalkRoom;
    }

    private static NetherFortressPieces.MonsterThrone fittedFortressThrone(
            StructurePiecesBuilder builder,
            BlockPos anchor,
            int y,
            Direction direction,
            DimensionTiling tiling) {
        NetherFortressPieces.MonsterThrone throne = NetherFortressPieces.MonsterThrone.createPiece(
                builder,
                anchor.getX(),
                y,
                anchor.getZ(),
                1,
                direction
        );
        if (throne == null) {
            return null;
        }

        if (!fitInsideTile(throne.getBoundingBox(), throne, tiling)) {
            return null;
        }
        return throne;
    }

    private static StructureStart withUpgradeChest(
            Structure structure,
            StructureStart start,
            ChunkPos forcedChunk,
            ChunkGenerator generator,
            ChunkAccess centerChunk,
            DimensionTiling tiling) {
        List<StructurePiece> pieces = new ArrayList<>(start.getPieces());
        ForcedFortressProgressionChestPiece chest = fittedUpgradeChestPiece(
                forcedChunk,
                fortressPieceY(generator, centerChunk),
                Direction.from2DDataValue((int) Math.floorMod(mix(forcedChunk.pack() ^ FORCED_TINY_FORTRESS_SALT), 4)),
                tiling,
                pieces
        );
        if (chest != null) {
            pieces.add(chest);
        } else {
            GlobeWorld.LOGGER.warn(
                    "Failed to add forced Nether fortress upgrade chest at canonical chunk {} {}",
                    forcedChunk.x(),
                    forcedChunk.z()
            );
        }
        return new StructureStart(structure, forcedChunk, start.getReferences(), new PiecesContainer(pieces));
    }

    private static ForcedFortressProgressionChestPiece fittedUpgradeChestPiece(
            ChunkPos forcedChunk,
            int y,
            Direction direction,
            DimensionTiling tiling,
            List<StructurePiece> avoidPieces) {
        for (BlockPos anchor : upgradeChestAnchors(forcedChunk, y, direction, tiling)) {
            ForcedFortressProgressionChestPiece chest = new ForcedFortressProgressionChestPiece(
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ(),
                    direction
            );
            if (!fitInsideTile(chest.getBoundingBox(), chest, tiling)
                    || intersectsAny(chest.getBoundingBox(), avoidPieces)) {
                continue;
            }
            return chest;
        }
        if (!avoidPieces.isEmpty()) {
            return fittedUpgradeChestPiece(forcedChunk, y, direction, tiling, List.of());
        }
        return null;
    }

    private static List<BlockPos> upgradeChestAnchors(
            ChunkPos forcedChunk,
            int y,
            Direction direction,
            DimensionTiling tiling) {
        List<BlockPos> anchors = new ArrayList<>();
        int centerX = forcedChunk.getBlockX(8);
        int centerZ = forcedChunk.getBlockZ(8);
        Direction right = direction.getClockWise();
        Direction left = direction.getCounterClockWise();
        Direction back = direction.getOpposite();

        int[] distances = {10, 16, 24, 32, 48, 64};
        for (int distance : distances) {
            addAnchor(anchors, centerX + right.getStepX() * distance, y, centerZ + right.getStepZ() * distance);
            addAnchor(anchors, centerX + left.getStepX() * distance, y, centerZ + left.getStepZ() * distance);
            addAnchor(anchors, centerX + back.getStepX() * distance, y, centerZ + back.getStepZ() * distance);
            addAnchor(anchors, centerX + direction.getStepX() * distance, y, centerZ + direction.getStepZ() * distance);
            addAnchor(
                    anchors,
                    centerX + right.getStepX() * distance + back.getStepX() * distance,
                    y,
                    centerZ + right.getStepZ() * distance + back.getStepZ() * distance
            );
            addAnchor(
                    anchors,
                    centerX + left.getStepX() * distance + back.getStepX() * distance,
                    y,
                    centerZ + left.getStepZ() * distance + back.getStepZ() * distance
            );
        }

        int tileSize = tiling.tileSizeBlocks();
        int tileMin = -tileSize / 2;
        int tileMax = tileMin + tileSize - 1;
        int tileMid = Math.floorDiv(tileMin + tileMax, 2);
        addAnchor(anchors, tileMin, y, tileMin);
        addAnchor(anchors, tileMax - 2, y, tileMin);
        addAnchor(anchors, tileMin, y, tileMax - 2);
        addAnchor(anchors, tileMax - 2, y, tileMax - 2);
        addAnchor(anchors, tileMid, y, tileMin);
        addAnchor(anchors, tileMid, y, tileMax - 2);
        addAnchor(anchors, tileMin, y, tileMid);
        addAnchor(anchors, tileMax - 2, y, tileMid);
        return anchors;
    }

    private static void addAnchor(List<BlockPos> anchors, int x, int y, int z) {
        BlockPos anchor = new BlockPos(x, y, z);
        if (!anchors.contains(anchor)) {
            anchors.add(anchor);
        }
    }

    private static boolean intersectsAny(BoundingBox box, List<StructurePiece> pieces) {
        for (StructurePiece piece : pieces) {
            if (box.intersects(piece.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos offsetFortressChunkAnchor(ChunkPos forcedChunk, Direction direction, int distance) {
        return new BlockPos(
                forcedChunk.getBlockX(8) + direction.getStepX() * distance,
                0,
                forcedChunk.getBlockZ(8) + direction.getStepZ() * distance
        );
    }

    private static int fortressPieceY(ChunkGenerator generator, ChunkAccess centerChunk) {
        int low = centerChunk.getMinY() + 16;
        int high = Math.max(low, centerChunk.getMaxY() - 16);
        int preferred = generator != null ? generator.getSeaLevel() : 64;
        return Math.clamp(preferred, low, high);
    }

    private static boolean fitInsideTile(BoundingBox box, StructurePiece piece, DimensionTiling tiling) {
        int dx = fitDelta(box.minX(), box.maxX(), tiling);
        int dz = fitDelta(box.minZ(), box.maxZ(), tiling);
        if (dx == 0 && dz == 0) {
            return isInsideTile(box, tiling);
        }
        piece.move(dx, 0, dz);
        return isInsideTile(piece.getBoundingBox(), tiling);
    }

    private static boolean isInsideTile(BoundingBox box, DimensionTiling tiling) {
        int tileSize = tiling.tileSizeBlocks();
        int tileMin = -tileSize / 2;
        int tileMax = tileMin + tileSize - 1;
        return box.minX() >= tileMin && box.maxX() <= tileMax
                && box.minZ() >= tileMin && box.maxZ() <= tileMax;
    }

    private static Holder.Reference<Structure> strongholdHolder(RegistryAccess registryAccess) {
        return structureHolder(registryAccess, BuiltinStructures.STRONGHOLD);
    }

    private static Holder.Reference<Structure> structureHolder(RegistryAccess registryAccess, ResourceKey<Structure> key) {
        Registry<Structure> structures = registryAccess.lookup(Registries.STRUCTURE).orElse(null);
        if (structures == null) {
            return null;
        }
        return structures.get(key.identifier()).orElse(null);
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

    private static boolean hasCanonicalVanillaRandomSpreadCandidate(
            ChunkGeneratorStructureState state,
            Holder.Reference<Structure> structure,
            DimensionTiling tiling) {
        int tileSize = tiling.tileSizeChunks();
        int min = -tileSize / 2;
        int max = min + tileSize - 1;
        for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
            if (!(placement instanceof RandomSpreadStructurePlacement randomSpread)) {
                continue;
            }

            int spacing = randomSpread.spacing();
            int cellMinX = Math.floorDiv(min, spacing);
            int cellMaxX = Math.floorDiv(max, spacing);
            int cellMinZ = Math.floorDiv(min, spacing);
            int cellMaxZ = Math.floorDiv(max, spacing);
            for (int cellX = cellMinX; cellX <= cellMaxX; cellX++) {
                for (int cellZ = cellMinZ; cellZ <= cellMaxZ; cellZ++) {
                    ChunkPos rawPos = randomSpread.getPotentialStructureChunk(
                            state.getLevelSeed(),
                            cellX * spacing,
                            cellZ * spacing
                    );
                    if (rawPos.x() < min || rawPos.x() > max || rawPos.z() < min || rawPos.z() > max) {
                        continue;
                    }
                    if (rawPos.equals(CoordUtil.wrapChunkPos(tiling, rawPos))
                            && placement.isStructureChunk(state, rawPos.x(), rawPos.z())) {
                        return true;
                    }
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

    private static ChunkPos forcedNetherStructureChunk(long seed, DimensionTiling tiling, long salt) {
        int tileSize = tiling.tileSizeChunks();
        if (tileSize <= TINY_NETHER_STRUCTURE_TILE_MAX_CHUNKS) {
            return forcedInteriorChunk(seed, tiling, salt);
        }
        return forcedEdgeChunk(seed, tiling, salt, Math.clamp(tileSize / 8, 1, 16));
    }

    private static ChunkPos forcedInteriorChunk(long seed, DimensionTiling tiling, long salt) {
        int tileSize = tiling.tileSizeChunks();
        int min = -tileSize / 2;
        int max = min + tileSize - 1;
        int inset = Math.min(Math.max(0, tileSize / 4), Math.max(0, (tileSize - 1) / 2));
        int low = Math.min(max, min + inset);
        int high = Math.max(low, max - inset);
        long mixed = mix(seed ^ salt ^ tileSize);
        int x = low + (int) Math.floorMod(mixed, high - low + 1);
        int z = low + (int) Math.floorMod(mix(mixed), high - low + 1);
        return new ChunkPos(CoordUtil.wrapChunk(tiling, x), CoordUtil.wrapChunk(tiling, z));
    }

    private static ChunkPos forcedEdgeChunk(long seed, DimensionTiling tiling, long salt, int inset) {
        int tileSize = tiling.tileSizeChunks();
        int min = -tileSize / 2;
        int max = min + tileSize - 1;
        long mixed = mix(seed ^ salt ^ tileSize);
        int side = (int) Math.floorMod(mixed, 4);
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
