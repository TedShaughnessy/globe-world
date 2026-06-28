package globe.world.map;

import globe.world.GlobeWorld;
import globe.world.atlas.GlobeAtlasPowerState;
import globe.world.atlas.GlobeAtlasSurvey;
import globe.world.atlas.GlobeAtlasSurveyState;
import globe.world.atlas.GlobeAtlasSurveyWindows;
import globe.world.atlas.GlobeDiscoveryRewards;
import globe.world.block.entity.GlobeBlockEntity;
import globe.world.network.GlobeAtlasSurveyWindowPayload;
import globe.world.network.GlobeMapSnapshotPayload;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GlobeMapTracker {
    private static final Identifier MASTERED_ATLAS_ADVANCEMENT = Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "mastered_atlas");
    private static final Identifier ADVENTURING_TIME_ADVANCEMENT = Identifier.withDefaultNamespace("adventure/adventuring_time");
    private static final int UPDATE_INTERVAL_TICKS = 5;
    private static final int SYNC_INTERVAL_TICKS = 20;
    private static final int REVEAL_RADIUS_BLOCKS = 48;
    private static final int REVEAL_MOVE_THRESHOLD_BLOCKS = 8;
    private static final int PERIODIC_REVEAL_TICKS = 200;
    private static final int MAX_PIXELS_PER_PLAYER_REVEAL = 20_000;
    private static final int SURVEY_REVEAL_RADIUS_CHUNKS = 2;
    private static final int SURVEY_GAP_FILL_PADDING_CHUNKS = 2;
    private static final int SURVEY_MAX_GAP_CHUNKS = 12;
    private static final int PLACED_SURVEY_VIEW_DISTANCE_BLOCKS = 96;
    private static final int MAX_PLACED_SURVEY_WINDOWS_PER_PLAYER = 2;
    private static final Map<ResourceKey<Level>, Integer> LAST_SENT_REVISIONS = new HashMap<>();
    private static final Map<UUID, RevealState> LAST_REVEALS = new HashMap<>();
    private static final Map<UUID, WindowSyncState> LAST_HELD_SURVEY_WINDOWS = new HashMap<>();
    private static final Map<UUID, Map<Long, Integer>> LAST_PLACED_SURVEY_WINDOWS = new HashMap<>();

    private GlobeMapTracker() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GlobeMapTracker::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendInitialSnapshot(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.player.getUUID();
            LAST_REVEALS.remove(playerId);
            LAST_HELD_SURVEY_WINDOWS.remove(playerId);
            LAST_PLACED_SURVEY_WINDOWS.remove(playerId);
        });
    }

    public static void refreshChangedColumn(final ServerLevel level, final BlockPos pos) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (!tiling.enabled()) {
            return;
        }

        GlobeMapSavedData data = GlobeMapSavedData.getIfPresent(level, tiling);
        if (data != null) {
            data.refreshColumn(level, tiling, pos);
        }
    }

    private static void tick(final MinecraftServer server) {
        if (server.getTickCount() % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (!tiling.enabled()) {
            return;
        }

        GlobeMapSavedData data = GlobeMapSavedData.get(overworld, tiling);
        GlobeAtlasSurveyState survey = GlobeAtlasSurvey.surveyMode(tiling)
                ? GlobeAtlasSurveyState.get(overworld, tiling)
                : null;
        revealForPlayers(server, overworld, tiling, data, survey);
        awardCompletionAdvancement(server, overworld, tiling, data);

        if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
            sendIfChanged(overworld, data, survey);
            if (survey != null) {
                sendSurveyWindows(overworld, tiling, survey);
            }
        }
    }

    private static void revealForPlayers(
            final MinecraftServer server,
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeMapSavedData data,
            final GlobeAtlasSurveyState survey) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.level() == level && !player.isSpectator()) {
                revealForPlayer(server, level, tiling, data, survey, player);
            }
        }
    }

    private static void revealForPlayer(
            final MinecraftServer server,
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeMapSavedData data,
            final GlobeAtlasSurveyState survey,
            final ServerPlayer player) {
        double canonicalX = CoordUtil.wrapBlock(tiling, player.getX());
        double canonicalZ = CoordUtil.wrapBlock(tiling, player.getZ());
        UUID playerId = player.getUUID();
        RevealState previous = LAST_REVEALS.get(playerId);
        if (previous != null && !previous.shouldReveal(tiling, canonicalX, canonicalZ, server.getTickCount())) {
            return;
        }

        boolean changed;
        if (survey != null) {
            changed = importAdventuringTimeBiomes(server, survey, player)
                    | recordSurveyVisit(level, tiling, survey, canonicalX, player.getY(), canonicalZ);
        } else {
            changed = data.revealAround(
                    level,
                    tiling,
                    canonicalX,
                    canonicalZ,
                    REVEAL_RADIUS_BLOCKS,
                    MAX_PIXELS_PER_PLAYER_REVEAL);
        }
        if (!changed) {
            LAST_REVEALS.put(playerId, new RevealState(canonicalX, canonicalZ, server.getTickCount()));
        }
    }

    private static boolean importAdventuringTimeBiomes(
            final MinecraftServer server,
            final GlobeAtlasSurveyState survey,
            final ServerPlayer player) {
        AdvancementHolder advancement = server.getAdvancements().get(ADVENTURING_TIME_ADVANCEMENT);
        if (advancement == null) {
            return false;
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        boolean changed = progress.isDone() && survey.recordCompletedBiomes();
        for (String criterion : progress.getCompletedCriteria()) {
            Identifier biomeId = Identifier.tryParse(criterion);
            if (biomeId != null) {
                changed |= survey.recordBiome(biomeId);
            }
        }
        return changed;
    }

    private static boolean recordSurveyVisit(
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final double canonicalX,
            final double y,
            final double canonicalZ) {
        int centerChunkX = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(Mth.floor(canonicalX)));
        int centerChunkZ = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(Mth.floor(canonicalZ)));
        boolean changed = false;
        int radiusSqr = SURVEY_REVEAL_RADIUS_CHUNKS * SURVEY_REVEAL_RADIUS_CHUNKS;
        for (int dz = -SURVEY_REVEAL_RADIUS_CHUNKS; dz <= SURVEY_REVEAL_RADIUS_CHUNKS; dz++) {
            for (int dx = -SURVEY_REVEAL_RADIUS_CHUNKS; dx <= SURVEY_REVEAL_RADIUS_CHUNKS; dx++) {
                if (dx * dx + dz * dz > radiusSqr) {
                    continue;
                }

                int chunkX = CoordUtil.wrapChunk(tiling, centerChunkX + dx);
                int chunkZ = CoordUtil.wrapChunk(tiling, centerChunkZ + dz);
                changed |= recordSurveyChunk(level, tiling, survey, chunkX, chunkZ, y, false);
            }
        }
        return fillSurveyGaps(level, tiling, survey, centerChunkX, centerChunkZ, y) | changed;
    }

    private static boolean recordSurveyChunk(
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final int chunkX,
            final int chunkZ,
            final double y,
            final boolean allowUnknownUnloaded) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null || chunk.isEmpty()) {
            return allowUnknownUnloaded && survey.recordVisitedChunk(tiling, chunkX, chunkZ);
        }

        Optional<Identifier> biome = loadedChunkBiome(level, chunkX, chunkZ, y);
        return biome
                .map(biomeId -> survey.recordVisitedChunk(tiling, chunkX, chunkZ, biomeId))
                .orElseGet(() -> survey.recordVisitedChunk(tiling, chunkX, chunkZ));
    }

    private static Optional<Identifier> loadedChunkBiome(
            final ServerLevel level,
            final int chunkX,
            final int chunkZ,
            final double y) {
        int blockX = SectionPos.sectionToBlockCoord(chunkX) + 8;
        int blockZ = SectionPos.sectionToBlockCoord(chunkZ) + 8;
        Holder<Biome> biome = level.getBiome(BlockPos.containing(blockX, y, blockZ));
        return biome.unwrapKey().map(ResourceKey::identifier);
    }

    private static boolean fillSurveyGaps(
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final int centerChunkX,
            final int centerChunkZ,
            final double y) {
        int radius = SURVEY_REVEAL_RADIUS_CHUNKS + SURVEY_GAP_FILL_PADDING_CHUNKS;
        int size = radius * 2 + 1;
        boolean[] seen = new boolean[size * size];
        int[] queue = new int[size * size];
        int[] component = new int[size * size];
        boolean changed = false;

        for (int start = 0; start < seen.length; start++) {
            if (seen[start]) {
                continue;
            }

            int localX = start % size;
            int localZ = start / size;
            int chunkX = CoordUtil.wrapChunk(tiling, centerChunkX + localX - radius);
            int chunkZ = CoordUtil.wrapChunk(tiling, centerChunkZ + localZ - radius);
            if (survey.isVisitedChunk(chunkX, chunkZ)) {
                seen[start] = true;
                continue;
            }

            int componentSize = collectLocalSurveyGap(
                    tiling,
                    survey,
                    centerChunkX,
                    centerChunkZ,
                    radius,
                    size,
                    start,
                    seen,
                    queue,
                    component);
            if (componentSize > SURVEY_MAX_GAP_CHUNKS || touchesLocalEdge(component, componentSize, size)) {
                continue;
            }

            for (int i = 0; i < componentSize; i++) {
                int index = component[i];
                int fillX = CoordUtil.wrapChunk(tiling, centerChunkX + index % size - radius);
                int fillZ = CoordUtil.wrapChunk(tiling, centerChunkZ + index / size - radius);
                changed |= recordSurveyChunk(level, tiling, survey, fillX, fillZ, y, true);
            }
        }

        return changed;
    }

    private static int collectLocalSurveyGap(
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final int centerChunkX,
            final int centerChunkZ,
            final int radius,
            final int size,
            final int start,
            final boolean[] seen,
            final int[] queue,
            final int[] component) {
        int head = 0;
        int tail = 0;
        int componentSize = 0;
        seen[start] = true;
        queue[tail++] = start;

        while (head < tail) {
            int index = queue[head++];
            component[componentSize++] = index;
            int localX = index % size;
            int localZ = index / size;

            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }

                    int nextX = localX + dx;
                    int nextZ = localZ + dz;
                    if (nextX < 0 || nextX >= size || nextZ < 0 || nextZ >= size) {
                        continue;
                    }

                    int next = nextX + nextZ * size;
                    if (seen[next]) {
                        continue;
                    }

                    int chunkX = CoordUtil.wrapChunk(tiling, centerChunkX + nextX - radius);
                    int chunkZ = CoordUtil.wrapChunk(tiling, centerChunkZ + nextZ - radius);
                    if (survey.isVisitedChunk(chunkX, chunkZ)) {
                        seen[next] = true;
                        continue;
                    }

                    seen[next] = true;
                    queue[tail++] = next;
                }
            }
        }

        return componentSize;
    }

    private static boolean touchesLocalEdge(final int[] component, final int componentSize, final int size) {
        for (int i = 0; i < componentSize; i++) {
            int index = component[i];
            int x = index % size;
            int z = index / size;
            if (x == 0 || z == 0 || x == size - 1 || z == size - 1) {
                return true;
            }
        }
        return false;
    }

    private static void awardCompletionAdvancement(
            final MinecraftServer server,
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeMapSavedData data) {
        if (GlobeAtlasSurvey.surveyMode(tiling)) {
            return;
        }
        if (!data.complete()) {
            return;
        }

        AdvancementHolder advancement = server.getAdvancements().get(MASTERED_ATLAS_ADVANCEMENT);
        if (advancement == null) {
            return;
        }

        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (player.level() == level && !player.isSpectator()) {
                player.getAdvancements().award(advancement, "completed");
            }
        }
    }

    private static void sendInitialSnapshot(final ServerPlayer player) {
        ServerLevel level = player.level();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        if (tiling.enabled()) {
            GlobeMapSavedData data = GlobeMapSavedData.get(level, tiling);
            GlobeAtlasSurveyState survey = GlobeAtlasSurvey.surveyMode(tiling)
                    ? GlobeAtlasSurveyState.get(level, tiling)
                    : null;
            send(player, data, survey);
            if (survey != null) {
                sendSurveyWindows(player, level, tiling, survey, true);
            }
        }
    }

    private static void sendIfChanged(final ServerLevel level, final GlobeMapSavedData data, final GlobeAtlasSurveyState survey) {
        int revision = combinedRevision(data, survey);
        int lastSentRevision = LAST_SENT_REVISIONS.getOrDefault(level.dimension(), -1);
        if (lastSentRevision == revision) {
            return;
        }

        GlobeDiscoveryRewards rewards = GlobeDiscoveryRewards.get(level);
        GlobeMapSnapshotPayload payload = GlobeMapSnapshotPayload.from(data, survey, rewards);
        for (ServerPlayer player : List.copyOf(level.getServer().getPlayerList().getPlayers())) {
            if (player.level() == level && ServerPlayNetworking.canSend(player, GlobeMapSnapshotPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
        LAST_SENT_REVISIONS.put(level.dimension(), revision);
    }

    private static void send(final ServerPlayer player, final GlobeMapSavedData data, final GlobeAtlasSurveyState survey) {
        if (ServerPlayNetworking.canSend(player, GlobeMapSnapshotPayload.TYPE)) {
            GlobeDiscoveryRewards rewards = GlobeDiscoveryRewards.get(player.level());
            ServerPlayNetworking.send(player, GlobeMapSnapshotPayload.from(data, survey, rewards));
        }
    }

    private static void sendSurveyWindows(
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey) {
        for (ServerPlayer player : List.copyOf(level.getServer().getPlayerList().getPlayers())) {
            if (player.level() == level) {
                sendSurveyWindows(player, level, tiling, survey, false);
            }
        }
    }

    private static void sendSurveyWindows(
            final ServerPlayer player,
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final boolean force) {
        if (player.isSpectator() || !ServerPlayNetworking.canSend(player, GlobeAtlasSurveyWindowPayload.TYPE)) {
            return;
        }

        GlobeAtlasPowerState powerState = GlobeAtlasPowerState.get(level);
        List<GlobeAtlasPowerState.Entry> atlases = powerState.entries();
        sendHeldSurveyWindow(player, level, tiling, survey, atlases, force);
        sendPlacedSurveyWindows(player, level, tiling, survey, atlases, force);
    }

    private static void sendHeldSurveyWindow(
            final ServerPlayer player,
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final List<GlobeAtlasPowerState.Entry> atlases,
            final boolean force) {
        double canonicalX = CoordUtil.wrapBlock(tiling, player.getX());
        double canonicalZ = CoordUtil.wrapBlock(tiling, player.getZ());
        int centerChunkX = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(Mth.floor(canonicalX)));
        int centerChunkZ = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(Mth.floor(canonicalZ)));
        UUID playerId = player.getUUID();
        WindowSyncState previous = LAST_HELD_SURVEY_WINDOWS.get(playerId);
        if (!force && previous != null && previous.matches(centerChunkX, centerChunkZ, survey.revision())) {
            return;
        }

        ServerPlayNetworking.send(player, GlobeAtlasSurveyWindows.held(level, tiling, survey, player, atlases));
        LAST_HELD_SURVEY_WINDOWS.put(playerId, new WindowSyncState(centerChunkX, centerChunkZ, survey.revision()));
    }

    private static void sendPlacedSurveyWindows(
            final ServerPlayer player,
            final ServerLevel level,
            final DimensionTiling tiling,
            final GlobeAtlasSurveyState survey,
            final List<GlobeAtlasPowerState.Entry> atlases,
            final boolean force) {
        int sent = 0;
        UUID playerId = player.getUUID();
        Map<Long, Integer> sentRevisions = LAST_PLACED_SURVEY_WINDOWS.computeIfAbsent(playerId, id -> new HashMap<>());
        List<PlacedSurveyCandidate> candidates = new ArrayList<>();
        for (GlobeAtlasPowerState.Entry entry : atlases) {
            BlockPos pos = entry.pos();
            if (!(level.getBlockEntity(pos) instanceof GlobeBlockEntity atlas) || !atlas.projectionEnabled()) {
                continue;
            }
            double distanceSqr = CoordUtil.wrappedDistanceSqr(
                    tiling,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D);
            if (distanceSqr > (double)PLACED_SURVEY_VIEW_DISTANCE_BLOCKS * PLACED_SURVEY_VIEW_DISTANCE_BLOCKS) {
                continue;
            }
            candidates.add(new PlacedSurveyCandidate(pos, distanceSqr));
        }

        candidates.sort(Comparator.comparingDouble(PlacedSurveyCandidate::distanceSqr));
        for (PlacedSurveyCandidate candidate : candidates) {
            if (sent >= MAX_PLACED_SURVEY_WINDOWS_PER_PLAYER) {
                return;
            }
            BlockPos pos = candidate.pos();
            int centerChunkX = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(pos.getX()));
            int centerChunkZ = CoordUtil.wrapChunk(tiling, SectionPos.blockToSectionCoord(pos.getZ()));
            long centerKey = new ChunkPos(centerChunkX, centerChunkZ).pack();
            if (!force && sentRevisions.getOrDefault(centerKey, -1) == survey.revision()) {
                continue;
            }

            ServerPlayNetworking.send(player, GlobeAtlasSurveyWindows.placed(level, tiling, survey, pos, atlases));
            sentRevisions.put(centerKey, survey.revision());
            sent++;
        }
    }

    private static int combinedRevision(final GlobeMapSavedData data, final GlobeAtlasSurveyState survey) {
        return survey == null ? data.revision() : 31 * data.revision() + survey.revision();
    }

    private record RevealState(double x, double z, int tick) {
        boolean shouldReveal(final DimensionTiling tiling, final double currentX, final double currentZ, final int currentTick) {
            if (currentTick - this.tick >= PERIODIC_REVEAL_TICKS) {
                return true;
            }

            double moveThresholdSqr = (double)REVEAL_MOVE_THRESHOLD_BLOCKS * REVEAL_MOVE_THRESHOLD_BLOCKS;
            return CoordUtil.wrappedDistanceSqrXZ(tiling, currentX, currentZ, this.x, this.z) >= moveThresholdSqr;
        }
    }

    private record WindowSyncState(int centerChunkX, int centerChunkZ, int revision) {
        boolean matches(final int centerChunkX, final int centerChunkZ, final int revision) {
            return this.centerChunkX == centerChunkX && this.centerChunkZ == centerChunkZ && this.revision == revision;
        }
    }

    private record PlacedSurveyCandidate(BlockPos pos, double distanceSqr) {
    }
}
