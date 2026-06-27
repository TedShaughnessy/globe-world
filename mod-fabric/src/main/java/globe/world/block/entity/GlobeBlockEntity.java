package globe.world.block.entity;

import globe.world.GlobeWorldBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class GlobeBlockEntity extends BlockEntity {
    private static final String TAG_PROJECTION_ENABLED = "projection_enabled";

    private boolean projectionEnabled = true;

    public GlobeBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
        super(GlobeWorldBlocks.GLOBE_BLOCK_ENTITY, worldPosition, blockState);
    }

    public boolean projectionEnabled() {
        return this.projectionEnabled;
    }

    public boolean toggleProjection() {
        this.projectionEnabled = !this.projectionEnabled;
        this.markModeChanged();
        return this.projectionEnabled;
    }

    private void markModeChanged() {
        this.setChanged();
        if (this.level != null) {
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        }
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean(TAG_PROJECTION_ENABLED, this.projectionEnabled);
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        this.projectionEnabled = input.getBooleanOr(TAG_PROJECTION_ENABLED, true);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
