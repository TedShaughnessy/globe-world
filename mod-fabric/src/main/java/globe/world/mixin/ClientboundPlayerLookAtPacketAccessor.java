package globe.world.mixin;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientboundPlayerLookAtPacket.class)
public interface ClientboundPlayerLookAtPacketAccessor {
    @Accessor("x")
    double globeWorld$getX();

    @Accessor("y")
    double globeWorld$getY();

    @Accessor("z")
    double globeWorld$getZ();

    @Accessor("entity")
    int globeWorld$getEntity();

    @Accessor("fromAnchor")
    EntityAnchorArgument.Anchor globeWorld$getFromAnchor();

    @Accessor("toAnchor")
    EntityAnchorArgument.Anchor globeWorld$getToAnchor();

    @Accessor("atEntity")
    boolean globeWorld$isAtEntity();

    @Invoker("<init>")
    static ClientboundPlayerLookAtPacket globeWorld$new(FriendlyByteBuf input) {
        throw new AssertionError();
    }
}
