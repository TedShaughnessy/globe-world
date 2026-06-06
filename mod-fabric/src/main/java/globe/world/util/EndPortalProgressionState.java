package globe.world.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import globe.world.GlobeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;

public class EndPortalProgressionState extends SavedData {
    public static final int CURRENT_DATA_VERSION = 1;

    private static final Codec<EndPortalProgressionState> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.INT.optionalFieldOf("data_version", CURRENT_DATA_VERSION)
                                    .forGetter(EndPortalProgressionState::dataVersion),
                            Codec.STRING.optionalFieldOf("last_classification", "")
                                    .forGetter(EndPortalProgressionState::lastClassification),
                            Codec.STRING.optionalFieldOf("last_reason", "")
                                    .forGetter(EndPortalProgressionState::lastReason),
                            Codec.INT.optionalFieldOf("tile_size_chunks", 0)
                                    .forGetter(EndPortalProgressionState::tileSizeChunks),
                            Codec.INT.optionalFieldOf("settings_version", 0)
                                    .forGetter(EndPortalProgressionState::settingsVersion),
                            BlockPos.CODEC.optionalFieldOf("fallback_portal")
                                    .forGetter(state -> Optional.ofNullable(state.fallbackPortalPos)),
                            Codec.INT.optionalFieldOf("fallback_eye_mask", -1)
                                    .forGetter(EndPortalProgressionState::fallbackEyeMask),
                            Codec.STRING.optionalFieldOf("last_validation", "")
                                    .forGetter(EndPortalProgressionState::lastValidationSummary)
                    ).apply(instance, EndPortalProgressionState::new)
            );

    public static final SavedDataType<EndPortalProgressionState> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GlobeWorld.MOD_ID, "end_portal_progression"),
            EndPortalProgressionState::new,
            CODEC,
            DataFixTypes.SAVED_DATA_MAP_DATA
    );

    private int dataVersion = CURRENT_DATA_VERSION;
    private String lastClassification = "";
    private String lastReason = "";
    private int tileSizeChunks;
    private int settingsVersion;
    private BlockPos fallbackPortalPos;
    private int fallbackEyeMask = -1;
    private String lastValidationSummary = "";

    public EndPortalProgressionState() {
    }

    private EndPortalProgressionState(
            int dataVersion,
            String lastClassification,
            String lastReason,
            int tileSizeChunks,
            int settingsVersion,
            Optional<BlockPos> fallbackPortalPos,
            int fallbackEyeMask,
            String lastValidationSummary) {
        this.dataVersion = dataVersion;
        this.lastClassification = lastClassification;
        this.lastReason = lastReason;
        this.tileSizeChunks = tileSizeChunks;
        this.settingsVersion = settingsVersion;
        this.fallbackPortalPos = fallbackPortalPos.orElse(null);
        this.fallbackEyeMask = fallbackEyeMask;
        this.lastValidationSummary = lastValidationSummary;
    }

    public static EndPortalProgressionState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public int dataVersion() {
        return dataVersion;
    }

    public String lastClassification() {
        return lastClassification;
    }

    public String lastReason() {
        return lastReason;
    }

    public int tileSizeChunks() {
        return tileSizeChunks;
    }

    public int settingsVersion() {
        return settingsVersion;
    }

    public BlockPos fallbackPortalPos() {
        return fallbackPortalPos;
    }

    public int fallbackEyeMask() {
        return fallbackEyeMask;
    }

    public String lastValidationSummary() {
        return lastValidationSummary;
    }

    public void recordClassification(EndPortalAvailability.Report report, int settingsVersion) {
        this.dataVersion = CURRENT_DATA_VERSION;
        this.lastClassification = report.status().name();
        this.lastReason = report.reason();
        this.tileSizeChunks = report.tileSizeChunks();
        this.settingsVersion = settingsVersion;
        setDirty();
    }

    public void setFallbackPortalPos(BlockPos fallbackPortalPos) {
        this.fallbackPortalPos = fallbackPortalPos;
        setDirty();
    }

    public void setFallbackEyeMask(int fallbackEyeMask) {
        this.fallbackEyeMask = fallbackEyeMask;
        setDirty();
    }

    public void setLastValidationSummary(String lastValidationSummary) {
        this.lastValidationSummary = lastValidationSummary;
        setDirty();
    }
}
