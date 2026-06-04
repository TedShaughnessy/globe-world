package globe.world.config;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum TilingMode implements StringRepresentable {
    DISABLED("disabled"),
    SQUARE("square");

    public static final Codec<TilingMode> CODEC = StringRepresentable.fromEnum(TilingMode::values);

    private final String serializedName;

    TilingMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String displayName() {
        return switch (this) {
            case DISABLED -> "Disabled";
            case SQUARE -> "Square";
        };
    }
}
