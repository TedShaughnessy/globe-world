package globe.world.atlas;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public enum GlobeAtlasEffect {
    SPEED("effect.minecraft.speed"),
    HASTE("effect.minecraft.haste"),
    JUMP_BOOST("effect.minecraft.jump_boost");

    private final String translationKey;

    GlobeAtlasEffect(final String translationKey) {
        this.translationKey = translationKey;
    }

    public int mask() {
        return 1 << this.ordinal();
    }

    public String translationKey() {
        return this.translationKey;
    }

    public Holder<MobEffect> mobEffect() {
        return switch (this) {
            case SPEED -> MobEffects.SPEED;
            case HASTE -> MobEffects.HASTE;
            case JUMP_BOOST -> MobEffects.JUMP_BOOST;
        };
    }

    public static int validMask(final int mask) {
        int valid = 0;
        for (GlobeAtlasEffect effect : values()) {
            valid |= effect.mask();
        }
        return mask & valid;
    }
}
