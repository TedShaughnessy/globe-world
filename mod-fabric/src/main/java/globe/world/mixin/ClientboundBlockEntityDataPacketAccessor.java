package globe.world.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientboundBlockEntityDataPacket.class)
public interface ClientboundBlockEntityDataPacketAccessor {
    @Invoker("<init>")
    static ClientboundBlockEntityDataPacket globeWorld$new(
            BlockPos pos,
            BlockEntityType<?> type,
            CompoundTag tag) {
        throw new AssertionError();
    }
}
