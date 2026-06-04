package globe.world.mixin;

import globe.world.util.EntityCanonicalizer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(ServerLevel.class)
public class ServerLevelEntityMixin {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void canonicalizeEntityBeforeStorage(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        canonicalizeStoredEntity(entity);
    }

    @ModifyVariable(method = "addLegacyChunkEntities", at = @At("HEAD"), argsOnly = true, index = 1)
    private Stream<Entity> canonicalizeLegacyChunkEntities(Stream<Entity> entities) {
        return entities.map(ServerLevelEntityMixin::canonicalizeStoredEntity);
    }

    @ModifyVariable(method = "addWorldGenChunkEntities", at = @At("HEAD"), argsOnly = true, index = 1)
    private Stream<Entity> canonicalizeWorldGenChunkEntities(Stream<Entity> entities) {
        return entities.map(ServerLevelEntityMixin::canonicalizeStoredEntity);
    }

    private static Entity canonicalizeStoredEntity(Entity entity) {
        EntityCanonicalizer.canonicalizeForStorage(entity);
        return entity;
    }
}
