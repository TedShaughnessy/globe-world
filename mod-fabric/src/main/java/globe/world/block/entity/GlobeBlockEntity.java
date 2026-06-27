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
    private static final String TAG_WRAP_AXIS = "wrap_axis";

    private WrapAxis wrapAxis = WrapAxis.X_MAJOR_Z_MINOR;

    public GlobeBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
        super(GlobeWorldBlocks.GLOBE_BLOCK_ENTITY, worldPosition, blockState);
    }

    public WrapAxis wrapAxis() {
        return this.wrapAxis;
    }

    public void cycleWrapAxis() {
        this.wrapAxis = this.wrapAxis.next();
        this.markModeChanged();
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
        output.putString(TAG_WRAP_AXIS, this.wrapAxis.serializedName);
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        this.wrapAxis = WrapAxis.byName(input.getStringOr(TAG_WRAP_AXIS, WrapAxis.X_MAJOR_Z_MINOR.serializedName));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public enum WrapAxis {
        X_MAJOR_Z_MINOR("x_major_z_minor"),
        Z_MAJOR_X_MINOR("z_major_x_minor");

        private final String serializedName;

        WrapAxis(final String serializedName) {
            this.serializedName = serializedName;
        }

        private WrapAxis next() {
            return this == X_MAJOR_Z_MINOR ? Z_MAJOR_X_MINOR : X_MAJOR_Z_MINOR;
        }

        private static WrapAxis byName(final String serializedName) {
            for (WrapAxis axis : values()) {
                if (axis.serializedName.equals(serializedName)) {
                    return axis;
                }
            }
            return X_MAJOR_Z_MINOR;
        }
    }
}
