package globe.world.util;

import globe.world.config.DayNightCycleMode;
import globe.world.config.GlobeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.KeyframeTrackSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalLong;

public final class GlobeLocalDaylight {
    private static final int MONSTERS_STOP_BURNING_TICK = 12542;
    private static final int MONSTERS_START_BURNING_TICK = 23460;
    private static final KeyframeTrackSampler<Float> SKY_LIGHT_LEVEL_MULTIPLIER = skyLightLevelSampler();

    private GlobeLocalDaylight() {
    }

    public static boolean enabled(Level level) {
        return GlobeConfig.dayNightCycleMode() == DayNightCycleMode.SCROLLING
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
        if (!enabled(level)) {
            return level.getLightLevelDependentMagicValue(pos);
        }

        float value = getMaxLocalRawBrightness(level, pos) / 15.0F;
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

    private static KeyframeTrackSampler<Float> skyLightLevelSampler() {
        KeyframeTrack.Builder<Float> builder = new KeyframeTrack.Builder<>();
        builder.addKeyframe(133, 1.0F)
                .addKeyframe(11867, 1.0F)
                .addKeyframe(13670, 0.26666668F)
                .addKeyframe(22330, 0.26666668F);
        return builder.build().bakeSampler(Optional.of(CoordUtil.MINECRAFT_DAY_TICKS), LerpFunction.ofFloat());
    }
}
