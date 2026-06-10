package globe.world.util;

import globe.world.config.DayNightCycleMode;
import globe.world.config.GlobeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.EasingType;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.KeyframeTrackSampler;
import net.minecraft.util.Mth;
import net.minecraft.util.TriState;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeLayer;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.timeline.Timelines;

import java.util.Optional;
import java.util.OptionalLong;

public final class GlobeLocalDaylight {
    private static final int MONSTERS_STOP_BURNING_TICK = 12542;
    private static final int MONSTERS_START_BURNING_TICK = 23460;
    private static final KeyframeTrackSampler<Float> SKY_LIGHT_LEVEL_MULTIPLIER = skyLightLevelSampler();
    private static final KeyframeTrackSampler<Boolean> BEES_STAY_IN_HIVE = booleanSampler(keyframes -> keyframes
            .addKeyframe(12542, true)
            .addKeyframe(23460, false)
    );
    private static final KeyframeTrackSampler<Float> TURTLE_EGG_HATCH_CHANCE = constantFloatSampler(keyframes -> keyframes
            .addKeyframe(21062, 1.0F)
            .addKeyframe(21905, 0.002F)
    );
    private static final KeyframeTrackSampler<Float> CAT_WAKING_UP_GIFT_CHANCE = constantFloatSampler(keyframes -> keyframes
            .addKeyframe(362, 0.0F)
            .addKeyframe(23667, 0.7F)
    );
    private static final KeyframeTrackSampler<TriState> EYEBLOSSOM_OPEN = triStateSampler(keyframes -> keyframes
            .addKeyframe(12600, TriState.TRUE)
            .addKeyframe(23401, TriState.FALSE)
    );
    private static final KeyframeTrackSampler<Boolean> CREAKING_ACTIVE = booleanSampler(keyframes -> keyframes
            .addKeyframe(12600, true)
            .addKeyframe(23401, false)
    );
    private static final KeyframeTrackSampler<Boolean> MONSTERS_BURN = booleanSampler(keyframes -> keyframes
            .addKeyframe(12542, false)
            .addKeyframe(23460, true)
    );
    private static final KeyframeTrackSampler<Activity> VILLAGER_ACTIVITY = activitySampler(keyframes -> keyframes
            .addKeyframe(10, Activity.IDLE)
            .addKeyframe(2000, Activity.WORK)
            .addKeyframe(9000, Activity.MEET)
            .addKeyframe(11000, Activity.IDLE)
            .addKeyframe(12000, Activity.REST)
    );
    private static final KeyframeTrackSampler<Activity> BABY_VILLAGER_ACTIVITY = activitySampler(keyframes -> keyframes
            .addKeyframe(10, Activity.IDLE)
            .addKeyframe(3000, Activity.PLAY)
            .addKeyframe(6000, Activity.IDLE)
            .addKeyframe(10000, Activity.PLAY)
            .addKeyframe(12000, Activity.REST)
    );

    private GlobeLocalDaylight() {
    }

    public static boolean enabled(Level level) {
        return GlobeConfig.gameplaySettings().dayNightCycleMode() == DayNightCycleMode.SCROLLING
                && DimensionTiling.forLevel(level).enabled()
                && level.dimensionType().hasSkyLight()
                && !level.dimensionType().hasFixedTime();
    }

    public static boolean canSleep(BedRule rule, Level level, BlockPos pos) {
        if (!enabled(level)) {
            return rule.canSleep(level);
        }

        return switch (rule.canSleep()) {
            case ALWAYS -> true;
            case WHEN_DARK -> isDarkOutside(level, pos);
            case NEVER -> false;
        };
    }

    public static boolean isDarkOutside(Level level, BlockPos pos) {
        return enabled(level) ? !isBrightOutside(level, pos) : level.isDarkOutside();
    }

    public static boolean isBrightOutside(Level level, BlockPos pos) {
        return enabled(level) ? skyDarken(level, pos) < 4 : level.isBrightOutside();
    }

    public static int getMaxLocalRawBrightness(Level level, BlockPos pos) {
        return enabled(level) ? level.getMaxLocalRawBrightness(pos, skyDarken(level, pos)) : level.getMaxLocalRawBrightness(pos);
    }

