package globe.world.client.mixin;

import globe.world.client.GlobeEntityAliasDiagnostics;
import globe.world.client.GlobeVisualAliasUtil;
import globe.world.util.GlobeEntityAliasing;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ClientLevel level;

    @Shadow
    private EntityRenderState extractEntity(Entity entity, float partialTickTime) {
        throw new AssertionError();
    }

    @Shadow
    public abstract boolean isSectionCompiledAndVisible(BlockPos blockPos);

    @Shadow
    protected abstract boolean shouldShowEntityOutlines();

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void globeWorld$extractEntityAliasRenderStates(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output, CallbackInfo ci) {
        GlobeEntityAliasDiagnostics.beginFrame();

        Vec3 cameraPos = camera.position();
        TickRateManager tickRateManager = this.minecraft.level.tickRateManager();
        boolean shouldShowEntityOutlines = this.shouldShowEntityOutlines();

        Profiler.get().push("globeEntityAliases");
        for (Entity entity : this.level.entitiesForRendering()) {
            if (!GlobeEntityAliasing.canVisualAlias(entity, camera.entity())) {
                continue;
            }
            if (entity == camera.entity() && (!camera.isDetached() || !(camera.entity() instanceof LivingEntity livingEntity) || !livingEntity.isSleeping())) {
                continue;
            }

            List<GlobeEntityAliasing.AliasOffset> offsets = GlobeVisualAliasUtil.renderOffsets(
                    entity,
                    camera.entity(),
                    cameraPos,
                    frustum,
                    this::isSectionCompiledAndVisible
            );
            if (offsets.isEmpty()) {
                continue;
            }

            if (entity.tickCount == 0) {
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
            }

            float partialEntity = deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));
            for (GlobeEntityAliasing.AliasOffset offset : offsets) {
                EntityRenderState aliasState = this.extractEntity(entity, partialEntity);
                GlobeVisualAliasUtil.applyOffset(aliasState, offset, cameraPos);
                output.entityRenderStates.add(aliasState);
                GlobeEntityAliasDiagnostics.recordSubmitted();
                if (aliasState.appearsGlowing() && shouldShowEntityOutlines) {
                    output.haveGlowingEntities = true;
                }
            }
        }
        output.lastEntityRenderStateCount = output.entityRenderStates.size();
        Profiler.get().pop();
    }
}
