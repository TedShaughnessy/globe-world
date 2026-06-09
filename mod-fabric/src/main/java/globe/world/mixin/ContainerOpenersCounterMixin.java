package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ContainerOpenersCounter.class)
public class ContainerOpenersCounterMixin {
    @WrapOperation(
        method = "getEntitiesWithContainerOpen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private List<Entity> includeAliasNearbyPlayersWithOpenContainer(
            Level level,
            Entity except,
            AABB searchBox,
            Predicate<? super Entity> selector,
            Operation<List<Entity>> original) {
        List<Entity> entities = original.call(level, except, searchBox, selector);
        if (!(level instanceof ServerLevel serverLevel) || !DimensionTiling.forLevel(level).enabled()) {
            return entities;
        }

        for (ServerPlayer player : serverLevel.players()) {
            if (entities.contains(player)) {
                continue;
            }

            AABB virtualSearchBox = CoordUtil.virtualAabb(level, searchBox, player.getX(), player.getZ());
            if (virtualSearchBox.intersects(player.getBoundingBox()) && selector.test(player)) {
                entities.add(player);
            }
        }

        return entities;
    }
}
