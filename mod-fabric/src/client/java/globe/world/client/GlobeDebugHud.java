package globe.world.client;

import globe.world.config.GlobeConfig;
import globe.world.config.GameplaySettings;
import globe.world.config.TopologySettings;
import globe.world.topology.TileGeometry;
import globe.world.topology.TopologyContext;
import globe.world.topology.TopologyContexts;
import globe.world.util.CoordUtil;
import globe.world.util.DimensionTiling;
import globe.world.util.GlobeEntityAliasing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GlobeDebugHud {
    private static final int LINE_HEIGHT = 9;
    private static final int TEXT_COLOR = -2039584;
    private static final int BACKGROUND_COLOR = -1873784752;

    private GlobeDebugHud() {
    }

    public static void extractRenderState(GuiGraphicsExtractor graphics) {
        if (!GlobeDebugState.debugScreenEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Entity cameraEntity = minecraft.getCameraEntity();
        Level level = minecraft.level;
        if (level == null || cameraEntity == null || minecraft.options.hideGui && minecraft.screen == null) {
            return;
        }

        List<String> leftLines = new ArrayList<>();
        List<String> rightLines = new ArrayList<>();
        collectLines(level, cameraEntity, leftLines, rightLines);

        graphics.nextStratum();
        extractLines(graphics, minecraft.font, leftLines, true);
        extractLines(graphics, minecraft.font, rightLines, false);
    }

    private static void collectLines(Level level, Entity cameraEntity, List<String> leftLines, List<String> rightLines) {
        TopologyContext topology = TopologyContexts.forLevel(level);
        DimensionTiling currentTiling = topology.tiling();
        DimensionTiling overworldTiling = DimensionTiling.forDimension(Level.OVERWORLD);
        DimensionTiling netherTiling = DimensionTiling.forDimension(Level.NETHER);
        BlockPos pos = cameraEntity.blockPosition();
        ChunkPos chunk = ChunkPos.containing(pos);
        Vec3 canonical = topology.canonicalBlock(cameraEntity.position());
        ChunkPos canonicalChunk = topology.canonicalChunk(chunk);
        TileGeometry.LatticeCoordinate lattice = topology.latticeCoordinate(chunk);
        ChunkPos translation = topology.latticeTranslation(lattice);
        TileGeometry.BoundaryHit nearestBoundary =
                topology.enabled() ? topology.nearestBoundary(cameraEntity.position()) : null;
        long worldTime = level.getDefaultClockTime();
        double localDayTicks = CoordUtil.localSolarDayTicks(currentTiling, worldTime, cameraEntity.getX());
        double absoluteDayTicks = positiveModulo(worldTime, CoordUtil.MINECRAFT_DAY_TICKS);
        int configuredRenderDistance = Minecraft.getInstance().options.renderDistance().get();
        int effectiveRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();

        leftLines.add("[Globe World]");
        leftLines.add(String.format(Locale.ROOT, "Canonical XYZ: %.3f / %.3f / %.3f",
                canonical.x(), canonical.y(), canonical.z()));
        leftLines.add(String.format(Locale.ROOT, "Canon chunk: %d %d (%s)",
                canonicalChunk.x(), canonicalChunk.z(), topology.isCanonical(chunk) ? "Canon" : "Alias"));
        leftLines.add(String.format(Locale.ROOT, "Lattice: (%+d,%+d), shift %+d %+d [%s]",
                lattice.k(), lattice.l(), translation.x(), translation.z(), topology.geometryRevision()));
        leftLines.add(String.format(Locale.ROOT, "Render distance: configured %d, effective %d",
                configuredRenderDistance,
                effectiveRenderDistance));
        leftLines.add(String.format(Locale.ROOT, "Local time: %s (%s, %dt)",
                formatClock(localDayTicks),
                formatOffset(CoordUtil.longitudeOffsetTicks(currentTiling, cameraEntity.getX())),
                (int) Math.floor(localDayTicks)));
        leftLines.add("Day Cycle: " + dayCycleSummary());
        leftLines.add("Natural spawns: " + naturalSpawnSummary());
        leftLines.add("");
        leftLines.add("Facing: " + directionSummary(cameraEntity.getDirection()));
        leftLines.add("Local light level: " + lightSummary(level, pos));
        leftLines.add("Nearest wrap seam: " + seamSummary(nearestBoundary));
        leftLines.add("Overworld tile width: " + tileSummary(overworldTiling));
        leftLines.add("Nether tile width: " + tileSummary(netherTiling));
        leftLines.add("Nether portal ratio: " + portalScaleSummary());
        leftLines.add(String.format(Locale.ROOT, "Entity aliases: %s, rings %s (%d shown, %d culled, %d auto-skipped)",
                GlobeEntityAliasDiagnostics.mode().displayName(),
                GlobeEntityAliasing.maxAliasRingsDisplayName(),
                GlobeEntityAliasDiagnostics.submittedThisFrame(),
                GlobeEntityAliasDiagnostics.culledThisFrame(),
                GlobeEntityAliasDiagnostics.autoSkippedThisFrame()));

        rightLines.add("[Real / Alias]");
        rightLines.add(String.format(Locale.ROOT, "Absolute XYZ: %.3f / %.3f / %.3f",
                cameraEntity.getX(),
                cameraEntity.getY(),
                cameraEntity.getZ()));
        rightLines.add(String.format(Locale.ROOT, "Alias chunk: %d %d", chunk.x(), chunk.z()));
        rightLines.add(String.format(Locale.ROOT, "Absolute time: %s (day %d, %dt)",
                formatClock(absoluteDayTicks),
                Math.floorDiv(worldTime, CoordUtil.MINECRAFT_DAY_TICKS),
                (int) absoluteDayTicks));
    }

    private static void extractLines(GuiGraphicsExtractor graphics, Font font, List<String> lines, boolean alignLeft) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) {
                int width = font.width(line);
                int left = alignLeft ? 2 : graphics.guiWidth() - 2 - width;
                int top = 2 + LINE_HEIGHT * i;
                graphics.fill(left - 1, top - 1, left + width + 1, top + LINE_HEIGHT - 1, BACKGROUND_COLOR);
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) {
                int width = font.width(line);
                int left = alignLeft ? 2 : graphics.guiWidth() - 2 - width;
                int top = 2 + LINE_HEIGHT * i;
                graphics.text(font, line, left, top, TEXT_COLOR, false);
            }
        }
    }

    private static String directionSummary(Direction direction) {
        String label = switch (direction) {
            case NORTH -> "North";
            case SOUTH -> "South";
            case EAST -> "East";
            case WEST -> "West";
            default -> capitalize(direction.getName());
        };
        if (direction == Direction.WEST) {
            return label + " (toward sunset)";
        }
        if (direction == Direction.EAST) {
            return label + " (toward sunrise)";
        }
        return label;
    }

    private static String lightSummary(Level level, BlockPos pos) {
        int raw = level.getMaxLocalRawBrightness(pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        return String.format(Locale.ROOT, "%d (sky %d, block %d)", raw, sky, block);
    }

    private static String tileSummary(DimensionTiling tiling) {
        if (!tiling.enabled()) {
            return "disabled";
        }
        return String.format(
                Locale.ROOT,
                "%s, %d chunks / %d m (%s)",
                tiling.mode().displayName(),
                tiling.tileSizeChunks(),
                tiling.tileSizeBlocks(),
                tiling.terrainMode().displayName()
        );
    }

    private static String seamSummary(TileGeometry.BoundaryHit hit) {
        if (hit == null) {
            return "disabled";
        }
        return String.format(
                Locale.ROOT,
                "%s %.1f m at %.1f %.1f",
                hit.segment().outsideAlias().seamLabel(),
                hit.distance(),
                hit.boundaryX(),
                hit.boundaryZ());
    }

    private static String dayCycleSummary() {
        GameplaySettings settings = GlobeConfig.gameplaySettings();
        return settings.dayNightCycleMode().displayName() + " x" + formatMultiplier(settings.dayLengthMultiplier());
    }

    private static String portalScaleSummary() {
        TopologySettings settings = GlobeConfig.topologySettings();
        return String.format(
                Locale.ROOT,
                "%s (%d/%d)",
                settings.netherPortalScaleLabel(),
                settings.netherPortalScaleNumerator(),
                settings.netherPortalScaleDenominator()
        );
    }

    private static String naturalSpawnSummary() {
        GameplaySettings settings = GlobeConfig.gameplaySettings();
        return String.format(
                Locale.ROOT,
                "world spawn %s, player >= %d m",
                settings.allowMobsAtWorldSpawn() ? "allowed" : "blocked",
                settings.playerMobSpawnExclusionBlocks()
        );
    }

    private static String formatMultiplier(double multiplier) {
        if (multiplier == Math.rint(multiplier)) {
            return String.format(Locale.ROOT, "%.0f", multiplier);
        }
        return String.format(Locale.ROOT, "%.1f", multiplier);
    }

    private static String formatClock(double dayTicks) {
        double clockTicks = positiveModulo(dayTicks + 6000.0, CoordUtil.MINECRAFT_DAY_TICKS);
        int totalMinutes = (int) Math.floor(clockTicks * 1440.0 / CoordUtil.MINECRAFT_DAY_TICKS + 0.5) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", totalMinutes / 60, totalMinutes % 60);
    }

    private static String formatOffset(double offsetTicks) {
        int offsetMinutes = (int) Math.round(offsetTicks * 60.0 / 1000.0);
        String sign = offsetMinutes < 0 ? "-" : "+";
        int absoluteMinutes = Math.abs(offsetMinutes);
        return String.format(Locale.ROOT, "UTC%s%02d:%02d", sign, absoluteMinutes / 60, absoluteMinutes % 60);
    }

    private static double positiveModulo(double value, double mod) {
        double result = value % mod;
        return result < 0.0 ? result + mod : result;
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
