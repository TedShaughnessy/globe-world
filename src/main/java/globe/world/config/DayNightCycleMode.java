package globe.world.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.StringRepresentable;

public enum DayNightCycleMode implements StringRepresentable {
    VANILLA("vanilla"),
    SCROLLING("scrolling");

    public static final Codec<DayNightCycleMode> CODEC = Codec.STRING.comapFlatMap(
            DayNightCycleMode::fromSerializedName,
            DayNightCycleMode::getSerializedName
    );

    private final String serializedName;

    DayNightCycleMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String displayName() {
        return switch (this) {
            case VANILLA -> "Vanilla";
            case SCROLLING -> "Scrolling";
        };
    }

    private static DataResult<DayNightCycleMode> fromSerializedName(String name) {
        return switch (name) {
            case "vanilla" -> DataResult.success(VANILLA);
            case "scrolling", "realistic" -> DataResult.success(SCROLLING);
            default -> DataResult.error(() -> "Unknown day/night cycle mode: " + name);
        };
    }
}
