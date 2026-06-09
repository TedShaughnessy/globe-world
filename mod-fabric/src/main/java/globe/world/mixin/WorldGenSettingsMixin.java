package globe.world.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.config.GlobeSettings;
import globe.world.config.GlobeSettingsHolder;
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
public class WorldGenSettingsMixin implements GlobeSettingsHolder {
    @Mutable
    @Shadow
    @Final
    public static Codec<WorldGenSettings> CODEC;

    @Mutable
    @Shadow
    @Final
    public static SavedDataType<WorldGenSettings> TYPE;

    @Unique
    private GlobeSettings globeWorld$settings = GlobeSettings.DEFAULT;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void globeWorld$replaceCodec(CallbackInfo ci) {
        CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        WorldOptions.CODEC.forGetter(WorldGenSettings::options),
                        WorldDimensions.CODEC.forGetter(WorldGenSettings::dimensions),
                        GlobeSettings.CODEC.optionalFieldOf("globe_world", GlobeSettings.DEFAULT)
                                .forGetter(settings -> ((GlobeSettingsHolder) (Object) settings).globeWorld$getGlobeSettings())
                ).apply(instance, (options, dimensions, globeSettings) -> {
                    WorldGenSettings settings = new WorldGenSettings(options, dimensions);
                    ((GlobeSettingsHolder) (Object) settings).globeWorld$setGlobeSettings(globeSettings);
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
                    ((GlobeSettingsHolder) (Object) settings).globeWorld$setGlobeSettings(GlobeSettings.DEFAULT);
                    return settings;
                },
                CODEC,
                DataFixTypes.SAVED_DATA_WORLD_GEN_SETTINGS
        );
    }

    @Override
    public GlobeSettings globeWorld$getGlobeSettings() {
        return globeWorld$settings;
    }

    @Override
    public void globeWorld$setGlobeSettings(GlobeSettings settings) {
        globeWorld$settings = settings == null ? GlobeSettings.DEFAULT : settings;
    }
}
