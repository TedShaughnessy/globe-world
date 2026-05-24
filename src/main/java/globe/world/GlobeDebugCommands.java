package globe.world;

import globe.world.config.GlobeConfig;
import globe.world.util.CoordUtil;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Locale;

public final class GlobeDebugCommands {
    private GlobeDebugCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("globeworld")
                        .then(Commands.literal("debug")
                                .then(Commands.literal("pos")
                                        .executes(context -> printPos(context.getSource()))))));
    }

    private static int printPos(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = player.blockPosition();
        ChunkPos chunk = player.chunkPosition();
        int canonBlockX = CoordUtil.wrapBlock(pos.getX());
        int canonBlockZ = CoordUtil.wrapBlock(pos.getZ());
        int canonChunkX = CoordUtil.wrapChunk(chunk.x());
        int canonChunkZ = CoordUtil.wrapChunk(chunk.z());

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Globe World: enabled=%s tile=%d chunks / %d blocks",
                yesNo(GlobeConfig.enabled()),
                GlobeConfig.tileSizeChunks(),
                GlobeConfig.tileSizeBlocks())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "World block=%d %d %d canon block=%d %d %d",
                pos.getX(), pos.getY(), pos.getZ(),
                canonBlockX, pos.getY(), canonBlockZ)), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "World chunk=%d %d canon chunk=%d %d tile alias=%+d %+d in canon tile=%s",
                chunk.x(), chunk.z(),
                canonChunkX, canonChunkZ,
                CoordUtil.tileAliasChunk(chunk.x()), CoordUtil.tileAliasChunk(chunk.z()),
                yesNo(CoordUtil.isInCanonicalTile(pos)))), false);
        return 1;
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
