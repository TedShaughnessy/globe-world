package globe.world.util;

import globe.world.GlobeWorld;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CanonicalChunkTickets {
    private static final int NO_TICKET_RADIUS = -1;

    private static final ConcurrentHashMap<AliasKey, Integer> ALIAS_RADII = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<CanonicalTicketKey, AtomicInteger> CANONICAL_REFS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<AliasKey, Integer> ALIAS_SIMULATION_RADII = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<CanonicalTicketKey, AtomicInteger> CANONICAL_SIMULATION_REFS = new ConcurrentHashMap<>();

    private CanonicalChunkTickets() {
    }

    public static void updateAliasStatus(ServerLevel level, ChunkPos aliasPos, FullChunkStatus status) {
        updateAliasRadius(level, aliasPos, status == FullChunkStatus.INACCESSIBLE ? NO_TICKET_RADIUS : 0);
        updateAliasSimulationRadius(level, aliasPos, simulationRadius(status));
    }

    public static void clearLevel(ServerLevel level) {
        for (AliasKey aliasKey : ALIAS_RADII.keySet()) {
            if (aliasKey.level() == level) {
                ALIAS_RADII.remove(aliasKey);
            }
        }
        for (AliasKey aliasKey : ALIAS_SIMULATION_RADII.keySet()) {
            if (aliasKey.level() == level) {
                ALIAS_SIMULATION_RADII.remove(aliasKey);
            }
        }

        for (Map.Entry<CanonicalTicketKey, AtomicInteger> entry : CANONICAL_REFS.entrySet()) {
            CanonicalTicketKey key = entry.getKey();
            if (key.level() != level) {
                continue;
            }

            AtomicInteger ref = CANONICAL_REFS.remove(key);
            if (ref == null) {
                continue;
            }

            ChunkPos canonicalPos = ChunkPos.unpack(key.canonicalChunk());
            level.getChunkSource().removeTicketWithRadius(GlobeWorld.CANONICAL_ALIAS_TICKET, canonicalPos, key.radius());
        }
        for (Map.Entry<CanonicalTicketKey, AtomicInteger> entry : CANONICAL_SIMULATION_REFS.entrySet()) {
            CanonicalTicketKey key = entry.getKey();
            if (key.level() != level) {
                continue;
            }

            AtomicInteger ref = CANONICAL_SIMULATION_REFS.remove(key);
            if (ref == null) {
                continue;
            }

            ChunkPos canonicalPos = ChunkPos.unpack(key.canonicalChunk());
            level.getChunkSource().removeTicketWithRadius(GlobeWorld.CANONICAL_ALIAS_SIMULATION_TICKET, canonicalPos, key.radius());
        }
    }

    private static void updateAliasRadius(ServerLevel level, ChunkPos aliasPos, int newRadius) {
        int wcx = CoordUtil.wrapChunk(level, aliasPos.x());
        int wcz = CoordUtil.wrapChunk(level, aliasPos.z());
        if (wcx == aliasPos.x() && wcz == aliasPos.z()) {
            return;
        }

        AliasKey aliasKey = new AliasKey(level, aliasPos.pack());
        Integer oldRadius = newRadius == NO_TICKET_RADIUS
                ? ALIAS_RADII.remove(aliasKey)
                : ALIAS_RADII.put(aliasKey, newRadius);
        int previousRadius = oldRadius == null ? NO_TICKET_RADIUS : oldRadius;
        if (previousRadius == newRadius) {
            return;
        }

        ChunkPos canonicalPos = new ChunkPos(wcx, wcz);
        acquire(level, canonicalPos, newRadius, GlobeWorld.CANONICAL_ALIAS_TICKET, CANONICAL_REFS);
        release(level, canonicalPos, previousRadius, GlobeWorld.CANONICAL_ALIAS_TICKET, CANONICAL_REFS);
    }

    private static void updateAliasSimulationRadius(ServerLevel level, ChunkPos aliasPos, int newRadius) {
        int wcx = CoordUtil.wrapChunk(level, aliasPos.x());
        int wcz = CoordUtil.wrapChunk(level, aliasPos.z());
        if (wcx == aliasPos.x() && wcz == aliasPos.z()) {
            return;
        }

        AliasKey aliasKey = new AliasKey(level, aliasPos.pack());
        Integer oldRadius = newRadius == NO_TICKET_RADIUS
                ? ALIAS_SIMULATION_RADII.remove(aliasKey)
                : ALIAS_SIMULATION_RADII.put(aliasKey, newRadius);
        int previousRadius = oldRadius == null ? NO_TICKET_RADIUS : oldRadius;
        if (previousRadius == newRadius) {
            return;
        }

        ChunkPos canonicalPos = new ChunkPos(wcx, wcz);
        acquire(level, canonicalPos, newRadius, GlobeWorld.CANONICAL_ALIAS_SIMULATION_TICKET, CANONICAL_SIMULATION_REFS);
        release(level, canonicalPos, previousRadius, GlobeWorld.CANONICAL_ALIAS_SIMULATION_TICKET, CANONICAL_SIMULATION_REFS);
    }

    private static int simulationRadius(FullChunkStatus status) {
        if (status == FullChunkStatus.BLOCK_TICKING || status == FullChunkStatus.ENTITY_TICKING) {
            return ChunkLevel.byStatus(FullChunkStatus.FULL) - ChunkLevel.byStatus(status);
        }
        return NO_TICKET_RADIUS;
    }

    private static void acquire(
            ServerLevel level,
            ChunkPos canonicalPos,
            int radius,
            TicketType ticketType,
            ConcurrentHashMap<CanonicalTicketKey, AtomicInteger> refs) {
        if (radius == NO_TICKET_RADIUS) {
            return;
        }

        CanonicalTicketKey key = new CanonicalTicketKey(level, canonicalPos.pack(), radius);
        AtomicInteger ref = refs.computeIfAbsent(key, ignored -> new AtomicInteger());
        int previousRefs = ref.getAndIncrement();
        if (previousRefs == 0) {
            level.getChunkSource().addTicketWithRadius(ticketType, canonicalPos, radius);
            ChunkLoadDiagnostics.canonicalTicketAcquired(level, canonicalPos, radius, previousRefs + 1);
        }
    }

    private static void release(
            ServerLevel level,
            ChunkPos canonicalPos,
            int radius,
            TicketType ticketType,
            ConcurrentHashMap<CanonicalTicketKey, AtomicInteger> refs) {
        if (radius == NO_TICKET_RADIUS) {
            return;
        }

        CanonicalTicketKey key = new CanonicalTicketKey(level, canonicalPos.pack(), radius);
        AtomicInteger ref = refs.get(key);
        if (ref == null) {
            return;
        }

        if (ref.decrementAndGet() <= 0) {
            refs.remove(key);
            level.getChunkSource().removeTicketWithRadius(ticketType, canonicalPos, radius);
            ChunkLoadDiagnostics.canonicalTicketReleased(level, canonicalPos, radius);
        }
    }

    private record AliasKey(ServerLevel level, long aliasChunk) {
    }

    private record CanonicalTicketKey(ServerLevel level, long canonicalChunk, int radius) {
    }
}
