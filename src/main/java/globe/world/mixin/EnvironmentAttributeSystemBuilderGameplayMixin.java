package globe.world.mixin;

import globe.world.util.GlobeLocalDaylight;
import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnvironmentAttributeSystem.Builder.class)
public class EnvironmentAttributeSystemBuilderGameplayMixin {
    @Inject(method = "addTimelineLayerForAttribute", at = @At("HEAD"), cancellable = true)
    private void globeWorld$replaceTimelineGameplayWithLocalLayer(
            Holder<Timeline> timeline,
            EnvironmentAttribute<?> attribute,
            ClockManager clockManager,
            CallbackInfo ci
    ) {
        EnvironmentAttributeSystem.Builder builder = (EnvironmentAttributeSystem.Builder) (Object) this;
        if (GlobeLocalDaylight.tryAddLocalGameplayLayer(builder, timeline, attribute, clockManager)) {
            ci.cancel();
        }
    }
}
