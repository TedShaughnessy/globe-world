package globe.world.util;

import globe.world.diagnostics.DiagnosticsChannel;
import globe.world.diagnostics.GlobeDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public final class GenerationWindow {
    private final ServerLevel level;
    private final ChunkAccess center;
    private final StaticCache2D<GenerationChunkHolder> cache;
    private final ChunkStep generatingStep;
    private final DimensionTiling tiling;

    private GenerationWindow(
            ServerLevel level,
            ChunkAccess center,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkStep generatingStep) {
        this.level = level;
        this.center = center;
        this.cache = cache;
        this.generatingStep = generatingStep;
        this.tiling = DimensionTiling.forLevel(level);
    }

    public static GenerationWindow forRegion(
            ServerLevel level,
            ChunkAccess center,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkStep generatingStep) {
        return new GenerationWindow(level, center, cache, generatingStep);
    }

    public BlockPos canonicalReadPos(BlockPos rawPos) {
        return CoordUtil.wrapBlockPos(this.level, rawPos);
    }

    public ChunkLookup resolveChunk(int rawChunkX, int rawChunkZ, ChunkStatus status, boolean loadOrGenerate) {
        int virtualChunkX = virtualCacheChunkX(rawChunkX);
        int virtualChunkZ = virtualCacheChunkZ(rawChunkZ);
        if (virtualChunkX == rawChunkX && virtualChunkZ == rawChunkZ) {
            return new ChunkLookup(ChunkLookup.Kind.VANILLA, rawChunkX, rawChunkZ, virtualChunkX, virtualChunkZ);
        }
        if (this.cache.contains(virtualChunkX, virtualChunkZ)) {
            return new ChunkLookup(ChunkLookup.Kind.CACHE_ALIAS, rawChunkX, rawChunkZ, virtualChunkX, virtualChunkZ);
        }
        if (!loadOrGenerate) {
            return new ChunkLookup(ChunkLookup.Kind.UNAVAILABLE_NO_LOAD, rawChunkX, rawChunkZ, virtualChunkX, virtualChunkZ);
        }
        return new ChunkLookup(ChunkLookup.Kind.VANILLA, rawChunkX, rawChunkZ, virtualChunkX, virtualChunkZ);
    }

    public boolean hasChunk(int rawChunkX, int rawChunkZ) {
        int virtualChunkX = virtualCacheChunkX(rawChunkX);
        int virtualChunkZ = virtualCacheChunkZ(rawChunkZ);
        if (virtualChunkX == rawChunkX && virtualChunkZ == rawChunkZ) {
            return vanillaHasChunk(rawChunkX, rawChunkZ);
        }
        return this.cache.contains(virtualChunkX, virtualChunkZ) && vanillaHasChunk(virtualChunkX, virtualChunkZ);
    }

    public WriteDecision classifyWrite(BlockPos rawPos) {
        BlockPos canonicalPos = CoordUtil.wrapBlockPos(this.level, rawPos);
        ChunkPos canonicalChunk = chunkPos(canonicalPos);
        if (!withinWriteRadius(canonicalPos)) {
            return new WriteDecision(WriteDecision.Kind.DENIED_BY_RADIUS, rawPos, canonicalPos, canonicalChunk);
        }
        if (canonicalPos == rawPos) {
            return new WriteDecision(WriteDecision.Kind.CANONICAL, rawPos, canonicalPos, canonicalChunk);
        }
        if (physicalCacheContains(canonicalPos)) {
            return new WriteDecision(WriteDecision.Kind.WRAPPED_VISIBLE, rawPos, canonicalPos, canonicalChunk);
        }
        return new WriteDecision(WriteDecision.Kind.WRAPPED_UNOBSERVED, rawPos, canonicalPos, canonicalChunk);
    }

    public boolean canWriteCanonical(BlockPos canonicalPos) {
        if (!withinWriteRadius(canonicalPos)) {
            return false;
        }
        if (!this.center.isUpgrading()) {
            return true;
        }

        LevelHeightAccessor heightAccessor = this.center.getHeightAccessorForGeneration();
        return !heightAccessor.isOutsideBuildHeight(canonicalPos.getY());
    }

    public boolean withinWriteRadius(BlockPos canonicalPos) {
        int chunkX = SectionPos.blockToSectionCoord(canonicalPos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(canonicalPos.getZ());
        ChunkPos centerPos = this.center.getPos();
        int distanceX = canonicalChunkDistance(centerPos.x(), chunkX);
        int distanceZ = canonicalChunkDistance(centerPos.z(), chunkZ);
        int radius = this.generatingStep.blockStateWriteRadius();
        return distanceX <= radius && distanceZ <= radius;
    }

    public boolean physicalCacheContains(BlockPos canonicalPos) {
        ChunkPos chunkPos = chunkPos(canonicalPos);
        return this.cache.contains(virtualCacheChunkX(chunkPos.x()), virtualCacheChunkZ(chunkPos.z()));
    }

    public int canonicalChunkDistance(int a, int b) {
        return CoordUtil.wrappedChunkDistance(this.tiling, a, b);
    }

    public void logChunkLookup(ChunkLookup lookup, ChunkStatus status, boolean loadOrGenerate) {
        if (lookup.kind() == ChunkLookup.Kind.VANILLA) {
            return;
        }

        GlobeDiagnostics.debug(
                DiagnosticsChannel.WORLDGEN,
                "GW_WORLDGEN_WINDOW chunk_lookup dimension={} center={} raw={} virtual={} kind={} status={} loadOrGenerate={}",
                dimensionName(this.level.dimension()),
                format(this.center.getPos()),
                format(lookup.rawChunkX(), lookup.rawChunkZ()),
                format(lookup.virtualChunkX(), lookup.virtualChunkZ()),
                lookup.kind(),
                status.getName(),
                loadOrGenerate
        );
    }

    public void logWriteDecision(WriteDecision decision) {
        if (decision.kind() == WriteDecision.Kind.CANONICAL) {
            return;
        }

        GlobeDiagnostics.debug(
                DiagnosticsChannel.WORLDGEN,
                "GW_WORLDGEN_WINDOW write dimension={} center={} raw={} canonical={} canonicalChunk={} kind={} radius={}",
                dimensionName(this.level.dimension()),
                format(this.center.getPos()),
                format(decision.rawPos()),
                format(decision.canonicalPos()),
                format(decision.canonicalChunk()),
                decision.kind(),
                this.generatingStep.blockStateWriteRadius()
        );
    }

    private boolean vanillaHasChunk(int chunkX, int chunkZ) {
        int distance = this.center.getPos().getChessboardDistance(chunkX, chunkZ);
        return distance < this.generatingStep.directDependencies().size();
    }

    private int virtualCacheChunkX(int chunkX) {
        return CoordUtil.virtualChunk(this.level, CoordUtil.wrapChunk(this.level, chunkX), this.center.getPos().x());
    }

    private int virtualCacheChunkZ(int chunkZ) {
        return CoordUtil.virtualChunk(this.level, CoordUtil.wrapChunk(this.level, chunkZ), this.center.getPos().z());
    }

    private static ChunkPos chunkPos(BlockPos pos) {
        return new ChunkPos(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static String format(ChunkPos pos) {
        return format(pos.x(), pos.z());
    }

    private static String format(int chunkX, int chunkZ) {
        return chunkX + "," + chunkZ;
    }

    private static String format(BlockPos pos) {
        return pos.toShortString();
    }

    private static String dimensionName(ResourceKey<Level> dimension) {
        return dimension.identifier().toString();
    }

    public record ChunkLookup(Kind kind, int rawChunkX, int rawChunkZ, int virtualChunkX, int virtualChunkZ) {
        public enum Kind {
            VANILLA,
            CACHE_ALIAS,
            UNAVAILABLE_NO_LOAD
        }
    }

    public record WriteDecision(Kind kind, BlockPos rawPos, BlockPos canonicalPos, ChunkPos canonicalChunk) {
        public enum Kind {
            CANONICAL,
            WRAPPED_VISIBLE,
            WRAPPED_UNOBSERVED,
            DENIED_BY_RADIUS
        }
    }
}
