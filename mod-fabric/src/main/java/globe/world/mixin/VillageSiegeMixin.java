package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import globe.world.util.DimensionTiling;
import globe.world.util.GlobeSpawnFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VillageSiege.class)
public class VillageSiegeMixin {
    @WrapMethod(method = "findRandomSpawnPos")
    @Nullable
    private Vec3 findRandomSpawnPosAcrossSeams(
            ServerLevel level,
            BlockPos pos,
            Operation<Vec3> original) {
        if (DimensionTiling.forLevel(level).enabled()) {
            return GlobeSpawnFinder.findVillageSiegeSpawnPositionNear(level, pos, level.getRandom());
        }
        return original.call(level, pos);
    }
}
