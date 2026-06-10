package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GlobeSettings(
        TopologySettings topology,
        PresentationSettings presentation,
        GameplaySettings gameplay) {
    public static final GlobeSettings DEFAULT = new GlobeSettings(
            TopologySettings.DEFAULT,
            PresentationSettings.DEFAULT,
            GameplaySettings.DEFAULT
    );
    public static final Codec<GlobeSettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            TopologySettings.CODEC.fieldOf("topology").forGetter(GlobeSettings::topology),
                            PresentationSettings.CODEC.fieldOf("presentation").forGetter(GlobeSettings::presentation),
                            GameplaySettings.CODEC.fieldOf("gameplay").forGetter(GlobeSettings::gameplay)
                    ).apply(instance, GlobeSettings::new)
            );

    public GlobeSettings {
        topology = topology == null ? TopologySettings.DEFAULT : topology;
        presentation = presentation == null ? PresentationSettings.DEFAULT : presentation;
        gameplay = gameplay == null ? GameplaySettings.DEFAULT : gameplay;
    }

    public GlobeSettings withTopology(TopologySettings newTopology) {
        return new GlobeSettings(newTopology, presentation, gameplay);
    }

    public GlobeSettings withPresentation(PresentationSettings newPresentation) {
        return new GlobeSettings(topology, newPresentation, gameplay);
    }

    public GlobeSettings withGameplay(GameplaySettings newGameplay) {
        return new GlobeSettings(topology, presentation, newGameplay);
    }
}
