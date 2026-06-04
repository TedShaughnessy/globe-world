package globe.world.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientboundLightUpdatePacket.class)
public interface ClientboundLightUpdatePacketAccessor {
    @Invoker("<init>")
    static ClientboundLightUpdatePacket globeWorld$new(FriendlyByteBuf input) {
        throw new AssertionError();
    }
}
