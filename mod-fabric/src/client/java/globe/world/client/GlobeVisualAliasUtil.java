package globe.world.client;

import globe.world.util.GlobeEntityAliasing;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class GlobeVisualAliasUtil {
    private GlobeVisualAliasUtil() {
    }

    public static List<GlobeEntityAliasing.AliasOffset> renderOffsets(
            Entity entity,
            Entity cameraEntity,
            Vec3 cameraPos,
            Frustum frustum,
            Predicate<BlockPos> compiledSectionVisible) {
        double renderRadius = GlobeEntityAliasing.visualAliasRenderRadius(entity);
        if (GlobeEntityAliasing.disabledByAutoGate(entity.level(), renderRadius)) {
            GlobeEntityAliasDiagnostics.recordAutoSkipped();
            return List.of();
        }

        List<GlobeEntityAliasing.AliasOffset> candidates = GlobeEntityAliasing.visualOffsets(
                entity,
                cameraEntity,
                cameraPos,
                renderRadius,
                true
        );
        if (candidates.isEmpty()) {
            return candidates;
        }

        List<GlobeEntityAliasing.AliasOffset> visible = new ArrayList<>(candidates.size());
        for (GlobeEntityAliasing.AliasOffset offset : candidates) {
            if (!frustum.isVisible(offset.box().inflate(0.5D))) {
                GlobeEntityAliasDiagnostics.recordCulled();
                continue;
            }

            BlockPos aliasPos = BlockPos.containing(entity.getX() + offset.dx(), entity.getY(), entity.getZ() + offset.dz());
            if (!entity.level().isOutsideBuildHeight(aliasPos.getY()) && !compiledSectionVisible.test(aliasPos)) {
                GlobeEntityAliasDiagnostics.recordCulled();
                continue;
            }

            visible.add(offset);
        }
        return visible;
    }

    public static void applyOffset(EntityRenderState state, GlobeEntityAliasing.AliasOffset offset, Vec3 cameraPos) {
        state.x += offset.dx();
        state.z += offset.dz();
        double x = state.x - cameraPos.x;
        double y = state.y - cameraPos.y;
        double z = state.z - cameraPos.z;
        state.distanceToCameraSq = x * x + y * y + z * z;
    }
}
