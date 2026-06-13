package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ChestBlock.class)
public class ChestBlockCatQueryMixin {
    @WrapOperation(
            method = "isCatSittingOnChest",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private static List<Cat> getVisibleCatsOnChest(
            LevelAccessor level,
            Class<Cat> catClass,
            AABB box,
            Operation<List<Cat>> original,
            LevelAccessor requestedLevel,
            BlockPos pos) {
        return level instanceof Level realLevel
                ? TopologicalCollisionQueries.entitiesOfClass(realLevel, catClass, box, cat -> true)
                : original.call(level, catClass, box);
    }
}
