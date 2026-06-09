package globe.world.config;

import com.mojang.serialization.Codec;
import globe.world.diagnostics.DiagnosticsChannel;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record DiagnosticsSettings(Set<DiagnosticsChannel> enabledByDefault) {
    private static final Codec<DiagnosticsChannel> CHANNEL_CODEC = Codec.STRING.xmap(
            name -> DiagnosticsChannel.valueOf(name.toUpperCase(Locale.ROOT)),
            channel -> channel.name().toLowerCase(Locale.ROOT)
    );

    public static final DiagnosticsSettings DEFAULT = new DiagnosticsSettings(Set.of());
    public static final Codec<DiagnosticsSettings> CODEC = CHANNEL_CODEC.listOf()
            .fieldOf("enabled_by_default")
            .codec()
            .xmap(DiagnosticsSettings::fromList, settings -> List.copyOf(settings.enabledByDefault()));

    public DiagnosticsSettings {
        enabledByDefault = copy(enabledByDefault);
    }

    private static DiagnosticsSettings fromList(List<DiagnosticsChannel> channels) {
        return new DiagnosticsSettings(copy(channels));
    }

    private static Set<DiagnosticsChannel> copy(Iterable<DiagnosticsChannel> channels) {
        EnumSet<DiagnosticsChannel> copy = EnumSet.noneOf(DiagnosticsChannel.class);
        for (DiagnosticsChannel channel : channels) {
            copy.add(channel);
        }
        return Set.copyOf(copy);
    }
}
