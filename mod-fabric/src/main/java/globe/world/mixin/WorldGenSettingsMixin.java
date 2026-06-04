package globe.world.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.config.TilingSettings;
import globe.world.config.TilingSettingsHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldGenSettings.class)
public class WorldGenSettingsMixin implements TilingSettingsHolder {
    @Mutable
    @Shadow
    @Final
    public static Codec<WorldGenSettings> CODEC;

    @Mutable
    @Shadow
    @Final
    public static SavedDataType<WorldGenSettings> TYPE;

    @Unique
    private TilingSettings globeWorld$tilingSettings = TilingSettings.DEFAULT;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void globeWorld$replaceCodec(CallbackInfo ci) {
        CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        WorldOptions.CODEC.forGetter(WorldGenSettings::options),
                        WorldDimensions.CODEC.forGetter(WorldGenSettings::dimensions),
                        TilingSettings.CODEC.optionalFieldOf("globe_world", TilingSettings.DEFAULT)
                                .forGetter(settings -> ((TilingSettingsHolder) (Object) settings).globeWorld$getTilingSettings())
                ).apply(instance, (options, dimensions, tilingSettings) -> {
                    WorldGenSettings settings = new WorldGenSettings(options, dimensions);
                    ((TilingSettingsHolder) (Object) settings).globeWorld$setTilingSettings(tilingSettings);
                    return settings;
                })
        );
        TYPE = new SavedDataType<>(
                Identifier.withDefaultNamespace("world_gen_settings"),
                () -> {
                    WorldGenSettings settings = new WorldGenSettings(
                            WorldOptions.defaultWithRandomSeed(),
                            new WorldDimensions(new java.util.HashMap<>())
                    );
                    ((TilingSettingsHolder) (Object) settings).globeWorld$setTilingSettings(TilingSettings.DEFAULT);
                    return settings;
                },
                CODEC,
                DataFixTypes.SAVED_DATA_WORLD_GEN_SETTINGS
        );
    }

    @Override
    public TilingSettings globeWorld$getTilingSettings() {
        return globeWorld$tilingSettings;
    }

    @Override
    public void globeWorld$setTilingSettings(TilingSettings settings) {
        globeWorld$tilingSettings = settings.sanitized();
    }
}
