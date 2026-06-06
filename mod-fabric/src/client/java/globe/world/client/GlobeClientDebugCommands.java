package globe.world.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import globe.world.util.GlobeEntityAliasMode;
import globe.world.util.GlobeEntityAliasing;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class GlobeClientDebugCommands {
    private GlobeClientDebugCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("globeworld")
                        .then(clientCommands())));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> clientCommands() {
        return ClientCommands.literal("client")
                .then(ClientCommands.literal("entity_aliases")
                        .executes(context -> printEntityAliasState(context.getSource()))
                        .then(ClientCommands.literal("mode")
                                .executes(context -> cycleEntityAliasMode(context.getSource())))
                        .then(ClientCommands.literal("rings")
                                .executes(context -> cycleEntityAliasRingLimit(context.getSource()))));
    }

    private static int printEntityAliasState(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("Globe entity aliases: "
                + GlobeEntityAliasing.mode().displayName()
                + ", rings "
                + GlobeEntityAliasing.maxAliasRingsDisplayName()));
        return 1;
    }

    private static int cycleEntityAliasMode(FabricClientCommandSource source) {
        GlobeEntityAliasMode mode = GlobeEntityAliasing.cycleMode();
        source.sendFeedback(Component.literal("Globe entity aliases: " + mode.displayName()));
        return 1;
    }

    private static int cycleEntityAliasRingLimit(FabricClientCommandSource source) {
        GlobeEntityAliasing.cycleMaxAliasRings();
        source.sendFeedback(Component.literal("Globe entity alias rings: "
                + GlobeEntityAliasing.maxAliasRingsDisplayName()));
        return 1;
    }
}
