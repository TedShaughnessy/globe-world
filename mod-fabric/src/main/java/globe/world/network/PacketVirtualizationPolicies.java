package globe.world.network;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveMinecartPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;

import java.util.List;

public final class PacketVirtualizationPolicies {
    private static final List<PacketVirtualizationPolicy> POLICIES = List.of(
            policy(ClientboundLevelChunkWithLightPacket.class, PacketPolicyCategory.CHUNK_OWNER,
                    "PlayerChunkSenderMixin",
                    "Canonical chunk data is sent with the raw alias chunk header recorded in ChunkAliasTracker."),
            policy(ClientboundForgetLevelChunkPacket.class, PacketPolicyCategory.CHUNK_OWNER,
                    "PlayerChunkSenderMixin",
                    "Forget packets keep the raw alias chunk position and remove that alias tracker entry."),
            policy(ClientboundBlockUpdatePacket.class, PacketPolicyCategory.BLOCK_LOADED_ALIAS_FANOUT,
                    "BlockPacketUtil, ServerPlayerInteractionPacketMixin",
                    "Block updates are relabeled to loaded aliases, with nearest-alias fallback for targeted UI flows."),
            policy(ClientboundBlockEntityDataPacket.class, PacketPolicyCategory.BLOCK_LOADED_ALIAS_FANOUT,
                    "BlockPacketUtil",
                    "Block entity update position is relabeled for each loaded alias of the canonical owner."),
            policy(ClientboundSectionBlocksUpdatePacket.class, PacketPolicyCategory.BLOCK_LOADED_ALIAS_FANOUT,
                    "BlockPacketUtil",
                    "Section header is relabeled to every loaded alias while packed local block offsets stay unchanged."),
            policy(ClientboundLightUpdatePacket.class, PacketPolicyCategory.BLOCK_LOADED_ALIAS_FANOUT,
                    "BlockPacketUtil",
                    "Light chunk header is relabeled to loaded aliases, with nearest-alias fallback."),
            policy(ClientboundChunksBiomesPacket.class, PacketPolicyCategory.BLOCK_LOADED_ALIAS_FANOUT,
                    "ChunkPacketUtil",
                    "Biome resend data fans out to loaded aliases, with nearest-alias fallback."),
            policy(ClientboundSoundPacket.class, PacketPolicyCategory.WORLD_EVENT_NEAREST_ALIAS,
                    "WorldEventPacketUtil",
                    "Absolute sound coordinates are moved to the receiver's nearest visible alias."),
            policy(ClientboundSoundEntityPacket.class, PacketPolicyCategory.RELATIVE_OR_NON_POSITION_NOOP,
                    "WorldEventPacketUtil",
                    "Entity-attached sounds are currently recognized as handled but have no absolute coordinate relabeling."),
            policy(ClientboundLevelEventPacket.class, PacketPolicyCategory.WORLD_EVENT_NEAREST_ALIAS,
                    "WorldEventPacketUtil, ServerLevelWorldEventMixin",
                    "Level-event block positions are moved to the receiver's nearest visible alias."),
            policy(ClientboundBlockEventPacket.class, PacketPolicyCategory.WORLD_EVENT_LOADED_ALIAS_FANOUT,
                    "WorldEventPacketUtil",
                    "Block events fan out to loaded aliases and fall back to nearest-alias relabeling."),
            policy(ClientboundBlockDestructionPacket.class, PacketPolicyCategory.WORLD_EVENT_NEAREST_ALIAS,
                    "WorldEventPacketUtil, ServerLevelWorldEventMixin",
                    "Block break progress positions are moved to the receiver's nearest visible alias."),
            policy(ClientboundLevelParticlesPacket.class, PacketPolicyCategory.WORLD_EVENT_NEAREST_ALIAS,
                    "WorldEventPacketUtil",
                    "Particle origins are moved to the receiver's nearest visible alias."),
            policy(ClientboundExplodePacket.class, PacketPolicyCategory.WORLD_EVENT_NEAREST_ALIAS,
                    "WorldEventPacketUtil",
                    "Explosion centers are moved to the receiver's nearest visible alias while payload details remain unchanged."),
            policy(ClientboundBundlePacket.class, PacketPolicyCategory.ENTITY_NEAREST_ALIAS,
                    "EntityPacketUtil",
                    "Bundle contents are recursively virtualized when the utility handles a subpacket."),
            policy(ClientboundAddEntityPacket.class, PacketPolicyCategory.ENTITY_NEAREST_ALIAS,
                    "EntityPacketUtil",
                    "Spawn coordinates are moved to the receiver's nearest visible alias."),
            policy(ClientboundEntityPositionSyncPacket.class, PacketPolicyCategory.ENTITY_NEAREST_ALIAS,
                    "EntityPacketUtil, ChunkMapTrackedEntityMixin",
                    "Absolute sync coordinates are moved to the receiver's nearest visible alias."),
            policy(ClientboundTeleportEntityPacket.class, PacketPolicyCategory.ENTITY_NEAREST_ALIAS,
                    "EntityPacketUtil, ChunkMapTrackedEntityMixin",
                    "Absolute teleport axes are virtualized while relative X/Z axes keep vanilla meaning."),
            policy(ClientboundDamageEventPacket.class, PacketPolicyCategory.ENTITY_NEAREST_ALIAS,
                    "EntityPacketUtil",
                    "Optional damage source position is moved to the receiver's nearest visible alias."),
            policy(ClientboundMoveVehiclePacket.class, PacketPolicyCategory.ENTITY_NEAREST_ALIAS,
                    "EntityPacketUtil",
                    "Vehicle absolute correction position is moved to the receiver's nearest visible alias."),
            policy(ClientboundMoveMinecartPacket.class, PacketPolicyCategory.ENTITY_NEAREST_ALIAS,
                    "EntityPacketUtil",
                    "Minecart interpolation step positions are moved to the receiver's nearest visible alias."),
            policy(ClientboundMoveEntityPacket.class, PacketPolicyCategory.RELATIVE_OR_NON_POSITION_NOOP,
                    "ChunkMapTrackedEntityMixin",
                    "Relative movement packets stay vanilla; tile threshold crossings trigger absolute sync packets."),
            policy(ClientboundPlayerLookAtPacket.class, PacketPolicyCategory.NAVIGATION_UI_NEAREST_ALIAS,
                    "ServerPlayerInteractionPacketMixin",
                    "Look-at target coordinates are moved to the player's nearest visible alias."),
            policy(ClientboundOpenSignEditorPacket.class, PacketPolicyCategory.NAVIGATION_UI_NEAREST_ALIAS,
                    "ServerPlayerInteractionPacketMixin",
                    "Sign editor block position is moved to the player's nearest visible alias."),
            policy(ClientboundTrackedWaypointPacket.class, PacketPolicyCategory.NAVIGATION_UI_NEAREST_ALIAS,
                    "WaypointPacketUtil, waypoint mixins",
                    "Waypoint block/chunk/azimuth data uses the receiver's nearest visible alias and wrapped range checks."),
            policy(ClientboundMapItemDataPacket.class, PacketPolicyCategory.NAVIGATION_UI_NEAREST_ALIAS,
                    "MapItemMixin, MapItemSavedDataMixin",
                    "Map update source coordinates and player decorations use nearest map aliases before packet data is built.")
    );

    private PacketVirtualizationPolicies() {
    }

    public static List<PacketVirtualizationPolicy> all() {
        return POLICIES;
    }

    private static PacketVirtualizationPolicy policy(
            Class<?> packetClass,
            PacketPolicyCategory category,
            String owner,
            String notes) {
        return new PacketVirtualizationPolicy(packetClass, category, owner, notes);
    }
}
