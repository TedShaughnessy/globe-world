package globe.world.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import globe.world.topology.TopologicalCollisionQueries;
import globe.world.util.CoordUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ExperienceOrb.class)
public class ExperienceOrbAliasMixin {
    @WrapOperation(
        method = "followNearbyPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"
        )
    )
    private double useWrappedFollowerDistance(Player player, Entity entity, Operation<Double> original) {
        return CoordUtil.wrappedDistanceSqr(
                player.level(),
                player.getX(),
                player.getY(),
                player.getZ(),
                entity.getX(),
                entity.getY(),
                entity.getZ());
    }

    @WrapOperation(
        method = "followNearbyPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getX()D"
        )
    )
    private double useNearestAliasPlayerX(Player player, Operation<Double> original) {
        Entity orb = this.globeWorld$self();
        return orb.getX() + CoordUtil.wrappedDeltaBlock(orb.level(), original.call(player), orb.getX());
    }

    @WrapOperation(
        method = "followNearbyPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getZ()D"
        )
    )
    private double useNearestAliasPlayerZ(Player player, Operation<Double> original) {
        Entity orb = this.globeWorld$self();
        return orb.getZ() + CoordUtil.wrappedDeltaBlock(orb.level(), original.call(player), orb.getZ());
    }

    @WrapOperation(
        method = "scanForMerges",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private <T extends Entity> List<T> getVisibleMergeCandidates(
            Level level,
            EntityTypeTest<Entity, T> entityType,
            AABB box,
            Predicate<? super T> selector,
            Operation<List<T>> original) {
        return globeWorld$visibleMergeCandidates(level, entityType, box, selector);
    }

    @WrapOperation(
        method = "tryMergeToExisting",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
        )
    )
    private static <T extends Entity> List<T> getVisibleAwardMergeCandidates(
            ServerLevel level,
            EntityTypeTest<Entity, T> entityType,
            AABB box,
            Predicate<? super T> selector,
            Operation<List<T>> original) {
        return globeWorld$visibleMergeCandidates(level, entityType, box, selector);
    }

    @Unique
    private ExperienceOrb globeWorld$self() {
        return (ExperienceOrb)(Object)this;
    }

    @Unique
    private static <T extends Entity> List<T> globeWorld$visibleMergeCandidates(
            Level level,
            EntityTypeTest<Entity, T> entityType,
            AABB box,
            Predicate<? super T> selector) {
        return TopologicalCollisionQueries.entities(level, entityType, box, selector);
    }
}
