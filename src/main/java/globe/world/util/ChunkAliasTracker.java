package globe.world.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChunkAliasTracker {
    private static final ConcurrentMap<UUID, ConcurrentMap<Long, ConcurrentMap<Long, Boolean>>> ALIASES =
            new ConcurrentHashMap<>();

    public static void addAlias(ServerPlayer player, int canonicalX, int canonicalZ, int aliasX, int aliasZ) {
        aliasesFor(player, canonicalX, canonicalZ).put(key(aliasX, aliasZ), Boolean.TRUE);
    }

    public static void removeAlias(ServerPlayer player, int canonicalX, int canonicalZ, int aliasX, int aliasZ) {
        ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> byCanonical = ALIASES.get(player.getUUID());
        if (byCanonical == null) return;

        long canonicalKey = key(canonicalX, canonicalZ);
        ConcurrentMap<Long, Boolean> aliases = byCanonical.get(canonicalKey);
        if (aliases == null) return;

        aliases.remove(key(aliasX, aliasZ));
        if (aliases.isEmpty()) {
            byCanonical.remove(canonicalKey);
        }
        if (byCanonical.isEmpty()) {
            ALIASES.remove(player.getUUID());
        }
    }

    public static List<ChunkPos> aliasesForCanonical(ServerPlayer player, int canonicalX, int canonicalZ) {
        ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> byCanonical = ALIASES.get(player.getUUID());
        if (byCanonical == null) return List.of();

        ConcurrentMap<Long, Boolean> aliases = byCanonical.get(key(canonicalX, canonicalZ));
        if (aliases == null || aliases.isEmpty()) return List.of();

        List<ChunkPos> result = new ArrayList<>(aliases.size());
        for (long aliasKey : aliases.keySet()) {
            result.add(unpack(aliasKey));
        }
        return result;
    }

    private static ConcurrentMap<Long, Boolean> aliasesFor(
            ServerPlayer player,
            int canonicalX,
            int canonicalZ) {
        return ALIASES
                .computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key(canonicalX, canonicalZ), ignored -> new ConcurrentHashMap<>());
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | ((long) z & 0xFFFFFFFFL);
    }

    private static ChunkPos unpack(long key) {
        return new ChunkPos((int) (key >> 32), (int) key);
    }
}
