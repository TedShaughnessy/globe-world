package globe.world.util;

import globe.world.topology.TopologyContexts;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class PlayerCanonicalizer {
    private PlayerCanonicalizer() {
    }

    public static boolean canonicalize(ServerPlayer player) {
        Vec3 canonical = TopologyContexts.forLevel(player.level()).canonicalBlock(player.position());
        double x = canonical.x();
        double z = canonical.z();
        if (x == player.getX() && z == player.getZ()) {
            return false;
        }

        player.snapTo(x, player.getY(), z, player.getYRot(), player.getXRot());
        player.syncPacketPositionCodec(x, player.getY(), z);
        return true;
    }
}
