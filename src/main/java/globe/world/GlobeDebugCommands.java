package globe.world;

import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import globe.world.util.EntityCanonicalizer;
import globe.world.util.AiAliasUtil;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
    private GlobeDebugCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("globeworld")
                        .then(Commands.literal("debug")
                                .then(Commands.literal("pos")
                                        .executes(context -> printPos(context.getSource())))
                                .then(Commands.literal("entity")
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(context -> printEntity(
                                                        context.getSource(),
                                                        EntityArgument.getEntity(context, "target")))))
                                .then(Commands.literal("entities")
                                        .executes(context -> printEntitySummary(context.getSource()))))));
    }

    private static int printPos(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
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

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Globe World: dimension=%s current tile=%s",
                player.level().dimension().identifier(),
                tileSummary(tiling))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Configured tiles: overworld=%s nether=%s nether 1/8 requested/effective=%s/%s",
                tileSummary(overworldTiling),
                tileSummary(netherTiling),
                yesNo(globe.world.config.GlobeConfig.netherOneEighthOverworldSize()),
                yesNo(globe.world.config.GlobeConfig.effectiveNetherOneEighthOverworldSize()))), false);
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

    private static boolean isCanonical(Entity entity) {
        return entity.getX() == CoordUtil.wrapBlock(entity.level(), entity.getX())
                && entity.getZ() == CoordUtil.wrapBlock(entity.level(), entity.getZ());
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
}
