package globe.world.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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
        double canonicalX = CoordUtil.wrapBlock(level, pos.x());
        double canonicalZ = CoordUtil.wrapBlock(level, pos.z());
        double virtualX = CoordUtil.virtualBlock(level, canonicalX, viewer.getX());
        double virtualZ = CoordUtil.virtualBlock(level, canonicalZ, viewer.getZ());
        if (virtualX == pos.x() && virtualZ == pos.z()) {
            return pos;
        }
        return new Vec3(virtualX, pos.y(), virtualZ);
    }

    public static BlockPos virtualizeBlockPos(ServerLevel level, BlockPos pos, ServerPlayer viewer) {
        BlockPos canonicalPos = CoordUtil.wrapBlockPos(level, pos);
        int virtualX = (int) CoordUtil.virtualBlock(level, canonicalPos.getX(), viewer.getX());
        int virtualZ = (int) CoordUtil.virtualBlock(level, canonicalPos.getZ(), viewer.getZ());
        if (virtualX == pos.getX() && virtualZ == pos.getZ()) {
            return pos;
        }
        return new BlockPos(virtualX, pos.getY(), virtualZ);
    }

    public static double wrappedDistanceSqr(ServerLevel level, Vec3 source, ServerPlayer viewer) {
        return CoordUtil.wrappedDistanceSqr(
                level,
                source.x(),
                source.y(),
                source.z(),
                viewer.getX(),
                viewer.getY(),
                viewer.getZ()
        );
    }

    public static double wrappedDistanceSqr(ServerLevel level, Vec3 source, Vec3 viewer) {
        return CoordUtil.wrappedDistanceSqr(
                level,
                source.x(),
                source.y(),
                source.z(),
                viewer.x(),
                viewer.y(),
                viewer.z()
        );
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
        BlockPos canonicalPos = CoordUtil.wrapBlockPos(viewer.level(), packet.getPos());
        int canonicalChunkX = SectionPos.blockToSectionCoord(canonicalPos.getX());
        int canonicalChunkZ = SectionPos.blockToSectionCoord(canonicalPos.getZ());
        List<ChunkPos> aliases = ChunkAliasTracker.aliasesForCanonical(
                viewer,
                viewer.level().dimension(),
                canonicalChunkX,
                canonicalChunkZ);
        if (aliases.isEmpty()) {
            return List.of(virtualizeBlockEvent(packet, viewer));
        }

        Set<BlockPos> seenPositions = new HashSet<>(aliases.size());
        List<Packet<?>> packets = new ArrayList<>(aliases.size());
        for (ChunkPos alias : aliases) {
            BlockPos visiblePos = offsetBlockPos(canonicalPos, canonicalChunkX, canonicalChunkZ, alias);
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
            int canonicalChunkX,
            int canonicalChunkZ,
            ChunkPos alias) {
        int dx = (alias.x() - canonicalChunkX) * 16;
        int dz = (alias.z() - canonicalChunkZ) * 16;
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
