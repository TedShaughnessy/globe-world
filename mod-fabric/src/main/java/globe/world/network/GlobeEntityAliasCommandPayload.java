package globe.world.network;

import globe.world.GlobeWorld;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public record GlobeEntityAliasCommandPayload(Action action) implements CustomPacketPayload {
    public static final Type<GlobeEntityAliasCommandPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "entity_alias_command"));
    public static final StreamCodec<FriendlyByteBuf, GlobeEntityAliasCommandPayload> CODEC =
            ByteBufCodecs.STRING_UTF8
                    .map(Action::fromSerializedName, Action::serializedName)
                    .map(GlobeEntityAliasCommandPayload::new, GlobeEntityAliasCommandPayload::action)
                    .mapStream(buffer -> buffer);

    public GlobeEntityAliasCommandPayload {
        action = action == null ? Action.SHOW : action;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        SHOW("show"),
        CYCLE_MODE("mode"),
        CYCLE_RINGS("rings");

        private final String serializedName;

        Action(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static Action fromSerializedName(String name) {
            String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
            for (Action action : values()) {
                if (action.serializedName.equals(normalized)) {
                    return action;
                }
            }
            return SHOW;
        }
    }
}
