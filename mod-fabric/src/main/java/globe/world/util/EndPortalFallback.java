package globe.world.util;

import globe.world.GlobeWorld;
import globe.world.config.GlobeConfig;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class EndPortalFallback {
    private static final int CLEAR_RADIUS = 3;
    private static final int CLEAR_HEIGHT = 4;
    private static final int FRAME_RADIUS = 2;
    private static final int FRAME_COUNT = 12;
    private static final int PORTAL_EVENT = 1038;
    private static final int SET_BLOCK_FLAGS = 2;

    private EndPortalFallback() {
    }

    public static InteractionResult useEye(ServerLevel level, Player player, InteractionHand hand, Item eyeItem) {
        EndPortalProgressionState state = getOrCreatePortal(level, player);
        BlockPos portalPos = state.fallbackPortalPos();
        boolean changed = buildOrRepairPortal(level, portalPos, state.fallbackEyeMask());

        if (changed) {
            level.globalLevelEvent(PORTAL_EVENT, portalPos, 0);
        }
        return EnderEyeSignals.launch(level, player, hand, eyeItem, portalPos, signalTarget(level, portalPos));
    }

    public static boolean hasUsableSavedPortal(ServerLevel level) {
        EndPortalProgressionState state = EndPortalProgressionState.get(level);
        BlockPos saved = state.fallbackPortalPos();
        return saved != null
                && TopologyContexts.forLevel(level).isCanonical(saved)
                && validEyeMask(state.fallbackEyeMask());
    }

    public static EndPortalProgressionState getOrCreatePortal(ServerLevel level, Player player) {
        EndPortalProgressionState state = EndPortalProgressionState.get(level);
        EndPortalAvailability.Report report = EndPortalAvailability.classify(level);
        state.recordClassification(report, GlobeConfig.settingsVersion());

        TopologyContext topology = TopologyContexts.forLevel(level);
        BlockPos saved = state.fallbackPortalPos();
        if (saved != null && topology.isCanonical(saved) && validEyeMask(state.fallbackEyeMask())) {
            return state;
        }

        BlockPos portalPos = choosePortalPos(level, topology, player);
        state.setFallbackPortalPos(portalPos);
        state.setFallbackEyeMask(randomEyeMask(level, portalPos));
        return state;
    }

    public static boolean buildOrRepairPortal(ServerLevel level, BlockPos portalPos, int eyeMask) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        BlockPos center = topology.canonicalBlock(portalPos);
        loadPortalChunks(level, center);

        boolean changed = false;
        for (int dy = 0; dy <= CLEAR_HEIGHT; dy++) {
            for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
                for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!topology.isCanonical(pos)) {
                        GlobeWorld.LOGGER.warn("Skipping fallback End portal clear outside canonical tile at {}", pos);
                        continue;
                    }
                    if (dy == 0 && isFrame(dx, dz)) {
                        continue;
                    }
                    BlockState existingState = level.getBlockState(pos);
                    if (existingState.is(Blocks.BEDROCK) || existingState.is(Blocks.END_PORTAL)) {
                        continue;
                    }
                    changed |= setBlockIfDifferent(level, pos, Blocks.AIR.defaultBlockState());
                }
            }
        }

        for (int dx = -FRAME_RADIUS; dx <= FRAME_RADIUS; dx++) {
            for (int dz = -FRAME_RADIUS; dz <= FRAME_RADIUS; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (!topology.isCanonical(pos)) {
                    GlobeWorld.LOGGER.warn("Skipping fallback End portal write outside canonical tile at {}", pos);
                    continue;
                }

                if (isFrame(dx, dz)) {
                    changed |= setBlockIfDifferent(level, pos, frameState(level.getBlockState(pos), dx, dz, eyeMask));
                }
            }
        }
        return changed;
    }

    private static BlockPos choosePortalPos(ServerLevel level, TopologyContext topology, Player player) {
        BlockPos playerPos = topology.canonicalBlock(player.blockPosition());
        int minY = level.getMinY() + 4;
        int maxY = level.getMinY() + level.getHeight() - CLEAR_HEIGHT - 1;
        int y = Mth.clamp(playerPos.getY(), minY, Math.max(minY, maxY));
        BlockPos preferred = playerPos.atY(y);
        if (canFitPortal(topology, preferred)) {
            return preferred;
        }

        for (int radius = 1; radius <= topology.tileSizeBlocks(); radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int dz = radius - Math.abs(dx);
                BlockPos north = topology.canonicalBlock(preferred.offset(dx, 0, -dz));
                if (canFitPortal(topology, north)) {
                    return north;
                }
                if (dz != 0) {
                    BlockPos south = topology.canonicalBlock(preferred.offset(dx, 0, dz));
                    if (canFitPortal(topology, south)) {
                        return south;
                    }
                }
            }
        }
        return preferred;
    }

    private static boolean canFitPortal(TopologyContext topology, BlockPos center) {
        for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
            for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++) {
                if (!topology.isCanonical(center.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int randomEyeMask(ServerLevel level, BlockPos portalPos) {
        long seed = level.getServer().getWorldGenSettings().options().seed()
                ^ Mth.getSeed(portalPos)
                ^ 0x5f3759dfL;
        RandomSource random = RandomSource.create(seed);
        int eyeCount = random.nextInt(FRAME_COUNT);
        int mask = 0;
        while (Integer.bitCount(mask) < eyeCount) {
            mask |= 1 << random.nextInt(FRAME_COUNT);
        }
        return mask;
    }

    private static boolean validEyeMask(int eyeMask) {
        return eyeMask >= 0 && (eyeMask & ~((1 << FRAME_COUNT) - 1)) == 0;
    }

    private static Vec3 signalTarget(ServerLevel level, BlockPos portalPos) {
        BlockPos canonicalPortalPos = TopologyContexts.forLevel(level).canonicalBlock(portalPos);
        return new Vec3(
                canonicalPortalPos.getX(),
                canonicalPortalPos.getY(),
                canonicalPortalPos.getZ()
        );
    }

    private static void loadPortalChunks(ServerLevel level, BlockPos center) {
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - CLEAR_RADIUS);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + CLEAR_RADIUS);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - CLEAR_RADIUS);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + CLEAR_RADIUS);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos canonical = TopologyContexts.forLevel(level).canonicalChunk(chunkX, chunkZ);
                level.getChunk(canonical.x(), canonical.z());
            }
        }
    }

    private static boolean setBlockIfDifferent(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).equals(state)) {
            return false;
        }
        return level.setBlock(pos, state, SET_BLOCK_FLAGS);
    }

    private static boolean isFrame(int dx, int dz) {
        return Math.abs(dx) == FRAME_RADIUS && Math.abs(dz) <= 1
                || Math.abs(dz) == FRAME_RADIUS && Math.abs(dx) <= 1;
    }

    private static BlockState frameState(BlockState existingState, int dx, int dz, int eyeMask) {
        boolean existingEye = existingState.is(Blocks.END_PORTAL_FRAME)
                && existingState.getValue(EndPortalFrameBlock.HAS_EYE);
        return Blocks.END_PORTAL_FRAME.defaultBlockState()
                .setValue(EndPortalFrameBlock.HAS_EYE, existingEye || hasEye(dx, dz, eyeMask))
                .setValue(EndPortalFrameBlock.FACING, frameFacing(dx, dz));
    }

    private static boolean hasEye(int dx, int dz, int eyeMask) {
        return (eyeMask & (1 << frameIndex(dx, dz))) != 0;
    }

    private static int frameIndex(int dx, int dz) {
        if (dz == -FRAME_RADIUS) {
            return dx + 1;
        }
        if (dx == FRAME_RADIUS) {
            return 3 + dz + 1;
        }
        if (dz == FRAME_RADIUS) {
            return 6 + (1 - dx);
        }
        return 9 + (1 - dz);
    }

    private static Direction frameFacing(int dx, int dz) {
        if (dx == -FRAME_RADIUS) {
            return Direction.EAST;
        }
        if (dx == FRAME_RADIUS) {
            return Direction.WEST;
        }
        if (dz == -FRAME_RADIUS) {
            return Direction.SOUTH;
        }
        return Direction.NORTH;
    }
}
