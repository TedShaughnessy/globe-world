package globe.world.mixin;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import globe.world.GlobeChunkPacket;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Implements;

@Mixin(ClientboundLevelChunkWithLightPacket.class)
@Implements(@Interface(iface = GlobeChunkPacket.class, prefix = "globeWorld$"))
public class ClientboundLevelChunkWithLightMixin {

    @Unique private int globeWorld_vx = Integer.MIN_VALUE;
    @Unique private int globeWorld_vz = Integer.MIN_VALUE;

    @SuppressWarnings("unused")
    public void globeWorld$setVirtualPos(int x, int z) {
        this.globeWorld_vx = x;
        this.globeWorld_vz = z;
    }

    @Redirect(
        method = "write(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V",
        at = @At(value = "FIELD",
                 target = "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;x:I",
                 opcode = Opcodes.GETFIELD)
    )
    private int redirectGetX(ClientboundLevelChunkWithLightPacket self) {
        return globeWorld_vx != Integer.MIN_VALUE ? globeWorld_vx : self.getX();
    }

    @Redirect(
        method = "write(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V",
        at = @At(value = "FIELD",
                 target = "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;z:I",
                 opcode = Opcodes.GETFIELD)
    )
    private int redirectGetZ(ClientboundLevelChunkWithLightPacket self) {
        return globeWorld_vz != Integer.MIN_VALUE ? globeWorld_vz : self.getZ();
    }
}
