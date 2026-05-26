package globe.world.mixin;

import globe.world.util.CoordUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(ServerLevel.class)
public class ServerLevelEntityMixin {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void canonicalizeMobBeforeStorage(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        canonicalizeMob(entity);
    }

    @ModifyVariable(method = "addLegacyChunkEntities", at = @At("HEAD"), argsOnly = true, index = 1)
    private Stream<Entity> canonicalizeLegacyChunkMobs(Stream<Entity> entities) {
        return entities.map(ServerLevelEntityMixin::canonicalizeMob);
    }

    @ModifyVariable(method = "addWorldGenChunkEntities", at = @At("HEAD"), argsOnly = true, index = 1)
    private Stream<Entity> canonicalizeWorldGenChunkMobs(Stream<Entity> entities) {
        return entities.map(ServerLevelEntityMixin::canonicalizeMob);
    }

    private static Entity canonicalizeMob(Entity entity) {
        if (!(entity instanceof Mob)) {
            return entity;
        }

        double x = CoordUtil.wrapBlock(entity.level(), (int) Math.floor(entity.getX())) + (entity.getX() - Math.floor(entity.getX()));
        double z = CoordUtil.wrapBlock(entity.level(), (int) Math.floor(entity.getZ())) + (entity.getZ() - Math.floor(entity.getZ()));
        if (x != entity.getX() || z != entity.getZ()) {
            entity.snapTo(x, entity.getY(), z, entity.getYRot(), entity.getXRot());
            entity.syncPacketPositionCodec(x, entity.getY(), z);
        }
        return entity;
    }
}
