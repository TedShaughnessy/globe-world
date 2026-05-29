package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(Player.class)
public class PlayerItemPickupMixin {

    @WrapOperation(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        )
    )
    private List<Entity> includeCanonicalPickupEntities(
            Level level,
            Entity except,
            AABB box,
            Operation<List<Entity>> original) {
        List<Entity> entities = original.call(level, except, box);
        AABB canonicalBox = CoordUtil.wrapAabb(level, box);
        if (canonicalBox == box) {
            return entities;
        }

        List<Entity> canonicalEntities = original.call(level, except, canonicalBox);
        if (canonicalEntities.isEmpty()) {
            return entities;
        }

        List<Entity> combined = new ArrayList<>(entities);
        for (Entity entity : canonicalEntities) {
            if (!combined.contains(entity)) {
                combined.add(entity);
            }
        }
        return combined;
    }
}
