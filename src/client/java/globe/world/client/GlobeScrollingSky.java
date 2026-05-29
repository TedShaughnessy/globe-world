package globe.world.client;

import globe.world.config.DayNightCycleMode;
import globe.world.config.GlobeConfig;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.core.Holder;
import net.minecraft.util.ARGB;
import net.minecraft.util.EasingType;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.KeyframeTrackSampler;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeLayer;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.timeline.Timelines;

import java.util.Optional;

public final class GlobeScrollingSky {
    // Mirrors the vanilla Overworld day visual tracks, sampled at local camera X.
    private static final EasingType SKY_ANGLE_EASING = EasingType.symmetricCubicBezier(0.362F, 0.241F);
    private static final KeyframeTrackSampler<Float> SUN_ANGLE = floatSampler(
            SKY_ANGLE_EASING,
            keyframes -> keyframes.addKeyframe(6000, 360.0F).addKeyframe(6000, 0.0F),
            LerpFunction.ofFloat()
    );
    private static final KeyframeTrackSampler<Float> MOON_ANGLE = floatSampler(
            SKY_ANGLE_EASING,
            keyframes -> keyframes.addKeyframe(6000, 540.0F).addKeyframe(6000, 180.0F),
            LerpFunction.ofFloat()
    );
    private static final KeyframeTrackSampler<Float> STAR_ANGLE = SUN_ANGLE;
    private static final KeyframeTrackSampler<Integer> SKY_COLOR_MULTIPLIER = colorSampler(keyframes -> keyframes
            .addKeyframe(133, -1)
            .addKeyframe(11867, -1)
            .addKeyframe(13670, -16777216)
            .addKeyframe(22330, -16777216)
    );
    private static final KeyframeTrackSampler<Integer> SKY_LIGHT_COLOR_MULTIPLIER = colorSampler(keyframes -> keyframes
            .addKeyframe(730, -1)
            .addKeyframe(11270, -1)
            .addKeyframe(13140, ARGB.colorFromFloat(1.0F, 0.48F, 0.48F, 1.0F))
            .addKeyframe(22860, ARGB.colorFromFloat(1.0F, 0.48F, 0.48F, 1.0F))
    );
    private static final KeyframeTrackSampler<Float> SKY_LIGHT_FACTOR = floatSampler(keyframes -> keyframes
            .addKeyframe(730, 1.0F)
            .addKeyframe(11270, 1.0F)
            .addKeyframe(13140, 0.24F)
            .addKeyframe(22860, 0.24F)
    );
    private static final KeyframeTrackSampler<Integer> SUNRISE_SUNSET_COLOR = colorSampler(keyframes -> keyframes
            .addKeyframe(71, 1609540403)
            .addKeyframe(310, 703969843)
            .addKeyframe(565, 117167155)
            .addKeyframe(730, 16770355)
            .addKeyframe(11270, 16770355)
            .addKeyframe(11397, 83679283)
            .addKeyframe(11522, 268028723)
            .addKeyframe(11690, 703969843)
            .addKeyframe(11929, 1609540403)
            .addKeyframe(12243, -1310226637)
            .addKeyframe(12358, -857440717)
            .addKeyframe(12512, -371166669)
            .addKeyframe(12613, -153261261)
            .addKeyframe(12732, -19242189)
            .addKeyframe(12841, -19440589)
            .addKeyframe(13035, -321760973)
            .addKeyframe(13252, -1043577037)
            .addKeyframe(13775, 918435635)
            .addKeyframe(13888, 532362547)
            .addKeyframe(14039, 163001139)
            .addKeyframe(14192, 11744051)
            .addKeyframe(21807, 11678515)
            .addKeyframe(21961, 163001139)
            .addKeyframe(22112, 532362547)
            .addKeyframe(22225, 918435635)
            .addKeyframe(22748, -1043577037)
            .addKeyframe(22965, -321760973)
            .addKeyframe(23159, -19440589)
            .addKeyframe(23272, -19242189)
            .addKeyframe(23488, -371166669)
            .addKeyframe(23642, -857440717)
            .addKeyframe(23757, -1310226637)
    );
    private static final KeyframeTrackSampler<Float> STAR_BRIGHTNESS = floatSampler(keyframes -> keyframes
            .addKeyframe(92, 0.037F)
            .addKeyframe(627, 0.0F)
            .addKeyframe(11373, 0.0F)
            .addKeyframe(11732, 0.016F)
            .addKeyframe(11959, 0.044F)
            .addKeyframe(12399, 0.143F)
            .addKeyframe(12729, 0.258F)
            .addKeyframe(13228, 0.5F)
            .addKeyframe(22772, 0.5F)
            .addKeyframe(23032, 0.364F)
            .addKeyframe(23356, 0.225F)
            .addKeyframe(23758, 0.101F)
    );

    private GlobeScrollingSky() {
    }

