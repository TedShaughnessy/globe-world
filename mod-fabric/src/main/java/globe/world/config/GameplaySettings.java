package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GameplaySettings(DayNightCycleMode dayNightCycleMode, double dayLengthMultiplier) {
    public static final double DAY_LENGTH_DEFAULT_MULTIPLIER = 1.0D;
    public static final double DAY_LENGTH_HALF_MULTIPLIER = 0.5D;
    public static final double DAY_LENGTH_MAX_MULTIPLIER = 10.0D;

    public static final GameplaySettings DEFAULT = new GameplaySettings(
            DayNightCycleMode.VANILLA,
            DAY_LENGTH_DEFAULT_MULTIPLIER
    );
    public static final Codec<GameplaySettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            DayNightCycleMode.CODEC.fieldOf("day_night_cycle").forGetter(GameplaySettings::dayNightCycleMode),
                            Codec.DOUBLE.fieldOf("day_length_multiplier").forGetter(GameplaySettings::dayLengthMultiplier)
                    ).apply(instance, GameplaySettings::new)
            );

    public GameplaySettings {
        dayNightCycleMode = dayNightCycleMode == null ? DayNightCycleMode.VANILLA : dayNightCycleMode;
        dayLengthMultiplier = sanitizeDayLengthMultiplier(dayLengthMultiplier);
    }

    public GameplaySettings withDayNightCycleMode(DayNightCycleMode newDayNightCycleMode) {
        return new GameplaySettings(newDayNightCycleMode, dayLengthMultiplier);
    }

    public GameplaySettings withDayLengthMultiplier(double newDayLengthMultiplier) {
        return new GameplaySettings(dayNightCycleMode, newDayLengthMultiplier);
    }

    public static double sanitizeDayLengthMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier)) {
            return DAY_LENGTH_DEFAULT_MULTIPLIER;
        }
        if (multiplier <= 0.75D) {
            return DAY_LENGTH_HALF_MULTIPLIER;
        }
        return Math.clamp(Math.rint(multiplier), DAY_LENGTH_DEFAULT_MULTIPLIER, DAY_LENGTH_MAX_MULTIPLIER);
    }
}
