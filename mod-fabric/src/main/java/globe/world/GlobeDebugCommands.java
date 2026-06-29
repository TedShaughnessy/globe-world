package globe.world;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import globe.world.config.GlobeConfig;
import globe.world.config.DayNightCycleMode;
import globe.world.config.GlobeSettings;
import globe.world.config.GameplaySettings;
import globe.world.config.PresentationSettings;
import globe.world.config.GlobeSettingsHolder;
import globe.world.config.TopologySettings;
import globe.world.diagnostics.DiagnosticsChannel;
import globe.world.diagnostics.GlobeDiagnostics;
import globe.world.entity.ActorLocalTargetView;
import globe.world.entity.ActorLocalTargets;
import globe.world.topology.TileGeometry;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import globe.world.util.EndPortalAvailability;
import globe.world.util.EndPortalProgressionState;
import globe.world.util.EntityCanonicalizer;
import globe.world.util.GlobeDayLength;
import globe.world.util.GlobeDistanceCaps;
import globe.world.util.GlobeInteractionPermissions;
import globe.world.network.GlobeWorldNetworking;
import globe.world.network.GlobeEntityAliasCommandPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class GlobeDebugCommands {
    private static final DynamicCommandExceptionType INVALID_DAY_NIGHT_MODE = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown day/night mode: " + value + " (expected vanilla or scrolling)"));
    private static final DynamicCommandExceptionType INVALID_DIAGNOSTICS_CHANNEL = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown diagnostics channel: " + value));

    private GlobeDebugCommands() {
    }

    @FunctionalInterface
    private interface SettingsUpdater {
        GlobeSettings apply(GlobeSettings settings) throws CommandSyntaxException;
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
                .then(Commands.literal("query_block")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> queryBlock(
                                        context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "pos")))))
                .then(Commands.literal("teleport_canon")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> teleportPlayerToCanonicalPosition(context.getSource())))
                .then(Commands.literal("teleport_border")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> teleportPlayerToNearestBorder(context.getSource(), 1))
                        .then(Commands.argument("inset", IntegerArgumentType.integer(0))
                                .executes(context -> teleportPlayerToNearestBorder(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "inset"))))
                        .then(Commands.literal("seam")
                                .then(Commands.argument("direction", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("a+", "a-", "b+", "b-", "c+", "c-"),
                                                builder))
                                        .executes(context -> teleportPlayerToSeam(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "direction"),
                                                1))
                                        .then(Commands.argument("inset", IntegerArgumentType.integer(0))
                                                .executes(context -> teleportPlayerToSeam(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "direction"),
                                                        IntegerArgumentType.getInteger(context, "inset")))))))
                .then(Commands.literal("teleport_alias")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("k", IntegerArgumentType.integer())
                                .then(Commands.argument("l", IntegerArgumentType.integer())
                                        .executes(context -> teleportPlayerToAlias(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "k"),
                                                IntegerArgumentType.getInteger(context, "l"))))))
                .then(Commands.literal("end_portal")
                        .executes(context -> printEndPortal(context.getSource(), false))
                        .then(Commands.literal("validate")
                                .executes(context -> printEndPortal(context.getSource(), true))))
                .then(Commands.literal("portal_scale")
                        .executes(context -> printPortalScale(context.getSource())))
                .then(clientCommands())
                .then(debugCommands())
                .then(configCommands());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clientCommands() {
        return Commands.literal("client")
                .then(Commands.literal("entity_aliases")
                        .executes(context -> sendEntityAliasClientCommand(
                                context.getSource(),
                                GlobeEntityAliasCommandPayload.Action.SHOW))
                        .then(Commands.literal("mode")
                                .executes(context -> sendEntityAliasClientCommand(
                                        context.getSource(),
                                        GlobeEntityAliasCommandPayload.Action.CYCLE_MODE)))
                        .then(Commands.literal("rings")
                                .executes(context -> sendEntityAliasClientCommand(
                                        context.getSource(),
                                        GlobeEntityAliasCommandPayload.Action.CYCLE_RINGS))));
    }

    private static int sendEntityAliasClientCommand(
            CommandSourceStack source,
            GlobeEntityAliasCommandPayload.Action action) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ServerPlayNetworking.canSend(player, GlobeEntityAliasCommandPayload.TYPE)) {
            source.sendFailure(Component.literal("This client does not support Globe World entity alias commands."));
            return 0;
        }

        ServerPlayNetworking.send(player, new GlobeEntityAliasCommandPayload(action));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugCommands() {
        return Commands.literal("debug")
                .executes(context -> listDiagnostics(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> listDiagnostics(context.getSource())))
                .then(Commands.literal("enable")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("channel", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(diagnosticsChannelNames(), builder))
                                .executes(context -> setDiagnosticsChannel(context, true))))
                .then(Commands.literal("disable")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("channel", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(diagnosticsChannelNames(), builder))
                                .executes(context -> setDiagnosticsChannel(context, false))))
                .then(Commands.literal("clear")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> clearDiagnostics(context.getSource())));
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
                                                settings -> settings.withPresentation(
                                                        settings.presentation().withCurvaturePercent(
                                                                IntegerArgumentType.getInteger(context, "percent"))),
                                                "Updated Overworld curvature"))))
                        .then(Commands.literal("nether_curvature")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withPresentation(
                                                        settings.presentation().withNetherCurvaturePercent(
                                                                IntegerArgumentType.getInteger(context, "percent"))),
                                                "Updated Nether curvature"))))
                        .then(Commands.literal("day_night")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                new String[]{"vanilla", "scrolling"}, builder))
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withGameplay(
                                                        settings.gameplay().withDayNightCycleMode(dayNightMode(context, "mode"))),
                                                "Updated day/night mode"))))
                        .then(Commands.literal("day_length")
                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(
                                                GameplaySettings.DAY_LENGTH_HALF_MULTIPLIER,
                                                GameplaySettings.DAY_LENGTH_MAX_MULTIPLIER))
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withGameplay(
                                                        settings.gameplay().withDayLengthMultiplier(
                                                                DoubleArgumentType.getDouble(context, "multiplier"))),
                                                "Updated day length"))))
                        .then(Commands.literal("allow_mobs_at_world_spawn")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withGameplay(
                                                        settings.gameplay().withAllowMobsAtWorldSpawn(
                                                                BoolArgumentType.getBool(context, "enabled"))),
                                                "Updated world-spawn mob spawning"))))
                        .then(Commands.literal("player_mob_spawn_exclusion")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(
                                                GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_MIN_BLOCKS,
                                                GameplaySettings.PLAYER_MOB_SPAWN_EXCLUSION_MAX_BLOCKS))
                                        .executes(context -> updateSettings(
                                                context.getSource(),
                                                settings -> settings.withGameplay(
                                                        settings.gameplay().withPlayerMobSpawnExclusionBlocks(
                                                                IntegerArgumentType.getInteger(context, "blocks"))),
                                                "Updated player mob-spawn exclusion")))));
    }

    private static int printPos(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        DimensionTiling tiling = topology.tiling();
        DimensionTiling overworldTiling = DimensionTiling.forDimension(Level.OVERWORLD);
        DimensionTiling netherTiling = DimensionTiling.forDimension(Level.NETHER);
        TopologySettings savedTopology = GlobeConfig.topologySettings();
        BlockPos pos = player.blockPosition();
        ChunkPos chunk = player.chunkPosition();
        BlockPos canonicalPos = topology.canonicalBlock(pos);
        ChunkPos canonicalChunk = topology.canonicalChunk(chunk);
        TileGeometry.LatticeCoordinate latticeCoordinate = topology.latticeCoordinate(chunk);
        ChunkPos latticeTranslation = topology.latticeTranslation(latticeCoordinate);
        TileGeometry.LatticeBasis latticeBasis = topology.latticeBasis();
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
                "Configured tiles: overworld=%s nether=%s nether_tile_size=%d portal_scale=%d/%d (%s)",
                tileSummary(overworldTiling),
                tileSummary(netherTiling),
                savedTopology.netherTileSize(),
                savedTopology.netherPortalScaleNumerator(),
                savedTopology.netherPortalScaleDenominator(),
                savedTopology.netherPortalScaleLabel())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "World block=%d %d %d canon block=%d %d %d",
                pos.getX(), pos.getY(), pos.getZ(),
                canonicalPos.getX(), canonicalPos.getY(), canonicalPos.getZ())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "World chunk=%d %d canon chunk=%d %d lattice=(%+d,%+d) translation=%+d %+d in canon tile=%s",
                chunk.x(), chunk.z(),
                canonicalChunk.x(), canonicalChunk.z(),
                latticeCoordinate.k(), latticeCoordinate.l(),
                latticeTranslation.x(), latticeTranslation.z(),
                yesNo(topology.isCanonical(pos)))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Geometry=%s basis A=%+d %+d B=%+d %+d",
                topology.geometryRevision(),
                latticeBasis.a().x(), latticeBasis.a().z(),
                latticeBasis.b().x(), latticeBasis.b().z())), false);
        topology.blendGeometry().ifPresent(blend ->
                source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "Ideal hex blend: width=%.1f blocks inradius=%.1f blocks signed_distance=%.1f blocks",
                        blend.blendWidth(),
                        blend.inradius(),
                        blend.signedDistance(pos.getX(), pos.getZ(), latticeCoordinate.k(), latticeCoordinate.l()))), false));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Longitude offset=%.1f ticks local_solar_day=%.1f ticks",
                CoordUtil.longitudeOffsetTicks(tiling, player.getX()),
                CoordUtil.localSolarDayTicks(player.level(), player.getX()))), false);
        return 1;
    }

    private static int printBorderDistance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        if (!topology.enabled()) {
            source.sendFailure(Component.literal("Current dimension is not tiled."));
            return 0;
        }

        Vec3 canonical = topology.canonicalBlock(player.position());
        TileGeometry.BoundaryHit nearest = topology.nearestBoundary(player.position());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Canonical X/Z=%.3f %.3f geometry=%s",
                canonical.x(),
                canonical.z(),
                topology.geometryRevision())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Distance to seams: %s; nearest=%s %.3f blocks at %.3f %.3f",
                seamDistanceSummary(topology, canonical),
                nearest.segment().outsideAlias().seamLabel(),
                nearest.distance(),
                nearest.boundaryX(),
                nearest.boundaryZ())), false);
        return 1;
    }

    private static int printEntity(CommandSourceStack source, Entity entity) {
        Entity root = entity.getRootVehicle();
        BlockPos pos = entity.blockPosition();
        ChunkPos chunk = entity.chunkPosition();
        TopologyContext topology = TopologyContexts.forLevel(entity.level());
        Vec3 canonicalPos = topology.canonicalBlock(entity.position());
        ChunkPos canonicalChunk = topology.canonicalChunk(chunk);

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
                canonicalPos.x(), canonicalPos.y(), canonicalPos.z(),
                canonicalChunk.x(), canonicalChunk.z(),
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
            ActorLocalTargetView targetView = ActorLocalTargets.view(mob, target);
            Vec3 alias = targetView.actorLocalPosition();
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
                    targetView.wrappedDistanceSqr(),
                    navigationTarget == null ? "none" : formatBlock(navigationTarget))), false);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "ActorLocalTargetView sameLevel=%s aliasing=%s canonical=%.3f %.3f %.3f localBox=%s",
                    yesNo(targetView.sameLevel()),
                    yesNo(targetView.aliasingEnabled()),
                    targetView.canonicalPosition().x(),
                    targetView.canonicalPosition().y(),
                    targetView.canonicalPosition().z(),
                    targetView.actorLocalBox())), false);
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

    private static int queryBlock(CommandSourceStack source, BlockPos rawPos) {
        ServerLevel level = source.getLevel();
        TopologyContext topology = TopologyContexts.forLevel(level);
        BlockPos canonicalPos = topology.canonicalBlock(rawPos);
        ChunkPos rawChunk = ChunkPos.containing(rawPos);
        ChunkPos canonicalChunk = ChunkPos.containing(canonicalPos);
        TileGeometry.LatticeCoordinate lattice = topology.latticeCoordinate(rawChunk);
        ChunkPos translation = topology.latticeTranslation(lattice);
        TopologyContext.AliasMutationAccess mutationAccess = topology.aliasMutationAccess(level, rawPos);
        LevelChunk loadedCanonicalChunk = level.getChunkSource().getChunkNow(canonicalChunk.x(), canonicalChunk.z());

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Globe World block query: dimension=%s topology=%s",
                level.dimension().identifier(),
                topology.enabled() ? "enabled" : "disabled")), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Raw block=%s chunk=%s",
                formatBlock(rawPos),
                formatChunk(rawChunk))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Canonical block=%s chunk=%s lattice=(%+d,%+d) translation=%+d %+d",
                formatBlock(canonicalPos),
                formatChunk(canonicalChunk),
                lattice.k(),
                lattice.l(),
                translation.x(),
                translation.z())), false);

        if (loadedCanonicalChunk == null) {
            source.sendSuccess(() -> Component.literal("Canonical chunk is not loaded; block state and block entity were not read."), false);
        } else {
            BlockState state = loadedCanonicalChunk.getBlockState(canonicalPos);
            BlockEntity blockEntity = loadedCanonicalChunk.getBlockEntity(canonicalPos);
            source.sendSuccess(() -> Component.literal("Canonical block state=" + state), false);
            source.sendSuccess(() -> Component.literal("Canonical block entity="
                    + (blockEntity == null ? "none" : blockEntity.typeHolder().getRegisteredName())), false);
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            List<ChunkPos> aliases = topology.loadedAliasesFor(player, canonicalChunk);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Loaded aliases for player=%s",
                    aliases.isEmpty() ? "none" : formatChunks(aliases))), false);
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Alias mutation access: allowed=%s alias=%s canonicalBlock=%s canonicalChunk=%s canonicalChunkBlockTicking=%s",
                yesNo(mutationAccess.allowed()),
                yesNo(mutationAccess.alias()),
                formatBlock(mutationAccess.canonicalBlock()),
                formatChunk(mutationAccess.canonicalChunk()),
                yesNo(level.shouldTickBlocksAt(mutationAccess.canonicalChunk().pack())))), false);

        Entity sourceEntity = source.getEntity();
        if (sourceEntity != null) {
            GlobeInteractionPermissions.PermissionView permission =
                    GlobeInteractionPermissions.inspect(level, sourceEntity, rawPos);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Permission: allowed=%s rawWorldBorder=%s spawnProtectedRaw=%s spawnProtectedCanonical=%s canonicalOwner=%s",
                    yesNo(permission.allowed()),
                    yesNo(permission.rawInsideWorldBorder()),
                    yesNo(permission.spawnProtectedAtRaw()),
                    yesNo(permission.spawnProtectedAtCanonical()),
                    formatBlock(permission.canonicalPos()))), false);
        }

        return 1;
    }

    private static int teleportPlayerToCanonicalPosition(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        if (!topology.enabled()) {
            source.sendFailure(Component.literal("Current dimension is not tiled."));
            return 0;
        }

        Vec3 canonical = topology.canonicalBlock(player.position());
        double oldX = player.getX();
        double oldZ = player.getZ();

        if (canonical.x() == oldX && canonical.z() == oldZ) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Already in canonical tile at %.3f %.3f %.3f",
                    player.getX(), player.getY(), player.getZ())), false);
            return 0;
        }

        if (!teleportPlayer(player, canonical.x(), canonical.y(), canonical.z())) {
            source.sendFailure(Component.literal("Canonical position is outside valid teleport bounds."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Teleported to canonical position %.3f %.3f %.3f from X/Z %.3f %.3f",
                canonical.x(), canonical.y(), canonical.z(), oldX, oldZ)), false);
        return 1;
    }

    private static int teleportPlayerToNearestBorder(CommandSourceStack source, int inset) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        if (!topology.enabled()) {
            source.sendFailure(Component.literal("Current dimension is not tiled."));
            return 0;
        }

        return teleportPlayerToBoundaryHit(
                source,
                player,
                topology,
                topology.nearestBoundary(player.position()),
                inset);
    }

    private static int teleportPlayerToSeam(
            CommandSourceStack source,
            String direction,
            int inset) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        if (!topology.enabled()) {
            source.sendFailure(Component.literal("Current dimension is not tiled."));
            return 0;
        }

        TileGeometry.LatticeCoordinate requested = seamCoordinate(direction);
        if (requested == null) {
            source.sendFailure(Component.literal(
                    "Unknown seam direction: " + direction + " (expected a+, a-, b+, b-, c+, or c-)"));
            return 0;
        }

        Vec3 canonical = topology.canonicalBlock(player.position());
        TileGeometry.BoundaryHit hit = topology.boundarySegments().stream()
                .filter(segment -> segment.outsideAlias().equals(requested))
                .map(segment -> segment.hitFrom(canonical))
                .min(java.util.Comparator.comparingDouble(TileGeometry.BoundaryHit::distance))
                .orElse(null);
        if (hit == null) {
            source.sendFailure(Component.literal(
                    "Seam " + requested.seamLabel() + " does not exist for " + topology.geometryRevision() + "."));
            return 0;
        }
        return teleportPlayerToBoundaryHit(source, player, topology, hit, inset);
    }

    private static int teleportPlayerToBoundaryHit(
            CommandSourceStack source,
            ServerPlayer player,
            TopologyContext topology,
            TileGeometry.BoundaryHit hit,
            int inset) {
        int clampedInset = Math.min(inset, topology.tileSizeBlocks() - 1);
        Vec3 target = hit.segment().insidePoint(player.getY(), clampedInset);
        if (!topology.isCanonical(BlockPos.containing(target))) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "Inset %d crosses outside the canonical mask at seam %s; use a smaller inset.",
                    clampedInset,
                    hit.segment().outsideAlias().seamLabel())));
            return 0;
        }
        if (!teleportPlayer(player, target.x(), target.y(), target.z())) {
            source.sendFailure(Component.literal("Seam test position is outside valid teleport bounds."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Teleported to seam %s inset=%d at %.3f %.3f %.3f",
                hit.segment().outsideAlias().seamLabel(),
                clampedInset,
                target.x(),
                target.y(),
                target.z())), false);
        return 1;
    }

    private static int teleportPlayerToAlias(CommandSourceStack source, int k, int l) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TopologyContext topology = TopologyContexts.forLevel(player.level());
        if (!topology.enabled()) {
            source.sendFailure(Component.literal("Current dimension is not tiled."));
            return 0;
        }

        TileGeometry.LatticeCoordinate coordinate = new TileGeometry.LatticeCoordinate(k, l);
        Vec3 canonical = topology.canonicalBlock(player.position());
        Vec3 target = topology.translatedAlias(canonical, coordinate);
        if (!teleportPlayer(player, target.x(), target.y(), target.z())) {
            source.sendFailure(Component.literal("Alias position is outside valid teleport bounds."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Teleported to lattice alias (%+d,%+d) at %.3f %.3f %.3f",
                k,
                l,
                target.x(),
                target.y(),
                target.z())), false);
        return 1;
    }

    private static boolean teleportPlayer(ServerPlayer player, double x, double y, double z) {
        boolean success = player.teleportTo(
                player.level(),
                x,
                y,
                z,
                Set.of(),
                player.getYRot(),
                player.getXRot(),
                true);
        if (success && !player.isFallFlying()) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            player.setOnGround(true);
        }
        return success;
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
        BlockPos forcedTarget = report.forcedStrongholdTarget();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Forced stronghold target=%s validated=%d valid=%d",
                forcedTarget == null ? "none" : formatBlock(forcedTarget),
                report.validatedForcedStartCount(),
                report.validForcedStartCount())), false);
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

    private static int printPortalScale(CommandSourceStack source) {
        TopologySettings settings = GlobeConfig.topologySettings();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Globe World Nether portal scale: %s, nether_to_overworld=%d/%d, overworld_to_nether=%d/%d",
                settings.netherPortalScaleLabel(),
                settings.netherPortalScaleNumerator(),
                settings.netherPortalScaleDenominator(),
                settings.netherPortalScaleDenominator(),
                settings.netherPortalScaleNumerator())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Nether tile: %s",
                tileSummary(DimensionTiling.forDimension(Level.NETHER)))), false);
        return 1;
    }

    private static int printConfig(CommandSourceStack source) {
        TopologySettings topology = GlobeConfig.topologySettings();
        PresentationSettings presentation = GlobeConfig.presentationSettings();
        GameplaySettings gameplay = GlobeConfig.gameplaySettings();
        DimensionTiling overworldTiling = DimensionTiling.forDimension(Level.OVERWORLD);
        DimensionTiling netherTiling = DimensionTiling.forDimension(Level.NETHER);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Globe World config version=%d",
                GlobeConfig.settingsVersion())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Overworld: mode=%s tile=%d chunks/%d blocks terrain=%s configured=%s curvature=%d%%",
                topology.mode().getSerializedName(),
                topology.tileSize(),
                topology.tileSize() * 16,
                overworldTiling.terrainMode().displayName(),
                topology.terrainMode().getSerializedName(),
                presentation.curvaturePercent())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Nether: mode=%s tile=%d chunks/%d blocks terrain=%s configured=%s curvature=%d%% portal_scale=%d/%d (%s)",
                topology.netherMode().getSerializedName(),
                topology.netherTileSize(),
                topology.netherTileSize() * 16,
                netherTiling.terrainMode().displayName(),
                topology.netherTerrainMode().getSerializedName(),
                presentation.netherCurvaturePercent(),
                topology.netherPortalScaleNumerator(),
                topology.netherPortalScaleDenominator(),
                topology.netherPortalScaleLabel())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Day/night: mode=%s day_length_multiplier=%.1f",
                gameplay.dayNightCycleMode().getSerializedName(),
                gameplay.dayLengthMultiplier())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Natural spawning: allow_mobs_at_world_spawn=%s player_mob_spawn_exclusion_blocks=%d",
                Boolean.toString(gameplay.allowMobsAtWorldSpawn()),
                gameplay.playerMobSpawnExclusionBlocks())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Forced progression structures: stronghold=%s nether_fortress=%s",
                yesNo(topology.forceMissingStronghold()),
                yesNo(topology.forceMissingNetherFortress()))), false);
        return 1;
    }

    private static int updateSettings(CommandSourceStack source, SettingsUpdater updater, String message)
            throws CommandSyntaxException {
        GlobeSettings oldGlobeSettings = GlobeConfig.globeSettings();
        GlobeSettings newGlobeSettings = updater.apply(oldGlobeSettings);
        if (newGlobeSettings.equals(oldGlobeSettings)) {
            source.sendSuccess(() -> Component.literal(message + ": already set"), false);
            return 0;
        }

        ((GlobeSettingsHolder) (Object) source.getServer().getWorldGenSettings()).globeWorld$setGlobeSettings(newGlobeSettings);
        source.getServer().getWorldGenSettings().setDirty();
        GlobeConfig.setGlobeSettings(newGlobeSettings);
        GlobeDayLength.applyToServer(source.getServer(), newGlobeSettings.gameplay());
        GlobeWorldNetworking.broadcastSettings(source.getServer(), newGlobeSettings);
        source.sendSuccess(() -> Component.literal(message + " and saved it to this world."), true);
        printConfig(source);
        source.sendSuccess(() -> Component.literal(
                "Note: existing terrain is not regenerated; connected Globe World clients were synced."),
                false);
        return 1;
    }

    private static int listDiagnostics(CommandSourceStack source) {
        StringBuilder channels = new StringBuilder();
        for (DiagnosticsChannel channel : DiagnosticsChannel.values()) {
            if (channels.length() > 0) {
                channels.append(", ");
            }
            channels.append(channelName(channel))
                    .append('=')
                    .append(GlobeDiagnostics.enabled(channel) ? "on" : "off");
        }
        source.sendSuccess(() -> Component.literal("Globe World diagnostics: " + channels), false);
        return 1;
    }

    private static int setDiagnosticsChannel(CommandContext<CommandSourceStack> context, boolean enabled)
            throws CommandSyntaxException {
        DiagnosticsChannel channel = diagnosticsChannel(context, "channel");
        GlobeDiagnostics.setEnabled(channel, enabled);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Globe World diagnostics %s %s",
                channelName(channel),
                enabled ? "enabled" : "disabled"
        )), true);
        return 1;
    }

    private static int clearDiagnostics(CommandSourceStack source) {
        GlobeDiagnostics.clear();
        source.sendSuccess(() -> Component.literal("Globe World diagnostics cleared."), true);
        return 1;
    }

    private static boolean isCanonical(Entity entity) {
        Vec3 canonical = TopologyContexts.forLevel(entity.level()).canonicalBlock(entity.position());
        return entity.getX() == canonical.x() && entity.getZ() == canonical.z();
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

    private static DiagnosticsChannel diagnosticsChannel(CommandContext<CommandSourceStack> context, String name)
            throws CommandSyntaxException {
        String value = StringArgumentType.getString(context, name).toUpperCase(Locale.ROOT);
        try {
            return DiagnosticsChannel.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw INVALID_DIAGNOSTICS_CHANNEL.create(StringArgumentType.getString(context, name));
        }
    }

    private static String[] diagnosticsChannelNames() {
        DiagnosticsChannel[] channels = DiagnosticsChannel.values();
        String[] names = new String[channels.length];
        for (int i = 0; i < channels.length; i++) {
            names[i] = channelName(channels[i]);
        }
        return names;
    }

    private static String channelName(DiagnosticsChannel channel) {
        return channel.name().toLowerCase(Locale.ROOT);
    }

    private static BlockPos canonicalBlockPos(Entity entity) {
        return TopologyContexts.forLevel(entity.level()).canonicalBlock(entity.blockPosition());
    }

    private static String formatBlock(BlockPos pos) {
        return String.format(Locale.ROOT, "%d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String formatChunk(ChunkPos pos) {
        return String.format(Locale.ROOT, "%d %d", pos.x(), pos.z());
    }

    private static String formatChunks(List<ChunkPos> positions) {
        StringBuilder result = new StringBuilder();
        for (ChunkPos pos : positions) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(formatChunk(pos));
        }
        return result.toString();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String emptyAsNone(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }

    private static String seamDistanceSummary(TopologyContext topology, Vec3 canonical) {
        Map<String, Double> minimums = new TreeMap<>();
        for (TileGeometry.BoundarySegment segment : topology.boundarySegments()) {
            minimums.merge(
                    segment.outsideAlias().seamLabel(),
                    segment.hitFrom(canonical).distance(),
                    Math::min);
        }
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Double> entry : minimums.entrySet()) {
            if (!summary.isEmpty()) {
                summary.append(' ');
            }
            summary.append(entry.getKey())
                    .append('=')
                    .append(String.format(Locale.ROOT, "%.3f", entry.getValue()));
        }
        return summary.toString();
    }

    private static TileGeometry.LatticeCoordinate seamCoordinate(String direction) {
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "a+", "+a" -> new TileGeometry.LatticeCoordinate(1, 0);
            case "a-", "-a" -> new TileGeometry.LatticeCoordinate(-1, 0);
            case "b+", "+b" -> new TileGeometry.LatticeCoordinate(0, 1);
            case "b-", "-b" -> new TileGeometry.LatticeCoordinate(0, -1);
            case "c+", "+c" -> new TileGeometry.LatticeCoordinate(1, -1);
            case "c-", "-c" -> new TileGeometry.LatticeCoordinate(-1, 1);
            default -> null;
        };
    }

    private static String tileSummary(DimensionTiling tiling) {
        if (!tiling.enabled()) {
            return "disabled";
        }
        return String.format(
                Locale.ROOT,
                "%s, %d chunks / %d blocks, %s terrain",
                tiling.mode().displayName(),
                tiling.tileSizeChunks(),
                tiling.tileSizeBlocks(),
                tiling.terrainMode().displayName()
        );
    }

}
