package globe.world.util;

import net.minecraft.server.level.ServerPlayer;

public final class PlayerCanonicalizer {
    private PlayerCanonicalizer() {
    }

    public static boolean canonicalize(ServerPlayer player) {
        double x = CoordUtil.wrapBlock(player.level(), player.getX());
        double z = CoordUtil.wrapBlock(player.level(), player.getZ());
        if (x == player.getX() && z == player.getZ()) {
            return false;
        }

        player.snapTo(x, player.getY(), z, player.getYRot(), player.getXRot());
        player.syncPacketPositionCodec(x, player.getY(), z);
        return true;
    }
}