    public static boolean tryAddLocalVisualLayer(
            EnvironmentAttributeSystem.Builder builder,
            Holder<Timeline> timeline,
            EnvironmentAttribute<?> attribute,
            ClockManager clockManager
    ) {
        if (!timelineLayersEnabled() || !timeline.is(Timelines.OVERWORLD_DAY)) {
            return false;
        }

        if (attribute == EnvironmentAttributes.SUN_ANGLE) {
            addFloatOverride(builder, EnvironmentAttributes.SUN_ANGLE, timeline, clockManager, SUN_ANGLE);
            return true;
        }
        if (attribute == EnvironmentAttributes.MOON_ANGLE) {
            addFloatOverride(builder, EnvironmentAttributes.MOON_ANGLE, timeline, clockManager, MOON_ANGLE);
            return true;
        }
        if (attribute == EnvironmentAttributes.STAR_ANGLE) {
            addFloatOverride(builder, EnvironmentAttributes.STAR_ANGLE, timeline, clockManager, STAR_ANGLE);
            return true;
        }
        if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.STAR_BRIGHTNESS, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.STAR_BRIGHTNESS,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? Math.max(baseValue, sample(STAR_BRIGHTNESS, timeline, clockManager, pos.x))
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
            addColorOverride(builder, EnvironmentAttributes.SUNRISE_SUNSET_COLOR, timeline, clockManager, SUNRISE_SUNSET_COLOR);
            return true;
        }
        if (attribute == EnvironmentAttributes.SKY_COLOR) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.SKY_COLOR, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.SKY_COLOR,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? ARGB.multiply(baseValue, sample(SKY_COLOR_MULTIPLIER, timeline, clockManager, pos.x))
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.SKY_LIGHT_FACTOR) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.SKY_LIGHT_FACTOR, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.SKY_LIGHT_FACTOR,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? baseValue * sample(SKY_LIGHT_FACTOR, timeline, clockManager, pos.x)
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.SKY_LIGHT_COLOR) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.SKY_LIGHT_COLOR, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.SKY_LIGHT_COLOR,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? ARGB.multiply(baseValue, sample(SKY_LIGHT_COLOR_MULTIPLIER, timeline, clockManager, pos.x))
                            : baseValue
            );
            return true;
        }

        return false;
    }

    private static boolean timelineLayersEnabled() {
        return GlobeConfig.enabled();
    }

    private static boolean localTimelineActive() {
        return GlobeConfig.enabled() && GlobeConfig.dayNightCycleMode() == DayNightCycleMode.SCROLLING;
    }

    private static void addFloatOverride(
            EnvironmentAttributeSystem.Builder builder,
            EnvironmentAttribute<Float> attribute,
            Holder<Timeline> timeline,
            ClockManager clockManager,
            KeyframeTrackSampler<Float> sampler
    ) {
        addVanillaTimelineLayer(builder, timeline, attribute, clockManager);
        builder.addPositionalLayer(attribute, (baseValue, pos, biomeInterpolator) ->
                localTimelineActive() ? sample(sampler, timeline, clockManager, pos.x) : baseValue);
    }

    private static void addColorOverride(
            EnvironmentAttributeSystem.Builder builder,
            EnvironmentAttribute<Integer> attribute,
            Holder<Timeline> timeline,
            ClockManager clockManager,
            KeyframeTrackSampler<Integer> sampler
    ) {
        addVanillaTimelineLayer(builder, timeline, attribute, clockManager);
        builder.addPositionalLayer(attribute, (baseValue, pos, biomeInterpolator) ->
                localTimelineActive() ? sample(sampler, timeline, clockManager, pos.x) : baseValue);
    }

    private static <T> void addVanillaTimelineLayer(
            EnvironmentAttributeSystem.Builder builder,
            Holder<Timeline> timeline,
            EnvironmentAttribute<T> attribute,
            ClockManager clockManager
    ) {
        EnvironmentAttributeLayer.TimeBased<T> vanillaLayer = timeline.value().createTrackSampler(attribute, clockManager);
        builder.addTimeBasedLayer(attribute, (baseValue, cacheTickId) ->
                localTimelineActive() ? baseValue : vanillaLayer.applyTimeBased(baseValue, cacheTickId));
    }

    private static <T> T sample(KeyframeTrackSampler<T> sampler, Holder<Timeline> timeline, ClockManager clockManager, double x) {
        long worldTime = clockManager.getTotalTicks(timeline.value().clock());
        long localDayTicks = (long) Math.floor(CoordUtil.localSolarDayTicks(DimensionTiling.currentOrOverworld(), worldTime, x));
        return sampler.sample(localDayTicks);
    }

    private static KeyframeTrackSampler<Float> floatSampler(TrackBuilder<Float> trackBuilder) {
        return floatSampler(null, trackBuilder, LerpFunction.ofFloat());
    }

    private static KeyframeTrackSampler<Float> floatSampler(
            EasingType easing,
            TrackBuilder<Float> trackBuilder,
            LerpFunction<Float> lerp
    ) {
        KeyframeTrack.Builder<Float> builder = new KeyframeTrack.Builder<>();
        if (easing != null) {
            builder.setEasing(easing);
        }
        trackBuilder.accept(builder);
        return builder.build().bakeSampler(Optional.of(CoordUtil.MINECRAFT_DAY_TICKS), lerp);
    }

    private static KeyframeTrackSampler<Integer> colorSampler(TrackBuilder<Integer> trackBuilder) {
        KeyframeTrack.Builder<Integer> builder = new KeyframeTrack.Builder<>();
        trackBuilder.accept(builder);
        return builder.build().bakeSampler(Optional.of(CoordUtil.MINECRAFT_DAY_TICKS), LerpFunction.ofColor());
    }

    @FunctionalInterface
    private interface TrackBuilder<T> {
        void accept(KeyframeTrack.Builder<T> builder);
    }
}
