package globe.world.config;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DayNightCycleMode implements StringRepresentable {
    VANILLA("vanilla"),
    SCROLLING("scrolling"),
    REALISTIC("realistic");

    public static final Codec<DayNightCycleMode> CODEC = StringRepresentable.fromEnum(DayNightCycleMode::values);

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
            case REALISTIC -> "Realistic";
        };
    }
}
