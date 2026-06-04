package globe.world.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundSectionBlocksUpdatePacket.class)
public interface ClientboundSectionBlocksUpdatePacketAccessor {
    @Accessor("sectionPos")
    SectionPos globeWorld$getSectionPos();

    @Mutable
    @Accessor("sectionPos")
    void globeWorld$setSectionPos(SectionPos sectionPos);

    @Accessor("positions")
    short[] globeWorld$getPositions();

    @Mutable
    @Accessor("positions")
    void globeWorld$setPositions(short[] positions);

    @Accessor("states")
    BlockState[] globeWorld$getStates();

    @Mutable
    @Accessor("states")
    void globeWorld$setStates(BlockState[] states);
}
