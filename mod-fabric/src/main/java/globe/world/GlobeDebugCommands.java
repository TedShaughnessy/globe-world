package globe.world;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import globe.world.config.GlobeConfig;
import globe.world.config.DayNightCycleMode;
import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import globe.world.util.AiAliasUtil;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import globe.world.util.EndPortalAvailability;
import globe.world.util.EndPortalProgressionState;
import globe.world.util.EntityCanonicalizer;
import globe.world.util.GlobeDayLength;
import globe.world.util.GlobeDistanceCaps;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class GlobeDebugCommands {
    private static final DynamicCommandExceptionType INVALID_DAY_NIGHT_MODE = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown day/night mode: " + value + " (expected vanilla or scrolling)"));

    private GlobeDebugCommands() {
    }

    @FunctionalInterface
    private interface SettingsUpdater {
        TilingSettings apply(TilingSettings settings) throws CommandSyntaxException;
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(addCommonCommands(Commands.literal("globeworld")
                        .executes(context -> printPos(context.getSource())))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> addCommonCommands(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("pos")
                .executes(context -> printPos(context.getSource())))
                .then(Commands.literal("border_distance")
                        .executes(context -> printBorderDistance(context.getSource())))
                .then(Commands.literal("entity")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> printEntity(
                                        context.getSource(),
                                        EntityArgument.getEntity(context, "target")))))
                .then(Commands.literal("entities")
                        .executes(context -> printEntitySummary(context.getSource())))
                .then(Commands.literal("teleport_canon")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> teleportPlayerToCanonicalPosition(context.getSource())))
                .then(Commands.literal("teleport_border")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> teleportPlayerToNearestBorder(context.getSource(), 1))
                        .then(Commands.argument("inset", IntegerArgumentType.integer(0))
                                .executes(context -> teleportPlayerToNearestBorder(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "inset")))))
                .then(Commands.literal("teleport_alias")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("tileX", IntegerArgumentType.integer())
                                .then(Commands.argument("tileZ", IntegerArgumentType.integer())
                                        .executes(context -> teleportPlayerToAlias(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "tileX"),
                                                IntegerArgumentType.getInteger(context, "tileZ"))))))
                .then(Commands.literal("end_portal")
                        .executes(context -> printEndPortal(context.getSource(), false))
                        .then(Commands.literal("validate")
                                .executes(context -> printEndPortal(context.getSource(), true))))
                .then(configCommands());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> configCommands() {
        return Commands.literal("config")
                .executes(context -> printConfig(context.getSource()))
                .then(Commands.literal("show")
                        .executes(context -> printConfig(context.getSource())))
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("curvature")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withCurvaturePercent(
                                                        IntegerArgumentType.getInteger(context, "percent")),
                                                "Updated Overworld curvature"))))
                        .then(Commands.literal("nether_curvature")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withNetherCurvaturePercent(
                                                        IntegerArgumentType.getInteger(context, "percent")),
                                                "Updated Nether curvature"))))
                        .then(Commands.literal("day_night")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                new String[]{"vanilla", "scrolling"}, builder))
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withDayNightCycleMode(dayNightMode(context, "mode")),
                                                "Updated day/night mode"))))
                        .then(Commands.literal("day_length")
                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(
                                                TilingSettings.DAY_LENGTH_HALF_MULTIPLIER,
                                                TilingSettings.DAY_LENGTH_MAX_MULTIPLIER))
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withDayLengthMultiplier(
                                                        DoubleArgumentType.getDouble(context, "multiplier")),
                                                "Updated day length")))));
    }

    private static int printPos(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DimensionTiling tiling = DimensionTiling.forLevel(player.level());
        DimensionTiling overworldTiling = DimensionTiling.forDimension(Level.OVERWORLD);
        DimensionTiling netherTiling = DimensionTiling.forDimension(Level.NETHER);
        BlockPos pos = player.blockPosition();
        ChunkPos chunk = player.chunkPosition();
        int canonBlockX = CoordUtil.wrapBlock(tiling, pos.getX());
        int canonBlockZ = CoordUtil.wrapBlock(tiling, pos.getZ());
        int canonChunkX = CoordUtil.wrapChunk(tiling, chunk.x());
        int canonChunkZ = CoordUtil.wrapChunk(tiling, chunk.z());
        int configuredSimulationDistance = source.getServer().getPlayerList().getSimulationDistance();
        int effectiveSimulationDistance = GlobeDistanceCaps.effectiveSimulationDistance(tiling, configuredSimulationDistance);

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Globe World: dimension=%s current tile=%s",
                player.level().dimension().identifier(),
                tileSummary(tiling))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Server simulation distance: configured=%d effective=%d",
                configuredSimulationDistance,
                effectiveSimulationDistance)), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Configured tiles: overworld=%s nether=%s nether 1/8 requested/effective=%s/%s",
                tileSummary(overworldTiling),
                tileSummary(netherTiling),
                yesNo(GlobeConfig.netherOneEighthOverworldSize()),
                yesNo(GlobeConfig.effectiveNetherOneEighthOverworldSize()))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "World block=%d %d %d canon block=%d %d %d",
                pos.getX(), pos.getY(), pos.getZ(),
                canonBlockX, pos.getY(), canonBlockZ)), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "World chunk=%d %d canon chunk=%d %d tile alias=%+d %+d in canon tile=%s",
                chunk.x(), chunk.z(),
                canonChunkX, canonChunkZ,
                CoordUtil.tileAliasChunk(tiling, chunk.x()), CoordUtil.tileAliasChunk(tiling, chunk.z()),
                yesNo(CoordUtil.isInCanonicalTile(tiling, pos)))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Longitude offset=%.1f ticks local_solar_day=%.1f ticks",
                CoordUtil.longitudeOffsetTicks(tiling, player.getX()),
                CoordUtil.localSolarDayTicks(player.level(), player.getX()))), false);
        return 1;
    }

    private static int printBorderDistance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DimensionTiling tiling = DimensionTiling.forLevel(player.level());
        if (!tiling.enabled()) {
            source.sendFailure(Component.literal("Current dimension is not tiled."));
            return 0;
        }

        BorderDistances distances = borderDistances(tiling, player.getX(), player.getZ());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Canonical X/Z=%.3f %.3f tile=%d blocks",
                distances.canonicalX(),
                distances.canonicalZ(),
                tiling.tileSizeBlocks())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Distance to border: west=%.3f east=%.3f north=%.3f south=%.3f nearest=%s %.3f blocks",
                distances.west(),
                distances.east(),
                distances.north(),
                distances.south(),
                distances.nearestName(),
                distances.nearestDistance())), false);
        return 1;
    }

    private static int printEntity(CommandSourceStack source, Entity entity) {
        Entity root = entity.getRootVehicle();
        BlockPos pos = entity.blockPosition();
        ChunkPos chunk = entity.chunkPosition();
        DimensionTiling tiling = DimensionTiling.forLevel(entity.level());
        double canonX = CoordUtil.wrapBlock(tiling, entity.getX());
        double canonZ = CoordUtil.wrapBlock(tiling, entity.getZ());
        int canonChunkX = CoordUtil.wrapChunk(tiling, chunk.x());
        int canonChunkZ = CoordUtil.wrapChunk(tiling, chunk.z());

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Entity %d %s uuid=%s dimension=%s",
                entity.getId(),
                entity.typeHolder().getRegisteredName(),
                entity.getUUID(),
                entity.level().dimension().identifier())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Position=%.3f %.3f %.3f block=%d %d %d chunk=%d %d",
                entity.getX(), entity.getY(), entity.getZ(),
                pos.getX(), pos.getY(), pos.getZ(),
                chunk.x(), chunk.z())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Canonical position=%.3f %.3f %.3f canonical chunk=%d %d in canon tile=%s",
                canonX, entity.getY(), canonZ,
                canonChunkX, canonChunkZ,
                yesNo(isCanonical(entity)))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Canonicalized continuously=%s passenger=%s root=%d %s passengers=%d removed=%s",
                yesNo(EntityCanonicalizer.shouldCanonicalizeContinuously(entity)),
                yesNo(entity.isPassenger()),
                root.getId(),
                root.typeHolder().getRegisteredName(),
                root.getPassengers().size(),
                yesNo(entity.isRemoved()))), false);
        if (entity instanceof Mob mob && mob.getTarget() != null) {
            LivingEntity target = mob.getTarget();
            Vec3 alias = AiAliasUtil.nearestAliasPosition(mob, target);
            BlockPos navigationTarget = mob.getNavigation().getTargetPos();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Mob target=%d %s raw=%.3f %.3f %.3f alias=%.3f %.3f %.3f",
                    target.getId(),
                    target.typeHolder().getRegisteredName(),
                    target.getX(), target.getY(), target.getZ(),
                    alias.x, alias.y, alias.z)), false);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Target distance raw=%.3f wrapped=%.3f navigation target=%s",
                    mob.distanceToSqr(target),
                    AiAliasUtil.distanceToSqr(mob, target),
                    navigationTarget == null ? "none" : formatBlock(navigationTarget))), false);
        }
        return 1;
    }

    private static int printEntitySummary(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int total = 0;
        int eligible = 0;
        int nonCanonical = 0;
        int passengers = 0;
        StringBuilder examples = new StringBuilder();

        for (Entity entity : level.getAllEntities()) {
            total++;
            if (entity.isPassenger()) {
                passengers++;
            }
            if (!EntityCanonicalizer.shouldCanonicalizeContinuously(entity)) {
                continue;
            }

            eligible++;
            if (!isCanonical(entity)) {
                nonCanonical++;
                if (examples.length() < 1_000) {
                    if (examples.length() > 0) {
                        examples.append("; ");
                    }
                    examples.append(entity.getId())
                            .append(' ')
                            .append(entity.typeHolder().getRegisteredName())
                            .append(" @ ")
                            .append(formatBlock(entity.blockPosition()))
                            .append(" canon ")
                            .append(formatBlock(canonicalBlockPos(entity)));
                }
            }
        }

        int finalTotal = total;
        int finalEligible = eligible;
        int finalPassengers = passengers;
        int finalNonCanonical = nonCanonical;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Loaded entities in %s: total=%d eligible=%d passengers=%d outside canonical=%d",
                level.dimension().identifier(),
                finalTotal,
                finalEligible,
                finalPassengers,
                finalNonCanonical)), false);
        if (examples.length() > 0) {
            source.sendSuccess(() -> Component.literal("Outside canonical examples: " + examples), false);
        }
        return nonCanonical;
    }

    private static int teleportPlayerToCanonicalPosition(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DimensionTiling tiling = DimensionTiling.forLevel(player.level());
        double canonicalX = CoordUtil.wrapBlock(tiling, player.getX());
        double canonicalZ = CoordUtil.wrapBlock(tiling, player.getZ());
        double oldX = player.getX();
        double oldZ = player.getZ();

        if (canonicalX == oldX && canonicalZ == oldZ) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Already in canonical tile at %.3f %.3f %.3f",
                    player.getX(), player.getY(), player.getZ())), false);
            return 0;
        }

        player.connection.teleport(canonicalX, player.getY(), canonicalZ, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Teleported to canonical position %.3f %.3f %.3f from X/Z %.3f %.3f",
                canonicalX, player.getY(), canonicalZ, oldX, oldZ)), false);
        return 1;
    }

    private static int teleportPlayerToNearestBorder(CommandSourceStack source, int inset) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DimensionTiling tiling = DimensionTiling.forLevel(player.level());
        if (!tiling.enabled()) {
            source.sendFailure(Component.literal("Current dimension is not tiled."));
            return 0;
        }

        int clampedInset = Math.min(inset, tiling.tileSizeBlocks() - 1);
        BorderDistances distances = borderDistances(tiling, player.getX(), player.getZ());
        double targetX = distances.canonicalX();
        double targetZ = distances.canonicalZ();
        double min = tileMin(tiling);
        double max = tileMaxExclusive(tiling);
        switch (distances.nearestName()) {
            case "west" -> targetX = min + clampedInset;
            case "east" -> targetX = max - 1 - clampedInset;
            case "north" -> targetZ = min + clampedInset;
            case "south" -> targetZ = max - 1 - clampedInset;
            default -> {
            }
        }

        player.connection.teleport(targetX, player.getY(), targetZ, player.getYRot(), player.getXRot());
        double finalTargetX = targetX;
        double finalTargetZ = targetZ;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Teleported to %s border inset=%d at %.3f %.3f %.3f",
                distances.nearestName(),
                clampedInset,
                finalTargetX,
                player.getY(),
                finalTargetZ)), false);
        return 1;
    }

    private static int teleportPlayerToAlias(CommandSourceStack source, int tileX, int tileZ) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DimensionTiling tiling = DimensionTiling.forLevel(player.level());
        if (!tiling.enabled()) {
            source.sendFailure(Component.literal("Current dimension is not tiled."));
            return 0;
        }

        int period = tiling.tileSizeBlocks();
        double canonicalX = CoordUtil.wrapBlock(tiling, player.getX());
        double canonicalZ = CoordUtil.wrapBlock(tiling, player.getZ());
        double targetX = canonicalX + (double) tileX * period;
        double targetZ = canonicalZ + (double) tileZ * period;
        player.connection.teleport(targetX, player.getY(), targetZ, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Teleported to tile alias %+d %+d at %.3f %.3f %.3f",
                tileX,
                tileZ,
                targetX,
                player.getY(),
                targetZ)), false);
        return 1;
    }

    private static int printEndPortal(CommandSourceStack source, boolean validate) {
        ServerLevel currentLevel = source.getLevel();
        ServerLevel overworld = source.getServer().overworld();
        EndPortalAvailability.Report report = EndPortalAvailability.debugReport(overworld, validate);
        EndPortalProgressionState state = EndPortalProgressionState.get(overworld);
        state.recordClassification(report, GlobeConfig.settingsVersion());
        if (validate) {
            state.setLastValidationSummary(report.validationSummary());
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Globe World End portal: current_dimension=%s evaluating=%s validate=%s",
                currentLevel.dimension().identifier(),
                overworld.dimension().identifier(),
                yesNo(validate))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Tile=%d chunks / %d blocks enabled=%s generateStructures=%s",
                report.tileSizeChunks(),
                report.tileSizeBlocks(),
                yesNo(report.tilingEnabled()),
                yesNo(report.generateStructures()))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Policy=%s reason=%s",
                report.status().name(),
                report.reason())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Stronghold placements=%d raw_ring_candidates=%d canonical_owned=%d wrapped_aliases=%d",
                report.strongholdPlacementCount(),
                report.rawCandidateCount(),
                report.canonicalCandidateCount(),
                report.distinctWrappedAliasCount())), false);
        BlockPos fallback = state.fallbackPortalPos();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Fallback frame=%s eyes=%d mask=0x%03x saved_policy=%s saved_reason=%s saved_tile=%d settings_version=%d",
                fallback == null ? "none" : formatBlock(fallback),
                state.fallbackEyeMask() < 0 ? 0 : Integer.bitCount(state.fallbackEyeMask()),
                state.fallbackEyeMask() < 0 ? 0 : state.fallbackEyeMask(),
                emptyAsNone(state.lastClassification()),
                emptyAsNone(state.lastReason()),
                state.tileSizeChunks(),
                state.settingsVersion())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Validation=%s",
                validate ? report.validationSummary() : emptyAsNone(state.lastValidationSummary()))), false);
        if (validate) {
            source.sendSuccess(() -> Component.literal("Validation may load or generate STRUCTURE_STARTS chunks."), false);
        }
        return report.status().ordinal();
    }

    private static int printConfig(CommandSourceStack source) {
        TilingSettings settings = GlobeConfig.tilingSettings();
        DimensionTiling overworldTiling = DimensionTiling.forDimension(Level.OVERWORLD);
        DimensionTiling netherTiling = DimensionTiling.forDimension(Level.NETHER);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Globe World config version=%d",
                GlobeConfig.settingsVersion())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Overworld: mode=%s tile=%d chunks/%d blocks terrain=%s configured=%s curvature=%d%%",
                settings.mode().getSerializedName(),
                settings.tileSize(),
                settings.tileSize() * 16,
                overworldTiling.terrainMode().displayName(),
                settings.terrainMode().getSerializedName(),
                settings.curvaturePercent())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Nether: mode=%s tile=%d chunks/%d blocks terrain=%s configured=%s curvature=%d%% one_eighth requested/effective=%s/%s",
                settings.netherMode().getSerializedName(),
                settings.netherTileSize(),
                settings.netherTileSize() * 16,
                netherTiling.terrainMode().displayName(),
                settings.netherTerrainMode().getSerializedName(),
                settings.netherCurvaturePercent(),
                yesNo(settings.netherOneEighthOverworldSize()),
                yesNo(settings.effectiveNetherOneEighthOverworldSize()))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Day/night: mode=%s day_length_multiplier=%.1f",
                settings.dayNightCycleMode().getSerializedName(),
                settings.dayLengthMultiplier())), false);
        return 1;
    }

    private static int updateSettings(CommandSourceStack source, SettingsUpdater updater, String message)
            throws CommandSyntaxException {
        TilingSettings oldSettings = GlobeConfig.tilingSettings().sanitized();
        TilingSettings newSettings = updater.apply(oldSettings).sanitized();
        if (newSettings.equals(oldSettings)) {
            source.sendSuccess(() -> Component.literal(message + ": already set"), false);
            return 0;
        }

        ((TilingSettingsHolder) (Object) source.getServer().getWorldGenSettings()).globeWorld$setTilingSettings(newSettings);
        source.getServer().getWorldGenSettings().setDirty();
        GlobeConfig.setTilingSettings(newSettings);
        GlobeDayLength.applyToServer(source.getServer(), newSettings);
        source.sendSuccess(() -> Component.literal(message + " and saved it to this world."), true);
        printConfig(source);
        source.sendSuccess(() -> Component.literal(
                "Note: existing terrain is not regenerated; multiplayer clients may need matching client settings for client-only visuals."),
                false);
        return 1;
    }

    private static boolean isCanonical(Entity entity) {
        return entity.getX() == CoordUtil.wrapBlock(entity.level(), entity.getX())
                && entity.getZ() == CoordUtil.wrapBlock(entity.level(), entity.getZ());
    }

    private static DayNightCycleMode dayNightMode(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        String value = StringArgumentType.getString(context, name).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "vanilla" -> DayNightCycleMode.VANILLA;
            case "scrolling", "realistic" -> DayNightCycleMode.SCROLLING;
            default -> throw INVALID_DAY_NIGHT_MODE.create(value);
        };
    }

    private static BlockPos canonicalBlockPos(Entity entity) {
        return new BlockPos(
                CoordUtil.wrapBlock(entity.level(), entity.blockPosition().getX()),
                entity.blockPosition().getY(),
                CoordUtil.wrapBlock(entity.level(), entity.blockPosition().getZ()));
    }

    private static String formatBlock(BlockPos pos) {
        return String.format(Locale.ROOT, "%d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    private static BorderDistances borderDistances(DimensionTiling tiling, double x, double z) {
        double canonicalX = CoordUtil.wrapBlock(tiling, x);
        double canonicalZ = CoordUtil.wrapBlock(tiling, z);
        double min = tileMin(tiling);
        double max = tileMaxExclusive(tiling);
        double west = canonicalX - min;
        double east = max - canonicalX;
        double north = canonicalZ - min;
        double south = max - canonicalZ;
        String nearestName = "west";
        double nearestDistance = west;
        if (east < nearestDistance) {
            nearestName = "east";
            nearestDistance = east;
        }
        if (north < nearestDistance) {
            nearestName = "north";
            nearestDistance = north;
        }
        if (south < nearestDistance) {
            nearestName = "south";
            nearestDistance = south;
        }
        return new BorderDistances(canonicalX, canonicalZ, west, east, north, south, nearestName, nearestDistance);
    }

    private static double tileMin(DimensionTiling tiling) {
        return -tiling.tileSizeBlocks() / 2.0D;
    }

    private static double tileMaxExclusive(DimensionTiling tiling) {
        return tileMin(tiling) + tiling.tileSizeBlocks();
    }

    private static String tileSummary(DimensionTiling tiling) {
        if (!tiling.enabled()) {
            return "disabled";
        }
        return String.format(
                Locale.ROOT,
                "%d chunks / %d blocks, %s terrain",
                tiling.tileSizeChunks(),
                tiling.tileSizeBlocks(),
                tiling.terrainMode().displayName()
        );
    }

    private record BorderDistances(
            double canonicalX,
            double canonicalZ,
            double west,
            double east,
            double north,
            double south,
            String nearestName,
            double nearestDistance
    ) {
    }
}
