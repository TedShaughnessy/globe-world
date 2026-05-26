package globe.world.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

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
}
