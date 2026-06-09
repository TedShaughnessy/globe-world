package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PresentationSettings(int curvaturePercent, int netherCurvaturePercent) {
    public static final PresentationSettings DEFAULT = from(TilingSettings.DEFAULT);
    public static final Codec<PresentationSettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.fieldOf("curvature_percent").forGetter(PresentationSettings::curvaturePercent),
                            Codec.INT.fieldOf("nether_curvature_percent").forGetter(PresentationSettings::netherCurvaturePercent)
                    ).apply(instance, PresentationSettings::new)
            );

    public PresentationSettings {
        curvaturePercent = TilingSettings.sanitizeCurvaturePercent(curvaturePercent);
        netherCurvaturePercent = TilingSettings.sanitizeCurvaturePercent(netherCurvaturePercent);
    }

    public static PresentationSettings from(TilingSettings settings) {
        TilingSettings sanitized = settings.sanitized();
        return new PresentationSettings(sanitized.curvaturePercent(), sanitized.netherCurvaturePercent());
    }
}
