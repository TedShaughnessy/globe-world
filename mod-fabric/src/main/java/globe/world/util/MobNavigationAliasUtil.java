package globe.world.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Collections;
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
}
