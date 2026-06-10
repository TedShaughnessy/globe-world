package globe.world.util;

import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WorldEventPacketUtil {
    private WorldEventPacketUtil() {
    }

    public static boolean handles(Packet<?> packet) {
        return packet instanceof ClientboundSoundPacket
                || packet instanceof ClientboundSoundEntityPacket
                || packet instanceof ClientboundLevelEventPacket
                || packet instanceof ClientboundBlockEventPacket
                || packet instanceof ClientboundBlockDestructionPacket
                || packet instanceof ClientboundLevelParticlesPacket
                || packet instanceof ClientboundExplodePacket;
    }

    public static Packet<?> virtualizeFor(Packet<?> packet, ServerPlayer viewer) {
        if (packet instanceof ClientboundSoundPacket sound) {
            return virtualizeSound(sound, viewer);
        }
        if (packet instanceof ClientboundLevelEventPacket levelEvent) {
            return virtualizeLevelEvent(levelEvent, viewer);
        }
        if (packet instanceof ClientboundBlockEventPacket blockEvent) {
            return virtualizeBlockEvent(blockEvent, viewer);
        }
        if (packet instanceof ClientboundBlockDestructionPacket blockDestruction) {
            return virtualizeBlockDestruction(blockDestruction, viewer);
        }
        if (packet instanceof ClientboundLevelParticlesPacket particles) {
            return virtualizeParticles(particles, viewer);
        }
        if (packet instanceof ClientboundExplodePacket explode) {
            return virtualizeExplode(explode, viewer);
        }
        return packet;
    }

    public static List<Packet<?>> virtualizeForLoadedAliases(Packet<?> packet, ServerPlayer viewer) {
        if (packet instanceof ClientboundBlockEventPacket) {
            return virtualizeBlockEventForLoadedAliases((ClientboundBlockEventPacket) packet, viewer);
        }
        return List.of(virtualizeFor(packet, viewer));
    }

    public static Vec3 virtualizePos(ServerLevel level, Vec3 pos, ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        Vec3 canonical = new Vec3(topology.canonicalBlockX(pos.x()), pos.y(), topology.canonicalBlockX(pos.z()));
        return topology.virtualBlockForViewer(canonical, viewer.position());
    }

    public static BlockPos virtualizeBlockPos(ServerLevel level, BlockPos pos, ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        BlockPos canonicalPos = topology.canonicalBlock(pos);
        BlockPos virtualPos = topology.virtualBlockForViewer(canonicalPos, viewer);
        if (virtualPos.equals(pos)) {
            return pos;
        }
        return virtualPos;
    }

    public static double wrappedDistanceSqr(ServerLevel level, Vec3 source, ServerPlayer viewer) {
        return TopologyContexts.forLevel(level).wrappedDistanceSqr(source, viewer.position());
    }

    public static double wrappedDistanceSqr(ServerLevel level, Vec3 source, Vec3 viewer) {
        return TopologyContexts.forLevel(level).wrappedDistanceSqr(source, viewer);
    }

    private static Packet<?> virtualizeSound(ClientboundSoundPacket packet, ServerPlayer viewer) {
        Vec3 virtualPos = virtualizePos(viewer.level(), new Vec3(packet.getX(), packet.getY(), packet.getZ()), viewer);
        if (virtualPos.x() == packet.getX() && virtualPos.z() == packet.getZ()) {
            return packet;
        }
        return new ClientboundSoundPacket(
                packet.getSound(),
                packet.getSource(),
                virtualPos.x(),
                virtualPos.y(),
                virtualPos.z(),
                packet.getVolume(),
                packet.getPitch(),
                packet.getSeed()
        );
    }

    private static Packet<?> virtualizeLevelEvent(ClientboundLevelEventPacket packet, ServerPlayer viewer) {
        BlockPos virtualPos = virtualizeBlockPos(viewer.level(), packet.getPos(), viewer);
        if (virtualPos.equals(packet.getPos())) {
            return packet;
        }
        return new ClientboundLevelEventPacket(packet.getType(), virtualPos, packet.getData(), packet.isGlobalEvent());
    }

    private static Packet<?> virtualizeBlockEvent(ClientboundBlockEventPacket packet, ServerPlayer viewer) {
        BlockPos virtualPos = virtualizeBlockPos(viewer.level(), packet.getPos(), viewer);
        if (virtualPos.equals(packet.getPos())) {
            return packet;
        }
        return new ClientboundBlockEventPacket(virtualPos, packet.getBlock(), packet.getB0(), packet.getB1());
    }

    private static List<Packet<?>> virtualizeBlockEventForLoadedAliases(
            ClientboundBlockEventPacket packet,
            ServerPlayer viewer) {
        TopologyContext topology = TopologyContexts.forLevel(viewer.level());
        BlockPos canonicalPos = topology.canonicalBlock(packet.getPos());
        ChunkPos canonicalChunk = topology.canonicalChunkForBlock(canonicalPos);
        List<ChunkPos> aliases = topology.loadedAliasesFor(viewer, canonicalChunk);
        if (aliases.isEmpty()) {
            return List.of(virtualizeBlockEvent(packet, viewer));
        }

        Set<BlockPos> seenPositions = new HashSet<>(aliases.size());
        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            BlockPos visiblePos = offsetBlockPos(canonicalPos, canonicalChunk, alias);
            if (seenPositions.add(visiblePos)) {
                packets.add(new ClientboundBlockEventPacket(
                        visiblePos,
                        packet.getBlock(),
                        packet.getB0(),
                        packet.getB1()
                ));
            }
        }
        return packets;
    }

    private static Packet<?> virtualizeBlockDestruction(ClientboundBlockDestructionPacket packet, ServerPlayer viewer) {
        BlockPos virtualPos = virtualizeBlockPos(viewer.level(), packet.getPos(), viewer);
        if (virtualPos.equals(packet.getPos())) {
            return packet;
        }
        return new ClientboundBlockDestructionPacket(packet.getId(), virtualPos, packet.getProgress());
    }

    private static BlockPos offsetBlockPos(
            BlockPos canonicalPos,
            ChunkPos canonicalChunk,
            ChunkPos alias) {
        int dx = (alias.x() - canonicalChunk.x()) * 16;
        int dz = (alias.z() - canonicalChunk.z()) * 16;
        if (dx == 0 && dz == 0) {
            return canonicalPos;
        }
        return canonicalPos.offset(dx, 0, dz);
    }

    private static Packet<?> virtualizeParticles(ClientboundLevelParticlesPacket packet, ServerPlayer viewer) {
        Vec3 virtualPos = virtualizePos(viewer.level(), new Vec3(packet.getX(), packet.getY(), packet.getZ()), viewer);
        if (virtualPos.x() == packet.getX() && virtualPos.z() == packet.getZ()) {
            return packet;
        }
        return new ClientboundLevelParticlesPacket(
                packet.getParticle(),
                packet.isOverrideLimiter(),
                packet.alwaysShow(),
                virtualPos.x(),
                virtualPos.y(),
                virtualPos.z(),
                packet.getXDist(),
                packet.getYDist(),
                packet.getZDist(),
                packet.getMaxSpeed(),
                packet.getCount()
        );
    }

    private static Packet<?> virtualizeExplode(ClientboundExplodePacket packet, ServerPlayer viewer) {
        Vec3 virtualCenter = virtualizePos(viewer.level(), packet.center(), viewer);
        if (virtualCenter.x() == packet.center().x() && virtualCenter.z() == packet.center().z()) {
            return packet;
        }
        return new ClientboundExplodePacket(
                virtualCenter,
                packet.radius(),
                packet.blockCount(),
                packet.playerKnockback(),
                packet.explosionParticle(),
                packet.explosionSound(),
                packet.blockParticles()
        );
    }
}
