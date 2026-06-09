package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GlobeSettings(
        TopologySettings topology,
        PresentationSettings presentation,
        GameplaySettings gameplay,
        DiagnosticsSettings diagnostics) {
    public static final GlobeSettings DEFAULT = from(TilingSettings.DEFAULT);
    public static final Codec<GlobeSettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            TopologySettings.CODEC.fieldOf("topology").forGetter(GlobeSettings::topology),
                            PresentationSettings.CODEC.fieldOf("presentation").forGetter(GlobeSettings::presentation),
                            GameplaySettings.CODEC.fieldOf("gameplay").forGetter(GlobeSettings::gameplay),
                            DiagnosticsSettings.CODEC.fieldOf("diagnostics").forGetter(GlobeSettings::diagnostics)
                    ).apply(instance, GlobeSettings::new)
            );

    public GlobeSettings {
        topology = topology == null ? TopologySettings.DEFAULT : topology;
        presentation = presentation == null ? PresentationSettings.DEFAULT : presentation;
        gameplay = gameplay == null ? GameplaySettings.DEFAULT : gameplay;
        diagnostics = diagnostics == null ? DiagnosticsSettings.DEFAULT : diagnostics;
    }

    public static GlobeSettings from(TilingSettings settings) {
        TilingSettings sanitized = settings.sanitized();
        return new GlobeSettings(
                TopologySettings.from(sanitized),
                PresentationSettings.from(sanitized),
                GameplaySettings.from(sanitized),
                DiagnosticsSettings.DEFAULT
        );
    }

    public TilingSettings toTilingSettings() {
        return topology.toTilingSettings(presentation, gameplay);
    }
}
