package globe.world.util;

import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

public final class StructurePlacementShifts {
    private static final ThreadLocal<Map<StructureStart, ArrayDeque<Shift>>> PENDING =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<Boolean> PLACING_SHIFTED =
            ThreadLocal.withInitial(() -> false);

    private StructurePlacementShifts() {
    }

    public static void clear() {
        PENDING.get().clear();
        PLACING_SHIFTED.set(false);
    }

    public static void enqueue(StructureStart start, int chunkShiftX, int chunkShiftZ) {
        PENDING.get()
                .computeIfAbsent(start, ignored -> new ArrayDeque<>())
                .addLast(new Shift(chunkShiftX, chunkShiftZ));
    }

    public static Shift consume(StructureStart start) {
        ArrayDeque<Shift> shifts = PENDING.get().get(start);
        if (shifts == null) {
            return null;
        }

        Shift shift = shifts.pollFirst();
        if (shifts.isEmpty()) {
            PENDING.get().remove(start);
        }
        return shift;
    }

    public static boolean isPlacingShifted() {
        return PLACING_SHIFTED.get();
    }

    public static void setPlacingShifted(boolean placingShifted) {
        PLACING_SHIFTED.set(placingShifted);
    }

    public record Shift(int chunkX, int chunkZ) {
        public boolean isZero() {
            return chunkX == 0 && chunkZ == 0;
        }

        public int blockX() {
            return chunkX * 16;
        }

        public int blockZ() {
            return chunkZ * 16;
        }
    }
}
