package globe.world.network;

import globe.world.GlobeWorld;
import globe.world.atlas.GlobeAtlasLoadout;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record GlobeAtlasScreenPayload(
        BlockPos pos,
        String name,
        boolean projectionEnabled,
        GlobeAtlasLoadout loadout,
        int worldPoints,
        int radiusCap,
        int spentPoints,
        int discoveredPixels,
        double discoveredPercent,
        boolean surveyMode,
        int biomesVisited,
        int visitedCells,
        int totalCells,
        List<Integer> milestoneTenths,
        boolean complete,
        boolean travelUnlocked,
        boolean powered,
        List<Destination> destinations) implements CustomPacketPayload {
    private static final int MAX_DESTINATIONS = 128;
    private static final int MAX_MILESTONES = 32;

    public static final Type<GlobeAtlasScreenPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_screen"));
    public static final StreamCodec<FriendlyByteBuf, GlobeAtlasScreenPayload> CODEC = StreamCodec.ofMember(
            GlobeAtlasScreenPayload::write,
            GlobeAtlasScreenPayload::read);

    public GlobeAtlasScreenPayload {
        name = truncate(name);
        milestoneTenths = milestoneTenths == null ? List.of() : List.copyOf(milestoneTenths);
        destinations = destinations == null ? List.of() : List.copyOf(destinations);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static GlobeAtlasScreenPayload read(final FriendlyByteBuf input) {
        BlockPos pos = input.readBlockPos();
        String name = input.readUtf(64);
        boolean projectionEnabled = input.readBoolean();
        GlobeAtlasLoadout loadout = GlobeAtlasLoadout.STREAM_CODEC.decode(input);
        int worldPoints = input.readVarInt();
        int radiusCap = input.readVarInt();
        int spentPoints = input.readVarInt();
        int discoveredPixels = input.readVarInt();
        double discoveredPercent = input.readDouble();
        boolean surveyMode = input.readBoolean();
        int biomesVisited = input.readVarInt();
        int visitedCells = input.readVarInt();
        int totalCells = input.readVarInt();
        int milestoneCount = Math.min(input.readVarInt(), MAX_MILESTONES);
        List<Integer> milestoneTenths = new ArrayList<>(milestoneCount);
        for (int i = 0; i < milestoneCount; i++) {
            milestoneTenths.add(input.readVarInt());
        }
        boolean complete = input.readBoolean();
        boolean travelUnlocked = input.readBoolean();
        boolean powered = input.readBoolean();
        int count = Math.min(input.readVarInt(), MAX_DESTINATIONS);
        List<Destination> destinations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            destinations.add(Destination.read(input));
        }
        return new GlobeAtlasScreenPayload(
                pos,
                name,
                projectionEnabled,
                loadout,
                worldPoints,
                radiusCap,
                spentPoints,
                discoveredPixels,
                discoveredPercent,
                surveyMode,
                biomesVisited,
                visitedCells,
                totalCells,
                milestoneTenths,
                complete,
                travelUnlocked,
                powered,
                destinations);
    }

    private void write(final FriendlyByteBuf output) {
        output.writeBlockPos(this.pos);
        output.writeUtf(this.name, 64);
        output.writeBoolean(this.projectionEnabled);
        GlobeAtlasLoadout.STREAM_CODEC.encode(output, this.loadout);
        output.writeVarInt(this.worldPoints);
        output.writeVarInt(this.radiusCap);
        output.writeVarInt(this.spentPoints);
        output.writeVarInt(this.discoveredPixels);
        output.writeDouble(this.discoveredPercent);
        output.writeBoolean(this.surveyMode);
        output.writeVarInt(this.biomesVisited);
        output.writeVarInt(this.visitedCells);
        output.writeVarInt(this.totalCells);
        output.writeVarInt(Math.min(this.milestoneTenths.size(), MAX_MILESTONES));
        for (int milestone : this.milestoneTenths.stream().limit(MAX_MILESTONES).toList()) {
            output.writeVarInt(milestone);
        }
        output.writeBoolean(this.complete);
        output.writeBoolean(this.travelUnlocked);
        output.writeBoolean(this.powered);
        output.writeVarInt(Math.min(this.destinations.size(), MAX_DESTINATIONS));
        for (Destination destination : this.destinations.stream().limit(MAX_DESTINATIONS).toList()) {
            destination.write(output);
        }
    }

    public record Destination(BlockPos pos, boolean available, String label) {
        public Destination {
            label = truncate(label);
        }

        private static Destination read(final FriendlyByteBuf input) {
            return new Destination(input.readBlockPos(), input.readBoolean(), input.readUtf(64));
        }

        private void write(final FriendlyByteBuf output) {
            output.writeBlockPos(this.pos);
            output.writeBoolean(this.available);
            output.writeUtf(this.label, 64);
        }
    }

    private static String truncate(final String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
