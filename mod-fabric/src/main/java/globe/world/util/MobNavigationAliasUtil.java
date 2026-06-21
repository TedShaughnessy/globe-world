package globe.world.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class MobNavigationAliasUtil {
    private static final Set<Mob> NEEDS_PATH_RECOMPUTE = Collections.newSetFromMap(new WeakHashMap<>());

    private MobNavigationAliasUtil() {
    }

    public static void resetAfterAliasCanonicalization(Entity entity) {
        if (entity instanceof Mob mob) {
            NEEDS_PATH_RECOMPUTE.add(mob);
        }
    }

    public static boolean consumePathRecompute(Mob mob) {
        return NEEDS_PATH_RECOMPUTE.remove(mob);
    }

    public static Set<BlockPos> dedupePathTargetsByNodeHash(Mob mob, Iterable<BlockPos> targets) {
        Map<Integer, BlockPos> byNodeHash = new LinkedHashMap<>();
        for (BlockPos target : targets) {
            int hash = Node.createHash(target.getX(), target.getY(), target.getZ());
            BlockPos existing = byNodeHash.get(hash);
            if (existing == null || distanceToCenterSqr(mob, target) < distanceToCenterSqr(mob, existing)) {
                byNodeHash.put(hash, target);
            }
        }
        return new LinkedHashSet<>(byNodeHash.values());
    }

    private static double distanceToCenterSqr(Mob mob, BlockPos pos) {
        double dx = mob.getX() - (pos.getX() + 0.5D);
        double dy = mob.getY() - (pos.getY() + 0.5D);
        double dz = mob.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }
}
