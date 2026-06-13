package globe.world.mixin;

import globe.world.topology.TopologicalExplosions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Vec3 center;

    @Shadow
    @Final
    @Nullable
    private Entity source;

    @Shadow
    @Final
    private float radius;

    @Shadow
    @Final
    private DamageSource damageSource;

    @Shadow
    @Final
    private ExplosionDamageCalculator damageCalculator;

    @Shadow
    @Final
    private Map<Player, Vec3> hitPlayers;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void canonicalizeExplodedPositions(CallbackInfoReturnable<List<BlockPos>> cir) {
        cir.setReturnValue(TopologicalExplosions.canonicalAffectedBlocks(this.level, this.center, cir.getReturnValue()));
    }

    @Inject(method = "hurtEntities", at = @At("HEAD"), cancellable = true)
    private void hurtEntitiesTopologically(CallbackInfo ci) {
        if (TopologicalExplosions.hurtEntities(
                (ServerExplosion) (Object) this,
                this.level,
                this.source,
                this.damageSource,
                this.damageCalculator,
                this.center,
                this.radius,
                this.hitPlayers)) {
            ci.cancel();
        }
    }
}
