package globe.world.mixin;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLevelChunkWithLightPacket.class)
public interface LevelChunkPacketAccess {
    @Accessor("x") void setX(int x);
    @Accessor("z") void setZ(int z);
}
