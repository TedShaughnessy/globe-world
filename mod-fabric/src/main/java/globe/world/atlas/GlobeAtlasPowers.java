package globe.world.atlas;

import globe.world.block.entity.GlobeBlockEntity;
import globe.world.map.GlobeMapSavedData;
import globe.world.network.GlobeAtlasScreenPayload;
import globe.world.network.GlobeAtlasTravelPayload;
import globe.world.network.GlobeAtlasUpdatePayload;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class GlobeAtlasPowers {
    private static final int EFFECT_INTERVAL_TICKS = 80;
    private static final int EFFECT_DURATION_TICKS = 180;
    private static final int TRAVEL_COOLDOWN_TICKS = 600;
    private static final Map<UUID, Long> LAST_TRAVEL_TICK = new HashMap<>();

    private GlobeAtlasPowers() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(GlobeAtlasUpdatePayload.TYPE, (payload, context) ->
                updateAtlas(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(GlobeAtlasTravelPayload.TYPE, (payload, context) ->
                travel(context.player(), payload.source(), payload.destination()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_TRAVEL_TICK.remove(handler.player.getUUID()));
    }

    public static void openScreen(final ServerPlayer player, final BlockPos rawPos) {
        if (!ServerPlayNetworking.canSend(player, GlobeAtlasScreenPayload.TYPE)) {
            player.sendSystemMessage(Component.literal("The Globe World client mod is required for Atlas power controls.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        sendScreen(player, rawPos);
    }

    public static void tickBlockEntity(
            final ServerLevel level,
            final BlockPos rawPos,
            final GlobeBlockEntity atlas) {
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        GlobeAtlasPowerState state = GlobeAtlasPowerState.get(level);
        state.entry(rawPos).ifPresentOrElse(entry -> {
            if (!entry.loadout().equals(atlas.loadout()) || !entry.name().equals(atlas.atlasName())) {
                state.update(level, rawPos, atlas.loadout(), atlas.atlasName(), false);
            }
        }, () -> state.update(level, rawPos, atlas.loadout(), atlas.atlasName(), false));

        if (level.getGameTime() % EFFECT_INTERVAL_TICKS != Math.floorMod(rawPos.asLong(), EFFECT_INTERVAL_TICKS)) {
            return;
        }

        GlobeAtlasLoadout loadout = atlas.loadout();
        if (loadout.effectMask() == 0) {
            return;
        }

        GlobeDiscoveryRewards rewards = GlobeDiscoveryRewards.get(level);
        if (rewards.radiusCap() <= 0 || !state.isPowered(rawPos, rewards)) {
            return;
        }

        int radius = loadout.effectiveRadius(rewards);
        if (radius <= 0) {
            return;
        }

        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        double centerX = CoordUtil.wrapBlock(tiling, rawPos.getX()) + 0.5D;
        double centerY = rawPos.getY() + 0.5D;
        double centerZ = CoordUtil.wrapBlock(tiling, rawPos.getZ()) + 0.5D;
        double radiusSqr = (double)radius * radius;
        for (ServerPlayer player : level.getPlayers(player -> !player.isSpectator() && player.isAlive())) {
            if (CoordUtil.wrappedDistanceSqr(tiling, player.getX(), player.getY(), player.getZ(), centerX, centerY, centerZ) > radiusSqr) {
                continue;
            }

            for (GlobeAtlasEffect effect : GlobeAtlasEffect.values()) {
                if (loadout.hasEffect(effect)) {
                    player.addEffect(new MobEffectInstance(effect.mobEffect(), EFFECT_DURATION_TICKS, loadout.amplifier(effect), true, true, true));
                }
            }
        }
    }

    private static void updateAtlas(final ServerPlayer player, final GlobeAtlasUpdatePayload payload) {
        BlockPos rawPos = payload.pos();
        if (!(player.level() instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        if (!(level.getBlockEntity(rawPos) instanceof GlobeBlockEntity atlas)) {
            return;
        }

        GlobeDiscoveryRewards rewards = GlobeDiscoveryRewards.get(level);
        GlobeAtlasPowerState state = GlobeAtlasPowerState.get(level);
        Optional<GlobeAtlasPowerState.Entry> currentEntry = state.entry(rawPos);
        GlobeAtlasLoadout currentLoadout = currentEntry
                .map(GlobeAtlasPowerState.Entry::loadout)
                .orElse(atlas.loadout());
        GlobeAtlasLoadout loadout = payload.loadout();
        if (!rewards.complete()) {
            loadout = loadout.withoutTravel();
        }
        int otherSpent = currentEntry.isPresent()
                ? Math.max(0, state.spentPoints() - currentLoadout.cost())
                : state.spentPoints();
        boolean affordable = otherSpent + loadout.cost() <= rewards.totalPoints();
        boolean reducingCost = loadout.cost() <= currentLoadout.cost();
        if (!affordable && !reducingCost) {
            sendScreen(player, rawPos);
            return;
        }

        BlockPos canonicalPos = CoordUtil.wrapBlockPos(DimensionTiling.forDimension(Level.OVERWORLD), rawPos);
        String name = sanitizeName(payload.name(), canonicalPos);
        atlas.setAtlasName(name);
        atlas.setProjectionEnabled(payload.projectionEnabled());
        atlas.setLoadout(loadout, true);
        state.update(level, rawPos, loadout, name, !loadout.equals(currentLoadout));
        sendScreen(player, rawPos);
    }

    private static void travel(final ServerPlayer player, final BlockPos sourceRawPos, final BlockPos destinationRawPos) {
        if (!(player.level() instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        long now = level.getGameTime();
        long lastTravel = LAST_TRAVEL_TICK.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        if (now - lastTravel < TRAVEL_COOLDOWN_TICKS) {
            int seconds = Math.max(1, (int)Math.ceil((TRAVEL_COOLDOWN_TICKS - (now - lastTravel)) / 20.0D));
            player.sendSystemMessage(Component.literal("Atlas travel recharging: " + seconds + "s").withStyle(ChatFormatting.GRAY), true);
            sendScreen(player, sourceRawPos);
            return;
        }

        TravelCheck check = checkTravel(level, player, sourceRawPos, destinationRawPos);
        if (!check.allowed()) {
            player.sendSystemMessage(Component.literal(check.message()).withStyle(ChatFormatting.RED), true);
            sendScreen(player, sourceRawPos);
            return;
        }

        Vec3 arrival = check.arrival();
        boolean teleported = player.teleportTo(level, arrival.x(), arrival.y(), arrival.z(), Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
        if (teleported) {
            LAST_TRAVEL_TICK.put(player.getUUID(), now);
            player.resetFallDistance();
        }
        sendScreen(player, destinationRawPos);
    }

    private static TravelCheck checkTravel(
            final ServerLevel level,
            final ServerPlayer player,
            final BlockPos sourceRawPos,
            final BlockPos destinationRawPos) {
        GlobeDiscoveryRewards rewards = GlobeDiscoveryRewards.get(level);
        if (!rewards.complete()) {
            return TravelCheck.denied("Mastered Atlas discovery is required.");
        }

        GlobeAtlasPowerState state = GlobeAtlasPowerState.get(level);
        Set<BlockPos> powered = state.poweredPositions(rewards);
        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        BlockPos sourcePos = CoordUtil.wrapBlockPos(tiling, sourceRawPos);
        BlockPos destinationPos = CoordUtil.wrapBlockPos(tiling, destinationRawPos);
        if (!powered.contains(sourcePos) || !powered.contains(destinationPos)) {
            return TravelCheck.denied("Both Atlases must be powered.");
        }

        Optional<GlobeAtlasPowerState.Entry> sourceEntry = state.entry(sourcePos);
        Optional<GlobeAtlasPowerState.Entry> destinationEntry = state.entry(destinationPos);
        if (sourceEntry.isEmpty() || destinationEntry.isEmpty()
                || !sourceEntry.get().loadout().travelNetwork()
                || !destinationEntry.get().loadout().travelNetwork()) {
            return TravelCheck.denied("Both Atlases must join the travel network.");
        }

        int sourceRadius = sourceEntry.get().loadout().effectiveRadius(rewards);
        double sourceDistanceSqr = CoordUtil.wrappedDistanceSqr(
                tiling,
                player.getX(),
                player.getY(),
                player.getZ(),
                sourcePos.getX() + 0.5D,
                sourcePos.getY() + 0.5D,
                sourcePos.getZ() + 0.5D);
        if (sourceDistanceSqr > (double)sourceRadius * sourceRadius) {
            return TravelCheck.denied("Move closer to the source Atlas.");
        }

        if (!(level.getBlockEntity(sourcePos) instanceof GlobeBlockEntity)
                || !(level.getBlockEntity(destinationPos) instanceof GlobeBlockEntity)) {
            return TravelCheck.denied("Both Atlases must be loaded.");
        }

        GlobeMapSavedData data = GlobeMapSavedData.getIfPresent(level, tiling);
        if (data == null || !data.isDiscoveredCanonicalBlock(tiling, destinationPos)) {
            return TravelCheck.denied("Destination Atlas is not discovered on the map.");
        }

        Vec3 arrival = findArrival(level, player, destinationPos);
        return arrival == null ? TravelCheck.denied("No safe arrival space near destination.") : TravelCheck.allowed(arrival);
    }

    private static void sendScreen(final ServerPlayer player, final BlockPos rawPos) {
        if (player.level() instanceof ServerLevel level && ServerPlayNetworking.canSend(player, GlobeAtlasScreenPayload.TYPE)) {
            ServerPlayNetworking.send(player, createScreenPayload(level, rawPos));
        }
    }

    private static GlobeAtlasScreenPayload createScreenPayload(final ServerLevel level, final BlockPos rawPos) {
        GlobeDiscoveryRewards rewards = GlobeDiscoveryRewards.get(level);
        GlobeAtlasPowerState state = GlobeAtlasPowerState.get(level);
        Optional<GlobeAtlasPowerState.Entry> entry = state.entry(rawPos);
        BlockPos canonicalPos = CoordUtil.wrapBlockPos(DimensionTiling.forDimension(Level.OVERWORLD), rawPos);
        BlockEntity blockEntity = level.getBlockEntity(rawPos);
        GlobeAtlasLoadout loadout = entry
                .map(GlobeAtlasPowerState.Entry::loadout)
                .orElseGet(() -> blockEntity instanceof GlobeBlockEntity atlas ? atlas.loadout() : GlobeAtlasLoadout.EMPTY);
        String name = blockEntity instanceof GlobeBlockEntity atlas
                ? atlasDisplayName(atlas.atlasName(), canonicalPos)
                : atlasDisplayName(entry.map(GlobeAtlasPowerState.Entry::name).orElse(""), canonicalPos);
        boolean projectionEnabled = !(blockEntity instanceof GlobeBlockEntity atlas) || atlas.projectionEnabled();
        int spentPoints = state.spentPoints();
        boolean powered = state.isPowered(rawPos, rewards);
        return new GlobeAtlasScreenPayload(
                canonicalPos,
                name,
                projectionEnabled,
                loadout,
                rewards.totalPoints(),
                rewards.radiusCap(),
                spentPoints,
                rewards.discoveredPixels(),
                rewards.discoveredPercent(),
                rewards.complete(),
                powered,
                destinations(level, rawPos, rewards, state));
    }

    private static List<GlobeAtlasScreenPayload.Destination> destinations(
            final ServerLevel level,
            final BlockPos sourceRawPos,
            final GlobeDiscoveryRewards rewards,
            final GlobeAtlasPowerState state) {
        if (!rewards.complete()) {
            return List.of();
        }

        Set<BlockPos> powered = state.poweredPositions(rewards);
        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        BlockPos sourcePos = CoordUtil.wrapBlockPos(tiling, sourceRawPos);
        List<GlobeAtlasScreenPayload.Destination> destinations = new ArrayList<>();
        for (GlobeAtlasPowerState.Entry entry : state.entries()) {
            BlockPos pos = entry.pos();
            if (pos.equals(sourcePos) || !entry.loadout().travelNetwork()) {
                continue;
            }

            boolean available = powered.contains(pos) && level.getBlockEntity(pos) instanceof GlobeBlockEntity;
            String name = level.getBlockEntity(pos) instanceof GlobeBlockEntity atlas ? atlas.atlasName() : entry.name();
            destinations.add(new GlobeAtlasScreenPayload.Destination(pos, available, atlasDisplayName(name, pos)));
        }
        return destinations;
    }

    private static Vec3 findArrival(final ServerLevel level, final ServerPlayer player, final BlockPos atlasPos) {
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(atlasPos.above());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = atlasPos.relative(direction);
            candidates.add(side);
            candidates.add(side.above());
        }
        candidates.add(atlasPos.above(2));

        for (BlockPos candidate : candidates) {
            if (isSafeArrival(level, player, candidate)) {
                return Vec3.atBottomCenterOf(candidate);
            }
        }
        return null;
    }

    private static boolean isSafeArrival(final ServerLevel level, final ServerPlayer player, final BlockPos feetPos) {
        BlockState feet = level.getBlockState(feetPos);
        BlockState head = level.getBlockState(feetPos.above());
        BlockPos belowPos = feetPos.below();
        BlockState below = level.getBlockState(belowPos);
        return feet.isAir()
                && head.isAir()
                && below.entityCanStandOnFace(level, belowPos, player, Direction.UP);
    }

    private static String label(final BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String atlasDisplayName(final String name, final BlockPos pos) {
        String sanitized = sanitizeName(name, pos);
        return sanitized.isEmpty() ? label(pos) : sanitized;
    }

    private static String sanitizeName(final String name, final BlockPos pos) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.equals(label(pos))) {
            return "";
        }
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private record TravelCheck(boolean allowed, String message, Vec3 arrival) {
        static TravelCheck allowed(final Vec3 arrival) {
            return new TravelCheck(true, "", arrival);
        }

        static TravelCheck denied(final String message) {
            return new TravelCheck(false, message, Vec3.ZERO);
        }
    }
}
