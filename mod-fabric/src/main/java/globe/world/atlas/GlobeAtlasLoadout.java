package globe.world.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record GlobeAtlasLoadout(int radiusTier, int effectMask, boolean travelNetwork) {
    public static final int MAX_RADIUS_TIER = 4;
    public static final GlobeAtlasLoadout EMPTY = new GlobeAtlasLoadout(0, 0, false);
    public static final Codec<GlobeAtlasLoadout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("radius_tier", 0).forGetter(GlobeAtlasLoadout::radiusTier),
            Codec.INT.optionalFieldOf("effects", 0).forGetter(GlobeAtlasLoadout::effectMask),
            Codec.BOOL.optionalFieldOf("travel_network", false).forGetter(GlobeAtlasLoadout::travelNetwork)
    ).apply(instance, GlobeAtlasLoadout::new));
    public static final StreamCodec<FriendlyByteBuf, GlobeAtlasLoadout> STREAM_CODEC = StreamCodec.ofMember(
            GlobeAtlasLoadout::write,
            GlobeAtlasLoadout::read);

    public GlobeAtlasLoadout {
        radiusTier = Mth.clamp(radiusTier, 0, MAX_RADIUS_TIER);
        effectMask = GlobeAtlasEffect.validMask(effectMask);
    }

    public int radiusBlocks() {
        return switch (this.radiusTier) {
            case 0 -> 32;
            case 1 -> 64;
            case 2 -> 128;
            case 3 -> 256;
            default -> 512;
        };
    }

    public int effectiveRadius(final GlobeDiscoveryRewards rewards) {
        return Math.min(this.radiusBlocks(), rewards.radiusCap());
    }

    public int cost() {
        return this.effectCount() * 2 + this.radiusTier + (this.travelNetwork ? 4 : 0);
    }

    public boolean active() {
        return this.cost() > 0;
    }

    public int effectCount() {
        return Integer.bitCount(this.effectMask);
    }

    public boolean hasEffect(final GlobeAtlasEffect effect) {
        return (this.effectMask & effect.mask()) != 0;
    }

    public GlobeAtlasLoadout withoutTravel() {
        return this.travelNetwork ? new GlobeAtlasLoadout(this.radiusTier, this.effectMask, false) : this;
    }

    private static GlobeAtlasLoadout read(final FriendlyByteBuf input) {
        return new GlobeAtlasLoadout(input.readVarInt(), input.readVarInt(), input.readBoolean());
    }

    private void write(final FriendlyByteBuf output) {
        output.writeVarInt(this.radiusTier);
        output.writeVarInt(this.effectMask);
        output.writeBoolean(this.travelNetwork);
    }
}
