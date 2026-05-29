package globe.world.mixin;

import globe.world.util.EntityCanonicalizer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void canonicalizeAfterTick(CallbackInfo ci) {
        ItemEntity item = (ItemEntity) (Object) this;
        if (!item.level().isClientSide() && !item.isRemoved()) {
            EntityCanonicalizer.canonicalize(item);
        }
    }
}
