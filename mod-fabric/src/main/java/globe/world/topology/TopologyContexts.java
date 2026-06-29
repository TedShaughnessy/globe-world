package globe.world.topology;

import globe.world.util.DimensionTiling;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class TopologyContexts {
    private static final ThreadLocal<TopologyContext> CURRENT = new ThreadLocal<>();
    private static final ConcurrentMap<ContextKey, TopologyContext> CONTEXTS = new ConcurrentHashMap<>();

    private TopologyContexts() {
    }

    public static TopologyContext forLevel(Level level) {
        return forDimension(level.dimension());
    }

    public static TopologyContext forDimension(ResourceKey<Level> dimension) {
        DimensionTiling tiling = DimensionTiling.forDimension(dimension);
        return CONTEXTS.computeIfAbsent(
                new ContextKey(dimension, tiling),
                key -> new TopologyContext(key.dimension(), key.tiling())
        );
    }

    public static TopologyContext currentOrOverworld() {
        TopologyContext current = CURRENT.get();
        return current != null ? current : forDimension(Level.OVERWORLD);
    }

    public static void runWith(TopologyContext context, Runnable action) {
        with(context, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T with(TopologyContext context, Supplier<T> action) {
        TopologyContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return DimensionTiling.with(context.tiling(), action);
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    private record ContextKey(ResourceKey<Level> dimension, DimensionTiling tiling) {
    }
}
