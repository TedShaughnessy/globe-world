package globe.world.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EntityPacketUtil {
    public static Packet<? super ClientGamePacketListener> virtualizeFor(
            Packet<? super ClientGamePacketListener> packet,
            ServerPlayer viewer) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            return virtualizeBundle(bundle, viewer);
        }
        if (packet instanceof ClientboundAddEntityPacket add) {
            return virtualizeAddEntity(add, viewer);
        }
        if (packet instanceof ClientboundEntityPositionSyncPacket sync) {
            return virtualizePositionSync(sync, viewer);
        }
        if (packet instanceof ClientboundTeleportEntityPacket teleport) {
            return virtualizeTeleport(teleport, viewer);
        }
        if (packet instanceof ClientboundDamageEventPacket damage) {
            return virtualizeDamage(damage, viewer);
        }
        if (packet instanceof ClientboundMoveVehiclePacket vehicle) {
            return virtualizeMoveVehicle(vehicle, viewer);
        }
        if (packet instanceof ClientboundMoveMinecartPacket minecart) {
            return virtualizeMoveMinecart(minecart, viewer);
        }
        return packet;
    }

    private static ClientboundBundlePacket virtualizeBundle(ClientboundBundlePacket packet, ServerPlayer viewer) {
        List<Packet<? super ClientGamePacketListener>> virtualPackets = new ArrayList<>();
        boolean changed = false;
        for (Packet<? super ClientGamePacketListener> subPacket : packet.subPackets()) {
            Packet<? super ClientGamePacketListener> virtualPacket = virtualizeFor(subPacket, viewer);
            virtualPackets.add(virtualPacket);
            if (virtualPacket != subPacket) {
                changed = true;
            }
        }
        if (!changed) return packet;
        return new ClientboundBundlePacket(virtualPackets);
    }

    private static ClientboundAddEntityPacket virtualizeAddEntity(ClientboundAddEntityPacket packet, ServerPlayer viewer) {
        double x = CoordUtil.virtualBlock(viewer.level(), packet.getX(), viewer.getX());
        double z = CoordUtil.virtualBlock(viewer.level(), packet.getZ(), viewer.getZ());
        if (x == packet.getX() && z == packet.getZ()) return packet;

        return new ClientboundAddEntityPacket(
            packet.getId(),
            packet.getUUID(),
            x,
            packet.getY(),
            z,
            packet.getXRot(),
            packet.getYRot(),
            packet.getType(),
            packet.getData(),
            packet.getMovement(),
            packet.getYHeadRot()
        );
    }

    private static ClientboundEntityPositionSyncPacket virtualizePositionSync(
            ClientboundEntityPositionSyncPacket packet,
            ServerPlayer viewer) {
        PositionMoveRotation values = packet.values();
        PositionMoveRotation virtualValues = virtualize(values, viewer, false, false);
        if (virtualValues == values) return packet;
        return new ClientboundEntityPositionSyncPacket(packet.id(), virtualValues, packet.onGround());
    }

    private static ClientboundTeleportEntityPacket virtualizeTeleport(
            ClientboundTeleportEntityPacket packet,
            ServerPlayer viewer) {
        PositionMoveRotation change = packet.change();
        boolean relativeX = packet.relatives().contains(Relative.X);
        boolean relativeZ = packet.relatives().contains(Relative.Z);
        PositionMoveRotation virtualChange = virtualize(change, viewer, relativeX, relativeZ);
        if (virtualChange == change) return packet;
        return new ClientboundTeleportEntityPacket(packet.id(), virtualChange, packet.relatives(), packet.onGround());
    }

    private static ClientboundDamageEventPacket virtualizeDamage(
            ClientboundDamageEventPacket packet,
            ServerPlayer viewer) {
        Optional<Vec3> sourcePosition = packet.sourcePosition();
        if (sourcePosition.isEmpty()) return packet;

        Vec3 pos = sourcePosition.get();
        Vec3 virtualPos = virtualize(pos, viewer);
        if (virtualPos == pos) return packet;

        return new ClientboundDamageEventPacket(
                packet.entityId(),
                packet.sourceType(),
                packet.sourceCauseId(),
                packet.sourceDirectId(),
                Optional.of(virtualPos)
        );
    }

    private static ClientboundMoveVehiclePacket virtualizeMoveVehicle(
            ClientboundMoveVehiclePacket packet,
            ServerPlayer viewer) {
        Vec3 virtualPos = virtualize(packet.position(), viewer);
        if (virtualPos == packet.position()) return packet;
        return new ClientboundMoveVehiclePacket(virtualPos, packet.yRot(), packet.xRot());
    }

    private static ClientboundMoveMinecartPacket virtualizeMoveMinecart(
            ClientboundMoveMinecartPacket packet,
            ServerPlayer viewer) {
        List<NewMinecartBehavior.MinecartStep> virtualSteps = new ArrayList<>(packet.lerpSteps().size());
        boolean changed = false;
        for (NewMinecartBehavior.MinecartStep step : packet.lerpSteps()) {
            Vec3 virtualPos = virtualize(step.position(), viewer);
            if (virtualPos != step.position()) {
                changed = true;
            }
            virtualSteps.add(new NewMinecartBehavior.MinecartStep(
                    virtualPos,
                    step.movement(),
                    step.yRot(),
                    step.xRot(),
                    step.weight()
            ));
        }

        if (!changed) return packet;
        return new ClientboundMoveMinecartPacket(packet.entityId(), virtualSteps);
    }

    private static PositionMoveRotation virtualize(
            PositionMoveRotation values,
            ServerPlayer viewer,
            boolean keepX,
            boolean keepZ) {
        Vec3 pos = values.position();
        double x = keepX ? pos.x : CoordUtil.virtualBlock(viewer.level(), pos.x, viewer.getX());
        double z = keepZ ? pos.z : CoordUtil.virtualBlock(viewer.level(), pos.z, viewer.getZ());
        if (x == pos.x && z == pos.z) return values;
        return new PositionMoveRotation(new Vec3(x, pos.y, z), values.deltaMovement(), values.yRot(), values.xRot());
    }

    private static Vec3 virtualize(Vec3 pos, ServerPlayer viewer) {
        double canonicalX = CoordUtil.wrapBlock(viewer.level(), pos.x);
        double canonicalZ = CoordUtil.wrapBlock(viewer.level(), pos.z);
        double x = CoordUtil.virtualBlock(viewer.level(), canonicalX, viewer.getX());
        double z = CoordUtil.virtualBlock(viewer.level(), canonicalZ, viewer.getZ());
        if (x == pos.x && z == pos.z) return pos;
        return new Vec3(x, pos.y, z);
    }
}
