package globe.world.block.entity;

import globe.world.GlobeWorldBlocks;
import globe.world.atlas.GlobeAtlasLoadout;
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
    private static final String TAG_RADIUS_TIER = "atlas_radius_tier";
    private static final String TAG_EFFECTS = "atlas_effects";
    private static final String TAG_LEVEL_TWO_EFFECTS = "atlas_level_two_effects";
    private static final String TAG_TRAVEL_NETWORK = "atlas_travel_network";

    private boolean projectionEnabled = true;
    private GlobeAtlasLoadout loadout = GlobeAtlasLoadout.EMPTY;

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

    public GlobeAtlasLoadout loadout() {
        return this.loadout;
    }

    public void setLoadout(final GlobeAtlasLoadout loadout, final boolean edited) {
        GlobeAtlasLoadout sanitized = loadout == null ? GlobeAtlasLoadout.EMPTY : loadout;
        if (!sanitized.equals(this.loadout)) {
            this.loadout = sanitized;
            this.markModeChanged();
        } else if (edited) {
            this.setChanged();
        }
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
        output.putInt(TAG_RADIUS_TIER, this.loadout.radiusTier());
        output.putInt(TAG_EFFECTS, this.loadout.effectMask());
        output.putInt(TAG_LEVEL_TWO_EFFECTS, this.loadout.levelTwoMask());
        output.putBoolean(TAG_TRAVEL_NETWORK, this.loadout.travelNetwork());
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        this.projectionEnabled = input.getBooleanOr(TAG_PROJECTION_ENABLED, true);
        this.loadout = new GlobeAtlasLoadout(
                input.getIntOr(TAG_RADIUS_TIER, 0),
                input.getIntOr(TAG_EFFECTS, 0),
                input.getIntOr(TAG_LEVEL_TWO_EFFECTS, 0),
                input.getBooleanOr(TAG_TRAVEL_NETWORK, false));
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
