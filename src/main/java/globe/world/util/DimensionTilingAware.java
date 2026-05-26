package globe.world.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public interface DimensionTilingAware {
    void globeWorld$setDimension(ResourceKey<Level> dimension);

    ResourceKey<Level> globeWorld$getDimension();
}
