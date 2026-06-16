package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PresentationSettings(int curvaturePercent, int netherCurvaturePercent) {
    public static final int CURVATURE_DISABLED_PERCENT = 0;
    public static final int CURVATURE_COMFORTABLE_PERCENT = 50;
    public static final int CURVATURE_REALISTIC_PERCENT = 100;
    public static final float CURVATURE_REALISTIC_SCALE = 12.0F;

    public static final PresentationSettings DEFAULT = new PresentationSettings(
            CURVATURE_DISABLED_PERCENT,
            CURVATURE_DISABLED_PERCENT
    );
    public static final PresentationSettings DISABLED = new PresentationSettings(
            CURVATURE_DISABLED_PERCENT,
            CURVATURE_DISABLED_PERCENT
    );
    public static final Codec<PresentationSettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.fieldOf("curvature_percent").forGetter(PresentationSettings::curvaturePercent),
                            Codec.INT.fieldOf("nether_curvature_percent").forGetter(PresentationSettings::netherCurvaturePercent)
                    ).apply(instance, PresentationSettings::new)
            );

    public PresentationSettings {
        curvaturePercent = sanitizeCurvaturePercent(curvaturePercent);
        netherCurvaturePercent = sanitizeCurvaturePercent(netherCurvaturePercent);
    }

    public PresentationSettings withCurvaturePercent(int newCurvaturePercent) {
        return new PresentationSettings(newCurvaturePercent, netherCurvaturePercent);
    }

    public PresentationSettings withNetherCurvaturePercent(int newNetherCurvaturePercent) {
        return new PresentationSettings(curvaturePercent, newNetherCurvaturePercent);
    }

    public static int sanitizeCurvaturePercent(int percent) {
        return Math.clamp(percent, CURVATURE_DISABLED_PERCENT, CURVATURE_REALISTIC_PERCENT);
    }

    public static float curvatureScaleFromPercent(int percent) {
        return sanitizeCurvaturePercent(percent) * CURVATURE_REALISTIC_SCALE / 100.0F;
    }
}
