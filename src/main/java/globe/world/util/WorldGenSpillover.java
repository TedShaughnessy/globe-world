package globe.world.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldGenSpillover {
    private static final Map<Key, List<Write>> PENDING_WRITES = new HashMap<>();

    private WorldGenSpillover() {
    }

    public static synchronized void enqueue(ServerLevel level, BlockPos pos, BlockState state, int flags) {
        if (!DimensionTiling.forLevel(level).enabled()) {
            return;
        }

        BlockPos wrapped = CoordUtil.wrapBlockPos(level, pos).immutable();
        ChunkPos chunkPos = new ChunkPos(
                SectionPos.blockToSectionCoord(wrapped.getX()),
                SectionPos.blockToSectionCoord(wrapped.getZ())
        );
        PENDING_WRITES.computeIfAbsent(new Key(level, chunkPos.pack()), ignored -> new ArrayList<>())
                .add(new Write(wrapped, state, flags));
    }

    public static synchronized void applyToChunk(ServerLevel level, ChunkAccess chunk) {
        if (!DimensionTiling.forLevel(level).enabled()) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        if (CoordUtil.wrapChunk(level, chunkPos.x()) != chunkPos.x()
                || CoordUtil.wrapChunk(level, chunkPos.z()) != chunkPos.z()) {
            return;
        }

        List<Write> writes = PENDING_WRITES.remove(new Key(level, chunkPos.pack()));
        if (writes == null) {
            return;
        }

        for (Write write : writes) {
            chunk.setBlockState(write.pos(), write.state(), write.flags());
        }
    }

    private record Key(ServerLevel level, long chunkPos) {
    }

    private record Write(BlockPos pos, BlockState state, int flags) {
    }

}
