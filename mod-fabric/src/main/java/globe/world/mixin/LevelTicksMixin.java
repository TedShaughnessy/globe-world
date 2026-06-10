package globe.world.mixin;

import globe.world.topology.TopologyContexts;
import globe.world.util.DimensionTilingAware;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelTicks.class)
public class LevelTicksMixin<T> implements DimensionTilingAware {
    @Unique
    private ResourceKey<Level> globeWorld$dimension = Level.OVERWORLD;

    @SuppressWarnings("unchecked")
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void scheduleCanonicalTick(ScheduledTick<T> tick, CallbackInfo ci) {
        BlockPos wrapped = TopologyContexts.forDimension(this.globeWorld$dimension).canonicalBlock(tick.pos());
        if (wrapped.equals(tick.pos())) {
            return;
        }

        ScheduledTick<T> canonicalTick = new ScheduledTick<>(
                tick.type(),
                wrapped,
                tick.triggerTick(),
                tick.priority(),
                tick.subTickOrder());
        ((LevelTicks<T>) (Object) this).schedule(canonicalTick);
        ci.cancel();
    }

    @ModifyVariable(
        method = "hasScheduledTick",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private BlockPos hasScheduledTickCanonicalPos(BlockPos pos) {
        return TopologyContexts.forDimension(this.globeWorld$dimension).canonicalBlock(pos);
    }

    @ModifyVariable(
        method = "willTickThisTick",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private BlockPos willTickThisTickCanonicalPos(BlockPos pos) {
        return TopologyContexts.forDimension(this.globeWorld$dimension).canonicalBlock(pos);
    }

    @Override
    public void globeWorld$setDimension(ResourceKey<Level> dimension) {
        this.globeWorld$dimension = dimension;
    }

    @Override
    public ResourceKey<Level> globeWorld$getDimension() {
        return this.globeWorld$dimension;
    }
}
