package globe.world.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.GlobeWorld;
import globe.world.topology.AtlasTorusProjection;
import globe.world.topology.TileGeometry;
import globe.world.util.DimensionTiling;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public class GlobeAtlasPowerState extends SavedData {
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
            GlobeAtlasLoadout.CODEC.fieldOf("loadout").forGetter(Entry::loadout),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
            Codec.LONG.optionalFieldOf("priority", 0L).forGetter(Entry::priority)
    ).apply(instance, Entry::new));
    private static final Codec<GlobeAtlasPowerState> CODEC = ENTRY_CODEC.listOf().xmap(
            GlobeAtlasPowerState::new,
            state -> List.copyOf(state.entries.values()));
    private static final SavedDataType<GlobeAtlasPowerState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "atlas_power"),
            GlobeAtlasPowerState::new,
            CODEC,
            DataFixTypes.SAVED_DATA_MAP_DATA);
    private static final Comparator<BlockPos> POS_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getX())
            .thenComparingInt(pos -> pos.getY())
            .thenComparingInt(pos -> pos.getZ());

    private final Map<BlockPos, Entry> entries = new TreeMap<>(POS_ORDER);
    private String reconciledProjectionIdentity;

    public GlobeAtlasPowerState() {
    }

    private GlobeAtlasPowerState(final List<Entry> entries) {
        for (Entry entry : entries) {
            this.entries.put(entry.pos(), entry);
        }
    }

    public static GlobeAtlasPowerState get(final ServerLevel level) {
        GlobeAtlasPowerState state = level.getDataStorage().computeIfAbsent(TYPE);
        state.recanonicalize(DimensionTiling.forDimension(Level.OVERWORLD));
        return state;
    }

    public static Optional<GlobeAtlasPowerState> getIfOverworld(final ServerLevel level) {
        return Level.OVERWORLD.equals(level.dimension()) ? Optional.of(get(level)) : Optional.empty();
    }

    public Optional<Entry> entry(final BlockPos rawPos) {
        return Optional.ofNullable(this.entries.get(this.canonicalPos(rawPos)));
    }

    public void update(final ServerLevel level, final BlockPos rawPos, final GlobeAtlasLoadout loadout, final String name, final boolean edited) {
        BlockPos canonicalPos = this.canonicalPos(rawPos);
        Entry previous = this.entries.get(canonicalPos);
        long priority = edited || previous == null ? level.getGameTime() : previous.priority();
        this.entries.put(canonicalPos, new Entry(canonicalPos, loadout, sanitizeName(name), priority));
        this.setDirty();
    }

    public void remove(final BlockPos rawPos) {
        if (this.entries.remove(this.canonicalPos(rawPos)) != null) {
            this.setDirty();
        }
    }

    public int spentPoints() {
        int spent = 0;
        for (Entry entry : this.entries.values()) {
            spent += entry.loadout().cost();
        }
        return spent;
    }

    public Set<BlockPos> poweredPositions(final GlobeDiscoveryRewards rewards) {
        int budget = rewards.totalPoints();
        int spent = 0;
        Set<BlockPos> powered = new LinkedHashSet<>();
        for (Entry entry : this.priorityOrderedEntries()) {
            int cost = entry.loadout().cost();
            if (!entry.loadout().active() || cost <= 0) {
                continue;
            }
            if (spent + cost <= budget) {
                spent += cost;
                powered.add(entry.pos());
            }
        }
        return powered;
    }

    public boolean isPowered(final BlockPos rawPos, final GlobeDiscoveryRewards rewards) {
        return this.poweredPositions(rewards).contains(this.canonicalPos(rawPos));
    }

    public List<Entry> poweredEntries(final GlobeDiscoveryRewards rewards) {
        Set<BlockPos> powered = this.poweredPositions(rewards);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (powered.contains(entry.pos())) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<Entry> entries() {
        return List.copyOf(this.entries.values());
    }

    private List<Entry> priorityOrderedEntries() {
        List<Entry> ordered = new ArrayList<>(this.entries.values());
        ordered.sort(Comparator.comparingLong(Entry::priority).reversed().thenComparing(Entry::pos, POS_ORDER));
        return ordered;
    }

    private void recanonicalize(final DimensionTiling tiling) {
        String projectionIdentity = AtlasTorusProjection.create(tiling).identity();
        if (projectionIdentity.equals(this.reconciledProjectionIdentity)) {
            return;
        }
        TileGeometry geometry = TileGeometry.create(tiling);
        Map<BlockPos, Entry> migrated = new TreeMap<>(POS_ORDER);
        boolean changed = false;
        for (Entry entry : this.entries.values()) {
            BlockPos canonical = geometry.canonicalBlock(
                    entry.pos().getX(),
                    entry.pos().getY(),
                    entry.pos().getZ()).immutable();
            Entry migratedEntry = canonical.equals(entry.pos())
                    ? entry
                    : new Entry(canonical, entry.loadout(), entry.name(), entry.priority());
            Entry collision = migrated.get(canonical);
            if (collision == null || migratedEntry.priority() > collision.priority()) {
                migrated.put(canonical, migratedEntry);
            }
            changed |= !canonical.equals(entry.pos()) || collision != null;
        }
        if (changed) {
            this.entries.clear();
            this.entries.putAll(migrated);
            this.setDirty();
        }
        this.reconciledProjectionIdentity = projectionIdentity;
    }

    private BlockPos canonicalPos(final BlockPos rawPos) {
        DimensionTiling tiling = DimensionTiling.forDimension(Level.OVERWORLD);
        return TileGeometry.create(tiling).canonicalBlock(
                rawPos.getX(),
                rawPos.getY(),
                rawPos.getZ()).immutable();
    }

    private static String sanitizeName(final String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    public record Entry(BlockPos pos, GlobeAtlasLoadout loadout, String name, long priority) {
    }
}
