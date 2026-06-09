package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GameplaySettings(DayNightCycleMode dayNightCycleMode, double dayLengthMultiplier) {
    public static final GameplaySettings DEFAULT = from(TilingSettings.DEFAULT);
    public static final Codec<GameplaySettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            DayNightCycleMode.CODEC.fieldOf("day_night_cycle").forGetter(GameplaySettings::dayNightCycleMode),
                            Codec.DOUBLE.fieldOf("day_length_multiplier").forGetter(GameplaySettings::dayLengthMultiplier)
                    ).apply(instance, GameplaySettings::new)
            );

    public GameplaySettings {
        dayNightCycleMode = dayNightCycleMode == null ? DayNightCycleMode.VANILLA : dayNightCycleMode;
        dayLengthMultiplier = TilingSettings.sanitizeDayLengthMultiplier(dayLengthMultiplier);
    }

    public static GameplaySettings from(TilingSettings settings) {
        TilingSettings sanitized = settings.sanitized();
        return new GameplaySettings(sanitized.dayNightCycleMode(), sanitized.dayLengthMultiplier());
    }

    public GameplaySettings withDayNightCycleMode(DayNightCycleMode newDayNightCycleMode) {
        return new GameplaySettings(newDayNightCycleMode, dayLengthMultiplier);
    }

    public GameplaySettings withDayLengthMultiplier(double newDayLengthMultiplier) {
        return new GameplaySettings(dayNightCycleMode, newDayLengthMultiplier);
    }
}
