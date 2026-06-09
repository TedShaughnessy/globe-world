package globe.world.network;

public record PacketVirtualizationPolicy(
        Class<?> packetClass,
        PacketPolicyCategory category,
        String owner,
        String notes) {
}