    public static int skyDarken(Level level, BlockPos pos) {
        if (!enabled(level)) {
            return level.getSkyDarken();
        }

        long localDayTicks = (long) Math.floor(CoordUtil.localSolarDayTicks(level, pos));
        float skyLightLevel = 15.0F * SKY_LIGHT_LEVEL_MULTIPLIER.sample(localDayTicks);
        return (int) (15.0F - skyLightLevel);
    }

    public static float getLightLevelDependentMagicValue(Level level, BlockPos pos) {
        int brightness = enabled(level) ? getMaxLocalRawBrightness(level, pos) : level.getMaxLocalRawBrightness(pos);
        float value = brightness / 15.0F;
        float curvedValue = value / (4.0F - 3.0F * value);
        return Mth.lerp(level.dimensionType().ambientLight(), curvedValue, 1.0F);
    }

    public static boolean monstersBurn(Level level, Vec3 pos) {
        if (!enabled(level)) {
            return level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, pos);
        }

        double dayTicks = CoordUtil.localSolarDayTicks(level, pos.x);
        return dayTicks < MONSTERS_STOP_BURNING_TICK || dayTicks >= MONSTERS_START_BURNING_TICK;
    }

    public static boolean canPatrolSpawnAt(ServerLevel level, BlockPos pos) {
        return !enabled(level) || isBrightOutside(level, pos);
    }

    public static boolean tryAddLocalGameplayLayer(
            EnvironmentAttributeSystem.Builder builder,
            Holder<Timeline> timeline,
            EnvironmentAttribute<?> attribute,
            ClockManager clockManager
    ) {
        if (!enabledForTimelineLayers()) {
            return false;
        }

        if (timeline.is(Timelines.OVERWORLD_DAY)) {
            return tryAddLocalOverworldDayLayer(builder, timeline, attribute, clockManager);
        }
        if (timeline.is(Timelines.VILLAGER_SCHEDULE)) {
            return tryAddLocalVillagerScheduleLayer(builder, timeline, attribute, clockManager);
        }

        return false;
    }

    public static OptionalLong sleepWakeTime(ServerLevel level, Holder<WorldClock> clock) {
        if (!enabled(level)) {
            return OptionalLong.empty();
        }

        long worldTime = level.clockManager().getTotalTicks(clock);
        long largestAdvance = -1L;
        for (ServerPlayer player : level.players()) {
            if (!player.isSleeping()) {
                continue;
            }

            BlockPos pos = BlockPos.containing(player.position());
            if (isBrightOutside(level, pos)) {
                largestAdvance = Math.max(largestAdvance, 0L);
                continue;
            }

            double localDayTicks = CoordUtil.localSolarDayTicks(DimensionTiling.forLevel(level), worldTime, player.getX());
            long advance = ticksUntilNextMorning(localDayTicks);
            if (advance > largestAdvance) {
                largestAdvance = advance;
            }
        }

        return largestAdvance >= 0L ? OptionalLong.of(worldTime + largestAdvance) : OptionalLong.empty();
    }

    private static long ticksUntilNextMorning(double localDayTicks) {
        if (localDayTicks <= 0.0D) {
            return 0L;
        }

        return (long) Math.ceil(CoordUtil.MINECRAFT_DAY_TICKS - localDayTicks);
    }

    private static boolean enabledForTimelineLayers() {
        return DimensionTiling.currentOrOverworld().enabled();
    }

    private static boolean localTimelineActive() {
        return GlobeConfig.gameplaySettings().dayNightCycleMode() == DayNightCycleMode.SCROLLING
                && DimensionTiling.currentOrOverworld().enabled();
    }

    private static boolean tryAddLocalOverworldDayLayer(
            EnvironmentAttributeSystem.Builder builder,
            Holder<Timeline> timeline,
            EnvironmentAttribute<?> attribute,
            ClockManager clockManager
    ) {
        if (attribute == EnvironmentAttributes.BEES_STAY_IN_HIVE) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.BEES_STAY_IN_HIVE, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.BEES_STAY_IN_HIVE,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? baseValue || sample(BEES_STAY_IN_HIVE, timeline, clockManager, pos.x)
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.TURTLE_EGG_HATCH_CHANCE) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.TURTLE_EGG_HATCH_CHANCE, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.TURTLE_EGG_HATCH_CHANCE,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? Math.max(baseValue, sample(TURTLE_EGG_HATCH_CHANCE, timeline, clockManager, pos.x))
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.CAT_WAKING_UP_GIFT_CHANCE) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.CAT_WAKING_UP_GIFT_CHANCE, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.CAT_WAKING_UP_GIFT_CHANCE,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? Math.max(baseValue, sample(CAT_WAKING_UP_GIFT_CHANCE, timeline, clockManager, pos.x))
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.EYEBLOSSOM_OPEN) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.EYEBLOSSOM_OPEN, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.EYEBLOSSOM_OPEN,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? sample(EYEBLOSSOM_OPEN, timeline, clockManager, pos.x)
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.CREAKING_ACTIVE) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.CREAKING_ACTIVE, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.CREAKING_ACTIVE,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? baseValue || sample(CREAKING_ACTIVE, timeline, clockManager, pos.x)
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.MONSTERS_BURN) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.MONSTERS_BURN, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.MONSTERS_BURN,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? baseValue || sample(MONSTERS_BURN, timeline, clockManager, pos.x)
                            : baseValue
            );
            return true;
        }

        return false;
    }

    private static boolean tryAddLocalVillagerScheduleLayer(
            EnvironmentAttributeSystem.Builder builder,
            Holder<Timeline> timeline,
            EnvironmentAttribute<?> attribute,
            ClockManager clockManager
    ) {
        if (attribute == EnvironmentAttributes.VILLAGER_ACTIVITY) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.VILLAGER_ACTIVITY, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.VILLAGER_ACTIVITY,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? sample(VILLAGER_ACTIVITY, timeline, clockManager, pos.x)
                            : baseValue
            );
            return true;
        }
        if (attribute == EnvironmentAttributes.BABY_VILLAGER_ACTIVITY) {
            addVanillaTimelineLayer(builder, timeline, EnvironmentAttributes.BABY_VILLAGER_ACTIVITY, clockManager);
            builder.addPositionalLayer(
                    EnvironmentAttributes.BABY_VILLAGER_ACTIVITY,
                    (baseValue, pos, biomeInterpolator) -> localTimelineActive()
                            ? sample(BABY_VILLAGER_ACTIVITY, timeline, clockManager, pos.x)
                            : baseValue
            );
            return true;
        }

        return false;
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

    private static KeyframeTrackSampler<Float> skyLightLevelSampler() {
        KeyframeTrack.Builder<Float> builder = new KeyframeTrack.Builder<>();
        builder.addKeyframe(133, 1.0F)
                .addKeyframe(11867, 1.0F)
                .addKeyframe(13670, 0.26666668F)
                .addKeyframe(22330, 0.26666668F);
        return builder.build().bakeSampler(Optional.of(CoordUtil.MINECRAFT_DAY_TICKS), LerpFunction.ofFloat());
    }

    private static KeyframeTrackSampler<Float> constantFloatSampler(TrackBuilder<Float> trackBuilder) {
        KeyframeTrack.Builder<Float> builder = new KeyframeTrack.Builder<>();
        builder.setEasing(EasingType.CONSTANT);
        trackBuilder.accept(builder);
        return builder.build().bakeSampler(Optional.of(CoordUtil.MINECRAFT_DAY_TICKS), LerpFunction.ofFloat());
    }

    private static KeyframeTrackSampler<Boolean> booleanSampler(TrackBuilder<Boolean> trackBuilder) {
        KeyframeTrack.Builder<Boolean> builder = new KeyframeTrack.Builder<>();
        trackBuilder.accept(builder);
        return builder.build().bakeSampler(Optional.of(CoordUtil.MINECRAFT_DAY_TICKS), LerpFunction.ofConstant());
    }

    private static KeyframeTrackSampler<TriState> triStateSampler(TrackBuilder<TriState> trackBuilder) {
        KeyframeTrack.Builder<TriState> builder = new KeyframeTrack.Builder<>();
        trackBuilder.accept(builder);
        return builder.build().bakeSampler(Optional.of(CoordUtil.MINECRAFT_DAY_TICKS), LerpFunction.ofConstant());
    }

    private static KeyframeTrackSampler<Activity> activitySampler(TrackBuilder<Activity> trackBuilder) {
        KeyframeTrack.Builder<Activity> builder = new KeyframeTrack.Builder<>();
        trackBuilder.accept(builder);
        return builder.build().bakeSampler(Optional.of(CoordUtil.MINECRAFT_DAY_TICKS), LerpFunction.ofConstant());
    }

    @FunctionalInterface
    private interface TrackBuilder<T> {
        void accept(KeyframeTrack.Builder<T> builder);
    }
}
