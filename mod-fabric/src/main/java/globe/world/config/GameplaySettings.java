package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GameplaySettings(
        DayNightCycleMode dayNightCycleMode,
        double dayLengthMultiplier,
        boolean allowMobsAtWorldSpawn,
        int playerMobSpawnExclusionBlocks,
        int wanderingTraderSpawnFrequencyMultiplier) {
    public static final double DAY_LENGTH_DEFAULT_MULTIPLIER = 1.0D;
    public static final double DAY_LENGTH_HALF_MULTIPLIER = 0.5D;
    public static final double DAY_LENGTH_MAX_MULTIPLIER = 10.0D;
    public static final boolean ALLOW_MOBS_AT_WORLD_SPAWN_DEFAULT = false;
    public static final int PLAYER_MOB_SPAWN_EXCLUSION_DEFAULT_BLOCKS = 24;
    public static final int PLAYER_MOB_SPAWN_EXCLUSION_MIN_BLOCKS = 4;
    public static final int PLAYER_MOB_SPAWN_EXCLUSION_MAX_BLOCKS = 24;
    public static final int WANDERING_TRADER_SPAWN_FREQUENCY_DEFAULT_MULTIPLIER = 1;
    public static final int WANDERING_TRADER_SPAWN_FREQUENCY_MIN_MULTIPLIER = 1;
    public static final int WANDERING_TRADER_SPAWN_FREQUENCY_MAX_MULTIPLIER = 32;

    public static final GameplaySettings DEFAULT = new GameplaySettings(
            DayNightCycleMode.VANILLA,
            DAY_LENGTH_DEFAULT_MULTIPLIER,
            ALLOW_MOBS_AT_WORLD_SPAWN_DEFAULT,
            PLAYER_MOB_SPAWN_EXCLUSION_DEFAULT_BLOCKS,
            WANDERING_TRADER_SPAWN_FREQUENCY_DEFAULT_MULTIPLIER
    );
    public static final Codec<GameplaySettings> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            DayNightCycleMode.CODEC.fieldOf("day_night_cycle").forGetter(GameplaySettings::dayNightCycleMode),
                            Codec.DOUBLE.fieldOf("day_length_multiplier").forGetter(GameplaySettings::dayLengthMultiplier),
                            Codec.BOOL.optionalFieldOf(
                                    "allow_mobs_at_world_spawn",
                                    ALLOW_MOBS_AT_WORLD_SPAWN_DEFAULT
                            ).forGetter(GameplaySettings::allowMobsAtWorldSpawn),
                            Codec.INT.optionalFieldOf(
                                    "player_mob_spawn_exclusion_blocks",
                                    PLAYER_MOB_SPAWN_EXCLUSION_DEFAULT_BLOCKS
                            ).forGetter(GameplaySettings::playerMobSpawnExclusionBlocks),
                            Codec.INT.optionalFieldOf(
                                    "wandering_trader_spawn_frequency_multiplier",
                                    WANDERING_TRADER_SPAWN_FREQUENCY_DEFAULT_MULTIPLIER
                            ).forGetter(GameplaySettings::wanderingTraderSpawnFrequencyMultiplier)
                    ).apply(instance, GameplaySettings::new)
            );

    public GameplaySettings {
        dayNightCycleMode = dayNightCycleMode == null ? DayNightCycleMode.VANILLA : dayNightCycleMode;
        dayLengthMultiplier = sanitizeDayLengthMultiplier(dayLengthMultiplier);
        playerMobSpawnExclusionBlocks = sanitizePlayerMobSpawnExclusionBlocks(playerMobSpawnExclusionBlocks);
        wanderingTraderSpawnFrequencyMultiplier =
                sanitizeWanderingTraderSpawnFrequencyMultiplier(wanderingTraderSpawnFrequencyMultiplier);
    }

    public GameplaySettings withDayNightCycleMode(DayNightCycleMode newDayNightCycleMode) {
        return new GameplaySettings(
                newDayNightCycleMode,
                dayLengthMultiplier,
                allowMobsAtWorldSpawn,
                playerMobSpawnExclusionBlocks,
                wanderingTraderSpawnFrequencyMultiplier);
    }

    public GameplaySettings withDayLengthMultiplier(double newDayLengthMultiplier) {
        return new GameplaySettings(
                dayNightCycleMode,
                newDayLengthMultiplier,
                allowMobsAtWorldSpawn,
                playerMobSpawnExclusionBlocks,
                wanderingTraderSpawnFrequencyMultiplier);
    }

    public GameplaySettings withAllowMobsAtWorldSpawn(boolean newAllowMobsAtWorldSpawn) {
        return new GameplaySettings(
                dayNightCycleMode,
                dayLengthMultiplier,
                newAllowMobsAtWorldSpawn,
                playerMobSpawnExclusionBlocks,
                wanderingTraderSpawnFrequencyMultiplier);
    }

    public GameplaySettings withPlayerMobSpawnExclusionBlocks(int newPlayerMobSpawnExclusionBlocks) {
        return new GameplaySettings(
                dayNightCycleMode,
                dayLengthMultiplier,
                allowMobsAtWorldSpawn,
                newPlayerMobSpawnExclusionBlocks,
                wanderingTraderSpawnFrequencyMultiplier);
    }

    public GameplaySettings withWanderingTraderSpawnFrequencyMultiplier(int newWanderingTraderSpawnFrequencyMultiplier) {
        return new GameplaySettings(
                dayNightCycleMode,
                dayLengthMultiplier,
                allowMobsAtWorldSpawn,
                playerMobSpawnExclusionBlocks,
                newWanderingTraderSpawnFrequencyMultiplier);
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

    public static int sanitizePlayerMobSpawnExclusionBlocks(int blocks) {
        return Math.clamp(
                blocks,
                PLAYER_MOB_SPAWN_EXCLUSION_MIN_BLOCKS,
                PLAYER_MOB_SPAWN_EXCLUSION_MAX_BLOCKS);
    }

    public static int sanitizeWanderingTraderSpawnFrequencyMultiplier(int multiplier) {
        int clamped = Math.clamp(
                multiplier,
                WANDERING_TRADER_SPAWN_FREQUENCY_MIN_MULTIPLIER,
                WANDERING_TRADER_SPAWN_FREQUENCY_MAX_MULTIPLIER);
        int lower = Integer.highestOneBit(clamped);
        int upper = Math.min(lower << 1, WANDERING_TRADER_SPAWN_FREQUENCY_MAX_MULTIPLIER);
        return clamped - lower < upper - clamped ? lower : upper;
    }
}
