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

        ChunkPos forcedChunk = forcedStrongholdChunk(tiling);
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
        return Optional.of(forcedStrongholdChunk(tiling));
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

        Optional<ChunkPos> vanillaCandidate = nearestCanonicalVanillaRandomSpreadCandidate(state, fortress, tiling);
        if (vanillaCandidate.isPresent()) {
            maybeEnsureProgressionInVanillaFortress(
                    structureManager,
                    centerChunk,
                    fortress.value(),
                    vanillaCandidate.get(),
                    tiling
            );
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
            forcedStart = withNetherFortressProgression(structure, forcedStart, forcedChunk, tiling)
                    .orElse(StructureStart.INVALID_START);
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

    private static void maybeEnsureProgressionInVanillaFortress(
            StructureManager structureManager,
            ChunkAccess centerChunk,
            Structure structure,
            ChunkPos selectedChunk,
            DimensionTiling tiling) {
        if (!centerChunk.getPos().equals(selectedChunk)) {
            return;
        }

        SectionPos sectionPos = SectionPos.bottomOf(centerChunk);
        StructureStart existing = structureManager.getStartForStructure(sectionPos, structure, centerChunk);
        if (existing == null || !existing.isValid() || hasNetherFortressProgression(existing)) {
            return;
        }

        Optional<StructureStart> upgraded = withNetherFortressProgression(structure, existing, selectedChunk, tiling);
        if (upgraded.isEmpty()) {
            return;
        }

        structureManager.setStartForStructure(sectionPos, structure, upgraded.get(), centerChunk);
        GlobeWorld.LOGGER.info(
                "Added missing Nether fortress progression affordances to nearest canonical fortress at chunk {} {}",
                selectedChunk.x(),
                selectedChunk.z()
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
            Direction direction = Direction.from2DDataValue((preferredDirection + offset) & 3);
            for (int throneYOffset : new int[]{11, 3}) {
                StructureStart start = tinyFortressStart(
                        structure,
                        forcedChunk,
                        references,
                        generator,
                        centerChunk,
                        tiling,
                        direction,
                        throneYOffset
                );
                if (start.isValid()) {
                    return start;
                }
            }
        }
        return StructureStart.INVALID_START;
    }

    private static StructureStart tinyFortressStart(
            Structure structure,
            ChunkPos forcedChunk,
            int references,
            ChunkGenerator generator,
            ChunkAccess centerChunk,
            DimensionTiling tiling,
            Direction direction,
            int throneYOffset) {
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        int y = fortressPieceY(generator, centerChunk);
        NetherFortressPieces.CastleStalkRoom stalkRoom = fortressStalkRoom(
                builder,
                forcedChunk,
                y,
                direction
        );
        if (stalkRoom == null) {
            return StructureStart.INVALID_START;
        }
        builder.addPiece(stalkRoom);

        NetherFortressPieces.MonsterThrone throne = fortressThrone(
                builder,
                fortressForwardAnchor(stalkRoom.getBoundingBox(), direction, 5, throneYOffset),
                direction
        );
        if (throne == null) {
            return StructureStart.INVALID_START;
        }
        builder.addPiece(throne);

        List<StructurePiece> pieces = new ArrayList<>();
        pieces.add(stalkRoom);
        pieces.add(throne);
        if (!fitGroupInsideTile(pieces, tiling) || stalkRoom.getBoundingBox().intersects(throne.getBoundingBox())) {
            return StructureStart.INVALID_START;
        }

        pieces.addAll(fortressWartPatches(stalkRoom));
        ForcedFortressProgressionChestPiece chest = stalkRoomUpgradeChestPiece(stalkRoom, tiling);
        if (chest == null) {
            return StructureStart.INVALID_START;
        }
        pieces.add(chest);

        return new StructureStart(
                structure,
                forcedChunk,
                references,
                new PiecesContainer(pieces)
        );
    }

    private static NetherFortressPieces.CastleStalkRoom fortressStalkRoom(
            StructurePiecesBuilder builder,
            ChunkPos forcedChunk,
            int y,
            Direction direction) {
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

        return stalkRoom;
    }

    private static NetherFortressPieces.MonsterThrone fortressThrone(
            StructurePiecesBuilder builder,
            BlockPos anchor,
            Direction direction) {
        NetherFortressPieces.MonsterThrone throne = NetherFortressPieces.MonsterThrone.createPiece(
                builder,
                anchor.getX(),
                anchor.getY(),
                anchor.getZ(),
                1,
                direction
        );
        if (throne == null) {
            return null;
        }

        return throne;
    }

    private static Optional<StructureStart> withNetherFortressProgression(
            Structure structure,
            StructureStart start,
            ChunkPos logChunk,
            DimensionTiling tiling) {
        if (hasNetherFortressProgression(start)) {
            return Optional.of(start);
        }

        List<StructurePiece> pieces = new ArrayList<>(start.getPieces());
        NetherFortressPieces.CastleStalkRoom chestRoom = firstStalkRoom(start);
        if (!hasStalkRoom(start) || !hasMonsterThrone(start)) {
            FortressSupplement supplement = supplementalFortressProgressionPieces(start, logChunk, tiling, true);
            if (supplement == null) {
                supplement = supplementalFortressProgressionPieces(start, logChunk, tiling, false);
            }
            if (supplement == null) {
                GlobeWorld.LOGGER.warn(
                        "Failed to add missing Nether fortress blaze/wart pieces at canonical chunk {} {}",
                        logChunk.x(),
                        logChunk.z()
                );
                return Optional.empty();
            }
            pieces.addAll(supplement.pieces());
            if (chestRoom == null) {
                chestRoom = supplement.stalkRoom();
            }
        }

        ForcedFortressProgressionChestPiece chest = null;
        if (!hasUpgradeChest(start)) {
            if (chestRoom != null) {
                chest = stalkRoomUpgradeChestPiece(chestRoom, tiling);
            }
            if (chest == null) {
                chest = fittedUpgradeChestPiece(start, logChunk, tiling);
            }
        }
        if (!hasUpgradeChest(start) && chest == null) {
            GlobeWorld.LOGGER.warn(
                    "Failed to add Nether fortress progression upgrade chest at canonical chunk {} {}",
                    logChunk.x(),
                    logChunk.z()
            );
            return Optional.empty();
        }

        if (!hasUpgradeChest(start)) {
            pieces.add(chest);
        }
        return Optional.of(new StructureStart(structure, start.getChunkPos(), start.getReferences(), new PiecesContainer(pieces)));
    }

    private static FortressSupplement supplementalFortressProgressionPieces(
            StructureStart start,
            ChunkPos logChunk,
            DimensionTiling tiling,
            boolean avoidExistingPieces) {
        int preferredDirection = (int) Math.floorMod(mix(logChunk.pack() ^ FORCED_TINY_FORTRESS_SALT), 4);
        for (BlockPos anchor : fortressSupplementAnchors(start, logChunk)) {
            for (int offset = 0; offset < 4; offset++) {
                Direction direction = Direction.from2DDataValue((preferredDirection + offset) & 3);
                for (int throneYOffset : new int[]{11, 3}) {
                    FortressSupplement supplement = supplementalFortressProgressionPieces(
                            start,
                            anchor,
                            direction,
                            throneYOffset,
                            tiling,
                            avoidExistingPieces
                    );
                    if (supplement != null) {
                        return supplement;
                    }
                }
            }
        }
        return null;
    }

    private static FortressSupplement supplementalFortressProgressionPieces(
            StructureStart start,
            BlockPos anchor,
            Direction direction,
            int throneYOffset,
            DimensionTiling tiling,
            boolean avoidExistingPieces) {
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        if (avoidExistingPieces) {
            for (StructurePiece piece : start.getPieces()) {
                builder.addPiece(piece);
            }
        }

        NetherFortressPieces.CastleStalkRoom stalkRoom = NetherFortressPieces.CastleStalkRoom.createPiece(
                builder,
                anchor.getX(),
                anchor.getY(),
                anchor.getZ(),
                direction,
                0
        );
        if (stalkRoom == null) {
            return null;
        }
        builder.addPiece(stalkRoom);

        NetherFortressPieces.MonsterThrone throne = fortressThrone(
                builder,
                fortressForwardAnchor(stalkRoom.getBoundingBox(), direction, 5, throneYOffset),
                direction
        );
        if (throne == null) {
            return null;
        }

        List<StructurePiece> pieces = new ArrayList<>();
        pieces.add(stalkRoom);
        pieces.add(throne);
        if (!fitGroupInsideTile(pieces, tiling) || stalkRoom.getBoundingBox().intersects(throne.getBoundingBox())) {
            return null;
        }
        if (avoidExistingPieces && intersectsAnyPiece(pieces, start.getPieces())) {
            return null;
        }
        pieces.addAll(fortressWartPatches(stalkRoom));
        return new FortressSupplement(pieces, stalkRoom);
    }

    private static List<BlockPos> fortressSupplementAnchors(StructureStart start, ChunkPos logChunk) {
        List<BlockPos> anchors = new ArrayList<>();
        int y = start.getBoundingBox().minY() + 3;
        addAnchor(anchors, logChunk.getBlockX(8), y, logChunk.getBlockZ(8));

        Vec3i center = start.getBoundingBox().getCenter();
        addAnchor(anchors, center.getX(), y, center.getZ());

        int centerX = logChunk.getBlockX(8);
        int centerZ = logChunk.getBlockZ(8);
        for (int distance : new int[]{16, 32, 48, 64}) {
            addAnchor(anchors, centerX + distance, y, centerZ);
            addAnchor(anchors, centerX - distance, y, centerZ);
            addAnchor(anchors, centerX, y, centerZ + distance);
            addAnchor(anchors, centerX, y, centerZ - distance);
            addAnchor(anchors, centerX + distance, y, centerZ + distance);
            addAnchor(anchors, centerX - distance, y, centerZ - distance);
            addAnchor(anchors, centerX + distance, y, centerZ - distance);
            addAnchor(anchors, centerX - distance, y, centerZ + distance);
        }
        return anchors;
    }

    private static ForcedFortressProgressionChestPiece fittedUpgradeChestPiece(
            StructureStart start,
            ChunkPos logChunk,
            DimensionTiling tiling) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof NetherFortressPieces.CastleStalkRoom stalkRoom) {
                ForcedFortressProgressionChestPiece chest = stalkRoomUpgradeChestPiece(stalkRoom, tiling);
                if (chest != null) {
                    return chest;
                }
            }
        }

        ForcedFortressProgressionChestPiece fallback = fallbackUpgradeChestPiece(start, logChunk, tiling);
        if (fallback != null) {
            GlobeWorld.LOGGER.warn(
                    "Placed Nether fortress upgrade chest at fallback start location for chunk {} {} because no CastleStalkRoom was available",
                    logChunk.x(),
                    logChunk.z()
            );
        }
        return fallback;
    }

    private static ForcedFortressProgressionChestPiece stalkRoomUpgradeChestPiece(
            NetherFortressPieces.CastleStalkRoom stalkRoom,
            DimensionTiling tiling) {
        BoundingBox box = stalkRoom.getBoundingBox();
        Direction direction = stalkRoom.getOrientation() != null ? stalkRoom.getOrientation() : Direction.SOUTH;
        for (BlockPos anchor : stalkRoomChestAnchors(box, direction)) {
            ForcedFortressProgressionChestPiece chest = new ForcedFortressProgressionChestPiece(
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ(),
                    direction
            );
            if (isInsideTile(chest.getBoundingBox(), tiling) && contains(box, chest.getBoundingBox())) {
                return chest;
            }
        }
        return null;
    }

    private static List<BlockPos> stalkRoomChestAnchors(BoundingBox box, Direction direction) {
        List<BlockPos> anchors = new ArrayList<>();
        addStructureAnchor(anchors, box, direction, 5, 4, 8);
        addStructureAnchor(anchors, box, direction, 5, 4, 9);
        addStructureAnchor(anchors, box, direction, 5, 4, 10);
        return anchors;
    }

    private static void addStructureAnchor(
            List<BlockPos> anchors,
            BoundingBox box,
            Direction direction,
            int localX,
            int localY,
            int localZ) {
        addAnchor(
                anchors,
                structureWorldX(box, direction, localX, localZ),
                box.minY() + localY,
                structureWorldZ(box, direction, localX, localZ)
        );
    }

    private static ForcedFortressProgressionChestPiece fallbackUpgradeChestPiece(
            StructureStart start,
            ChunkPos logChunk,
            DimensionTiling tiling) {
        Direction direction = Direction.SOUTH;
        int y = start.getBoundingBox().minY();
        if (!start.getPieces().isEmpty()) {
            StructurePiece piece = start.getPieces().get(0);
            direction = piece.getOrientation() != null ? piece.getOrientation() : direction;
            y = piece.getBoundingBox().minY();
        }

        ForcedFortressProgressionChestPiece chest = new ForcedFortressProgressionChestPiece(
                logChunk.getBlockX(8),
                y,
                logChunk.getBlockZ(8),
                direction
        );
        if (isInsideTile(chest.getBoundingBox(), tiling)) {
            return chest;
        }
        return fitInsideTile(chest.getBoundingBox(), chest, tiling) ? chest : null;
    }

    private static void addAnchor(List<BlockPos> anchors, int x, int y, int z) {
        BlockPos anchor = new BlockPos(x, y, z);
        if (!anchors.contains(anchor)) {
            anchors.add(anchor);
        }
    }

    private static boolean hasUpgradeChest(StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof ForcedFortressProgressionChestPiece) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNetherFortressProgression(StructureStart start) {
        return hasStalkRoom(start) && hasMonsterThrone(start) && hasUpgradeChest(start);
    }

    private static boolean hasStalkRoom(StructureStart start) {
        return firstStalkRoom(start) != null;
    }

    private static NetherFortressPieces.CastleStalkRoom firstStalkRoom(StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof NetherFortressPieces.CastleStalkRoom stalkRoom) {
                return stalkRoom;
            }
        }
        return null;
    }

    private static boolean hasMonsterThrone(StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof NetherFortressPieces.MonsterThrone) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectsAnyPiece(List<StructurePiece> pieces, List<StructurePiece> existingPieces) {
        for (StructurePiece piece : pieces) {
            for (StructurePiece existing : existingPieces) {
                if (piece.getBoundingBox().intersects(existing.getBoundingBox())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int fortressPieceY(ChunkGenerator generator, ChunkAccess centerChunk) {
        int low = centerChunk.getMinY() + 16;
        int high = Math.max(low, centerChunk.getMaxY() - 16);
        int preferred = generator != null ? generator.getSeaLevel() : 64;
        return Math.clamp(preferred, low, high);
    }

    private static BlockPos fortressForwardAnchor(BoundingBox box, Direction direction, int xOff, int yOff) {
        return switch (direction) {
            case NORTH -> new BlockPos(box.minX() + xOff, box.minY() + yOff, box.minZ() - 1);
            case SOUTH -> new BlockPos(box.minX() + xOff, box.minY() + yOff, box.maxZ() + 1);
            case WEST -> new BlockPos(box.minX() - 1, box.minY() + yOff, box.minZ() + xOff);
            case EAST -> new BlockPos(box.maxX() + 1, box.minY() + yOff, box.minZ() + xOff);
            default -> new BlockPos(box.minX() + xOff, box.minY() + yOff, box.maxZ() + 1);
        };
    }

    private static List<ForcedFortressWartPatchPiece> fortressWartPatches(
            NetherFortressPieces.CastleStalkRoom stalkRoom) {
        return List.of(
                new ForcedFortressWartPatchPiece(wartPatchBox(stalkRoom, 3, 4, 4, 8, 4)),
                new ForcedFortressWartPatchPiece(wartPatchBox(stalkRoom, 8, 4, 9, 8, 4))
        );
    }

    private static BoundingBox wartPatchBox(
            StructurePiece piece,
            int minLocalX,
            int minLocalZ,
            int maxLocalX,
            int maxLocalZ,
            int soulSandLocalY) {
        BoundingBox box = piece.getBoundingBox();
        Direction direction = piece.getOrientation();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int localX = minLocalX; localX <= maxLocalX; localX++) {
            for (int localZ = minLocalZ; localZ <= maxLocalZ; localZ++) {
                int x = structureWorldX(box, direction, localX, localZ);
                int z = structureWorldZ(box, direction, localX, localZ);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        int y = box.minY() + soulSandLocalY;
        return new BoundingBox(minX, y, minZ, maxX, y + 1, maxZ);
    }

    private static int structureWorldX(BoundingBox box, Direction direction, int x, int z) {
        return switch (direction) {
            case WEST -> box.maxX() - z;
            case EAST -> box.minX() + z;
            default -> box.minX() + x;
        };
    }

    private static int structureWorldZ(BoundingBox box, Direction direction, int x, int z) {
        return switch (direction) {
            case NORTH -> box.maxZ() - z;
            case WEST, EAST -> box.minZ() + x;
            default -> box.minZ() + z;
        };
    }

    private static boolean fitGroupInsideTile(List<StructurePiece> pieces, DimensionTiling tiling) {
        BoundingBox box = StructurePiece.createBoundingBox(pieces.stream());
        int dx = fitDelta(box.minX(), box.maxX(), tiling);
        int dz = fitDelta(box.minZ(), box.maxZ(), tiling);
        if (dx != 0 || dz != 0) {
            for (StructurePiece piece : pieces) {
                piece.move(dx, 0, dz);
            }
            box = box.moved(dx, 0, dz);
        }
        return isInsideTile(box, tiling);
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

    private static boolean contains(BoundingBox outer, BoundingBox inner) {
        return inner.minX() >= outer.minX() && inner.maxX() <= outer.maxX()
                && inner.minY() >= outer.minY() && inner.maxY() <= outer.maxY()
                && inner.minZ() >= outer.minZ() && inner.maxZ() <= outer.maxZ();
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

    private static Optional<ChunkPos> nearestCanonicalVanillaRandomSpreadCandidate(
            ChunkGeneratorStructureState state,
            Holder.Reference<Structure> structure,
            DimensionTiling tiling) {
        int tileSize = tiling.tileSizeChunks();
        int min = -tileSize / 2;
        int max = min + tileSize - 1;
        ChunkPos nearest = null;
        long nearestDistance = Long.MAX_VALUE;
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
                        long distance = (long) rawPos.x() * rawPos.x() + (long) rawPos.z() * rawPos.z();
                        if (nearest == null || distance < nearestDistance) {
                            nearest = rawPos;
                            nearestDistance = distance;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static ChunkPos forcedStrongholdChunk(DimensionTiling tiling) {
        return new ChunkPos(CoordUtil.wrapChunk(tiling, 0), CoordUtil.wrapChunk(tiling, 0));
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

    private record FortressSupplement(List<StructurePiece> pieces, NetherFortressPieces.CastleStalkRoom stalkRoom) {
    }
}
