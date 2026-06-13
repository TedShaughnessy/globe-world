package globe.world.topology;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugGameEventInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class TopologicalGameEvents {
    private TopologicalGameEvents() {
    }

    public static boolean post(ServerLevel level, Holder<GameEvent> event, Vec3 source, GameEvent.Context context) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return false;
        }

        Vec3 canonicalSource = topology.canonicalBlock(source);
        List<GameEvent.ListenerInfo> byDistance = new ArrayList<>();
        Set<GameEventListener> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean applicable = false;

        // Narrow copy of vanilla GameEventDispatcher.post(...): the only changes are
        // section wrapping, listener identity dedupe, and listener-local source positions.
        for (SectionPos section : listenerSections(level, source, event.value().notificationRadius())) {
            ChunkAccess chunk = level.getChunkSource().getChunkNow(section.x(), section.z());
            if (chunk == null) {
                continue;
            }

            Vec3 sectionLocalSource = listenerLocalSource(topology, canonicalSource, Vec3.atCenterOf(section.center()));
            applicable |= chunk.getListenerRegistry(section.y()).visitInRangeListeners(event, sectionLocalSource, context, (listener, listenerPos) -> {
                if (!seen.add(listener)) {
                    return;
                }

                Vec3 listenerLocalSource = listenerLocalSource(topology, canonicalSource, listenerPos);
                if (listener.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                    byDistance.add(new GameEvent.ListenerInfo(event, listenerLocalSource, context, listener, listenerPos));
                } else {
                    listener.handleGameEvent(level, event, context, listenerLocalSource);
                }
            });
        }

        if (!byDistance.isEmpty()) {
            Collections.sort(byDistance);
            for (GameEvent.ListenerInfo listenerInfo : byDistance) {
                listenerInfo.recipient().handleGameEvent(level, listenerInfo.gameEvent(), listenerInfo.context(), listenerInfo.source());
            }
        }

        if (applicable) {
            level.debugSynchronizers()
                    .broadcastEventToTracking(BlockPos.containing(canonicalSource), DebugSubscriptions.GAME_EVENTS, new DebugGameEventInfo(event, canonicalSource));
        }

        return true;
    }

    public static Vec3 listenerLocalSource(TopologyContext topology, Vec3 canonicalSource, Vec3 listenerPos) {
        if (!topology.enabled()) {
            return canonicalSource;
        }
        return topology.virtualBlockForViewer(canonicalSource, listenerPos);
    }

    public static List<SectionPos> listenerSections(ServerLevel level, Vec3 source, int radius) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        if (!topology.enabled()) {
            return vanillaListenerSections(source, radius);
        }

        BlockPos center = BlockPos.containing(source);
        AABB visibleRange = new AABB(
                center.getX() - radius,
                center.getY() - radius,
                center.getZ() - radius,
                center.getX() + radius,
                center.getY() + radius,
                center.getZ() + radius);
        List<AABB> canonicalBoxes = TopologicalEntityQueries.canonicalQueryBoxes(topology, visibleRange);
        LongSet seen = new LongOpenHashSet();
        List<SectionPos> sections = new ArrayList<>();
        int minCanonicalSection = -topology.tileSizeChunks() / 2;
        int maxCanonicalSection = minCanonicalSection + topology.tileSizeChunks() - 1;
        int minSectionY = level.getMinSectionY();
        int maxSectionY = level.getMaxSectionY();

        for (AABB box : canonicalBoxes) {
            int minX = clamp(SectionPos.blockToSectionCoord(box.minX), minCanonicalSection, maxCanonicalSection);
            int maxX = clamp(SectionPos.blockToSectionCoord(box.maxX), minCanonicalSection, maxCanonicalSection);
            int rawMinY = SectionPos.blockToSectionCoord(box.minY);
            int rawMaxY = SectionPos.blockToSectionCoord(box.maxY);
            if (rawMaxY < minSectionY || rawMinY > maxSectionY) {
                continue;
            }
            int minY = clamp(rawMinY, minSectionY, maxSectionY);
            int maxY = clamp(rawMaxY, minSectionY, maxSectionY);
            int minZ = clamp(SectionPos.blockToSectionCoord(box.minZ), minCanonicalSection, maxCanonicalSection);
            int maxZ = clamp(SectionPos.blockToSectionCoord(box.maxZ), minCanonicalSection, maxCanonicalSection);

            for (int sectionX = minX; sectionX <= maxX; sectionX++) {
                for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
                    for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                        long packed = SectionPos.asLong(sectionX, sectionY, sectionZ);
                        if (seen.add(packed)) {
                            sections.add(SectionPos.of(sectionX, sectionY, sectionZ));
                        }
                    }
                }
            }
        }

        return sections;
    }

    private static List<SectionPos> vanillaListenerSections(Vec3 source, int radius) {
        BlockPos center = BlockPos.containing(source);
        int minX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int minY = SectionPos.blockToSectionCoord(center.getY() - radius);
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int maxY = SectionPos.blockToSectionCoord(center.getY() + radius);
        int maxZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        List<SectionPos> sections = new ArrayList<>();
        for (int sectionX = minX; sectionX <= maxX; sectionX++) {
            for (int sectionZ = minZ; sectionZ <= maxZ; sectionZ++) {
                for (int sectionY = minY; sectionY <= maxY; sectionY++) {
                    sections.add(SectionPos.of(sectionX, sectionY, sectionZ));
                }
            }
        }
        return sections;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
