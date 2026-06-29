package globe.world.topology;

import com.mojang.datafixers.util.Pair;
import globe.world.mixin.PoiRecordAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class TopologicalPoiQueries {
    private static final int MAX_VILLAGE_DISTANCE = 6;

    private TopologicalPoiQueries() {
    }

    public static void ensureLoadedAndValid(ServerLevel level, BlockPos center, int radius) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            level.getPoiManager().ensureLoadedAndValid(level, center, radius);
            return;
        }

        AABB visibleSquare = new AABB(
                center.getX() - radius,
                center.getY(),
                center.getZ() - radius,
                center.getX() + radius + 1.0D,
                center.getY() + 1.0D,
                center.getZ() + radius + 1.0D);
        for (AABB canonicalBox : TopologicalEntityQueries.canonicalQueryBoxes(topology, visibleSquare)) {
            BlockPos boxCenter = BlockPos.containing(canonicalBox.getCenter());
            int boxRadius = (int)Math.ceil(Math.max(canonicalBox.getXsize(), canonicalBox.getZsize()) * 0.5D);
            level.getPoiManager().ensureLoadedAndValid(level, boxCenter, boxRadius);
        }
    }

    public static Stream<PoiRecord> recordsInRange(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().getInRange(type, center, radius, occupancy);
        }

        int radiusSqr = radius * radius;
        return candidates(level, topology, type, center, radius, occupancy)
                .stream()
                .filter(candidate -> candidate.wrappedDistanceSqr() <= radiusSqr)
                .map(PoiCandidate::record);
    }

    public static Stream<PoiRecord> recordsInSquare(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().getInSquare(type, center, radius, occupancy);
        }

        return candidates(level, topology, type, center, radius, occupancy)
                .stream()
                .filter(candidate -> inWrappedSquare(topology, candidate.canonicalPos(), center, radius))
                .map(PoiCandidate::record);
    }

    public static Stream<BlockPos> positionsInRange(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        return recordsInRange(level, type, center, radius, occupancy)
                .map(PoiRecord::getPos)
                .filter(filter);
    }

    public static Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        return recordsInRange(level, type, center, radius, occupancy)
                .filter(record -> filter.test(record.getPos()))
                .map(record -> Pair.of(record.getPoiType(), record.getPos()));
    }

    public static Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().findAllClosestFirstWithType(type, filter, center, radius, occupancy);
        }

        return candidates(level, topology, type, center, radius, occupancy)
                .stream()
                .filter(candidate -> candidate.wrappedDistanceSqr() <= radius * radius)
                .filter(candidate -> filter.test(candidate.canonicalPos()))
                .sorted(Comparator.comparingDouble(PoiCandidate::wrappedDistanceSqr))
                .map(candidate -> Pair.of(candidate.record().getPoiType(), candidate.canonicalPos()));
    }

    public static Optional<BlockPos> find(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        return positionsInRange(level, type, filter, center, radius, occupancy).findFirst();
    }

    public static Optional<BlockPos> findClosest(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().findClosest(type, center, radius, occupancy);
        }

        return closestCandidate(level, topology, type, pos -> true, center, radius, occupancy)
                .map(PoiCandidate::canonicalPos);
    }

    public static Optional<BlockPos> findClosest(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().findClosest(type, filter, center, radius, occupancy);
        }

        return closestCandidate(level, topology, type, filter, center, radius, occupancy)
                .map(PoiCandidate::canonicalPos);
    }

    public static Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().findClosestWithType(type, center, radius, occupancy);
        }

        return closestCandidate(level, topology, type, pos -> true, center, radius, occupancy)
                .map(candidate -> Pair.of(candidate.record().getPoiType(), candidate.canonicalPos()));
    }

    public static Optional<BlockPos> take(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            BiPredicate<Holder<PoiType>, BlockPos> filter,
            BlockPos center,
            int radius) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().take(type, filter, center, radius);
        }

        return candidates(level, topology, type, center, radius, PoiManager.Occupancy.HAS_SPACE)
                .stream()
                .filter(candidate -> candidate.wrappedDistanceSqr() <= radius * radius)
                .filter(candidate -> filter.test(candidate.record().getPoiType(), candidate.canonicalPos()))
                .sorted(Comparator.comparingDouble(PoiCandidate::wrappedDistanceSqr))
                .filter(candidate -> ((PoiRecordAccessor)candidate.record()).globeWorld$acquireTicket())
                .map(PoiCandidate::canonicalPos)
                .findFirst();
    }

    public static Optional<BlockPos> getRandom(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            PoiManager.Occupancy occupancy,
            BlockPos center,
            int radius,
            RandomSource random) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().getRandom(type, filter, occupancy, center, radius, random);
        }

        List<PoiCandidate> shuffled = Util.toShuffledList(
                candidates(level, topology, type, center, radius, occupancy)
                        .stream()
                        .filter(candidate -> candidate.wrappedDistanceSqr() <= radius * radius),
                random);
        return shuffled.stream()
                .filter(candidate -> filter.test(candidate.canonicalPos()))
                .findFirst()
                .map(PoiCandidate::canonicalPos);
    }

    public static long countInRange(
            ServerLevel level,
            Predicate<Holder<PoiType>> type,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        return recordsInRange(level, type, center, radius, occupancy).count();
    }

    public static int sectionsToVillage(ServerLevel level, SectionPos sectionPos) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return level.getPoiManager().sectionsToVillage(sectionPos);
        }

        int tileSections = topology.tileSizeChunks();
        int offsetRadius = Math.max(1, Math.floorDiv(MAX_VILLAGE_DISTANCE, tileSections) + 1);
        ChunkPos canonicalChunk = topology.canonicalChunk(sectionPos);
        SectionPos canonicalSection = SectionPos.of(canonicalChunk.x(), sectionPos.y(), canonicalChunk.z());
        int best = level.getPoiManager().sectionsToVillage(canonicalSection);
        AABB sectionBox = new AABB(
                canonicalSection.minBlockX(),
                0.0D,
                canonicalSection.minBlockZ(),
                canonicalSection.maxBlockX() + 1.0D,
                1.0D,
                canonicalSection.maxBlockZ() + 1.0D);
        Vec3 viewer = Vec3.atCenterOf(sectionPos.center());
        for (AABB aliasBox : TileGeometry.create(topology.tiling()).nearbyAliasBoxes(sectionBox, viewer, offsetRadius)) {
            SectionPos alias = SectionPos.of(
                    SectionPos.blockToSectionCoord(aliasBox.getCenter().x()),
                    sectionPos.y(),
                    SectionPos.blockToSectionCoord(aliasBox.getCenter().z()));
            if (alias.equals(canonicalSection)) {
                continue;
            }
            best = Math.min(best, level.getPoiManager().sectionsToVillage(alias));
            if (best == 0) {
                return 0;
            }
        }
        return best;
    }

    private static Optional<PoiCandidate> closestCandidate(
            ServerLevel level,
            TopologyContext topology,
            Predicate<Holder<PoiType>> type,
            Predicate<BlockPos> filter,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        return candidates(level, topology, type, center, radius, occupancy)
                .stream()
                .filter(candidate -> candidate.wrappedDistanceSqr() <= radius * radius)
                .filter(candidate -> filter.test(candidate.canonicalPos()))
                .min(Comparator.comparingDouble(PoiCandidate::wrappedDistanceSqr));
    }

    private static List<PoiCandidate> candidates(
            ServerLevel level,
            TopologyContext topology,
            Predicate<Holder<PoiType>> type,
            BlockPos center,
            int radius,
            PoiManager.Occupancy occupancy) {
        List<PoiCandidate> candidates = new ArrayList<>();
        LongSet seenPois = new LongOpenHashSet();
        LongSet queriedChunks = new LongOpenHashSet();

        AABB visibleSquare = new AABB(
                center.getX() - radius,
                center.getY(),
                center.getZ() - radius,
                center.getX() + radius + 1.0D,
                center.getY() + 1.0D,
                center.getZ() + radius + 1.0D);
        for (AABB canonicalBox : TopologicalEntityQueries.canonicalQueryBoxes(topology, visibleSquare)) {
            int minChunkX = SectionPos.blockToSectionCoord((int)Math.floor(canonicalBox.minX));
            int minChunkZ = SectionPos.blockToSectionCoord((int)Math.floor(canonicalBox.minZ));
            int maxChunkX = SectionPos.blockToSectionCoord((int)Math.ceil(canonicalBox.maxX) - 1);
            int maxChunkZ = SectionPos.blockToSectionCoord((int)Math.ceil(canonicalBox.maxZ) - 1);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    ChunkPos canonicalChunk = topology.canonicalChunk(chunkX, chunkZ);
                    if (!queriedChunks.add(canonicalChunk.pack())) {
                        continue;
                    }
                    level.getPoiManager()
                            .getInChunk(type, canonicalChunk, occupancy)
                            .forEach(record -> addCandidate(topology, center, record, seenPois, candidates));
                }
            }
        }
        return candidates;
    }

    private static void addCandidate(
            TopologyContext topology,
            BlockPos center,
            PoiRecord record,
            LongSet seenPois,
            List<PoiCandidate> candidates) {
        BlockPos canonicalPos = topology.canonicalBlock(record.getPos());
        if (!seenPois.add(canonicalPos.asLong())) {
            return;
        }
        BlockPos visiblePos = topology.virtualBlockForViewer(canonicalPos, center.getX(), center.getZ());
        double wrappedDistanceSqr = topology.wrappedDistanceSqr(center.getCenter(), canonicalPos.getCenter());
        candidates.add(new PoiCandidate(record, canonicalPos, visiblePos, wrappedDistanceSqr));
    }

    private static boolean inWrappedSquare(TopologyContext topology, BlockPos pos, BlockPos center, int radius) {
        BlockPos visible = topology.virtualBlockForViewer(topology.canonicalBlock(pos), center.getX(), center.getZ());
        double dx = Math.abs(visible.getX() - center.getX());
        double dz = Math.abs(visible.getZ() - center.getZ());
        return dx <= radius && dz <= radius;
    }

    public record PoiCandidate(PoiRecord record, BlockPos canonicalPos, BlockPos visiblePos, double wrappedDistanceSqr) {
    }
}
