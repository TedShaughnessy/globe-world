package globe.world.util;

import globe.world.GlobeWorld;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CanonicalChunkTickets {
    private static final int NO_TICKET_RADIUS = -1;

    private static final ConcurrentHashMap<AliasKey, Integer> ALIAS_RADII = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<CanonicalTicketKey, AtomicInteger> CANONICAL_REFS = new ConcurrentHashMap<>();

    private CanonicalChunkTickets() {
    }

    public static void updateAliasStatus(ServerLevel level, ChunkPos aliasPos, FullChunkStatus status) {
        updateAliasRadius(level, aliasPos, status == FullChunkStatus.INACCESSIBLE ? NO_TICKET_RADIUS : 0);
    }

    public static void clearLevel(ServerLevel level) {
        for (AliasKey aliasKey : ALIAS_RADII.keySet()) {
            if (aliasKey.level() == level) {
                ALIAS_RADII.remove(aliasKey);
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
        acquire(level, canonicalPos, newRadius);
        release(level, canonicalPos, previousRadius);
    }

    private static void acquire(ServerLevel level, ChunkPos canonicalPos, int radius) {
        if (radius == NO_TICKET_RADIUS) {
            return;
        }

        CanonicalTicketKey key = new CanonicalTicketKey(level, canonicalPos.pack(), radius);
        if (CANONICAL_REFS.computeIfAbsent(key, ignored -> new AtomicInteger()).getAndIncrement() == 0) {
            level.getChunkSource().addTicketWithRadius(GlobeWorld.CANONICAL_ALIAS_TICKET, canonicalPos, radius);
        }
    }

    private static void release(ServerLevel level, ChunkPos canonicalPos, int radius) {
        if (radius == NO_TICKET_RADIUS) {
            return;
        }

        CanonicalTicketKey key = new CanonicalTicketKey(level, canonicalPos.pack(), radius);
        AtomicInteger ref = CANONICAL_REFS.get(key);
        if (ref == null) {
            return;
        }

        if (ref.decrementAndGet() <= 0) {
            CANONICAL_REFS.remove(key);
            level.getChunkSource().removeTicketWithRadius(GlobeWorld.CANONICAL_ALIAS_TICKET, canonicalPos, radius);
        }
    }

    private record AliasKey(ServerLevel level, long aliasChunk) {
    }

    private record CanonicalTicketKey(ServerLevel level, long canonicalChunk, int radius) {
    }
}
