package globe.world.client;

import globe.world.GlobeWorld;
import globe.world.config.GlobeSettings;
import globe.world.config.GlobeSettingsHolder;
import globe.world.config.TopologySettings;
import globe.world.diagnostics.DiagnosticsChannel;
import globe.world.diagnostics.GlobeDiagnostics;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DebugLevelSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.storage.LevelDataAndDimensions;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public final class GlobeSeedPreflight {
    private static final int MAX_REROLL_ATTEMPTS = 32;
    private static final int SMALL_TILE_MAX_CHUNKS = 128;
    private static final int LARGE_TILE_SAMPLE_CAP = 4096;

    private GlobeSeedPreflight() {
    }

    public static LevelDataAndDimensions.WorldDataAndGenSettings rerollWaterOnlySeedIfNeeded(
            CreateWorldScreen screen,
            LayeredRegistryAccess<RegistryLayer> finalLayers,
            LevelDataAndDimensions.WorldDataAndGenSettings original) {
        GlobeSettings settings = GlobeWorldCreateState.get();
        TopologySettings topology = settings.topology();
        if (!topology.enabled() || !topology.avoidWaterOnlySeeds()) {
            return original;
        }
        if (!screen.getUiState().getSeed().trim().isEmpty()) {
            return original;
        }

        long originalSeed = original.genSettings().options().seed();
        long lastCandidate = originalSeed;
        int rejectedSeeds = 0;
        for (int attempt = 1; attempt <= MAX_REROLL_ATTEMPTS; attempt++) {
            long candidate = attempt == 1 ? originalSeed : WorldOptions.randomSeed();
            lastCandidate = candidate;
            boolean waterOnly;
            try {
                waterOnly = appearsWaterOnly(finalLayers, original, topology, candidate);
            } catch (RuntimeException exception) {
                GlobeWorld.LOGGER.warn("Globe World skipped water-only seed preflight after an unexpected error.", exception);
                return original;
            }
            if (!waterOnly) {
                if (candidate == originalSeed) {
                    return original;
                }
                GlobeWorld.LOGGER.info(
                        "Globe World replaced water-only random seed {} with {} after {} attempt(s)",
                        originalSeed,
                        candidate,
                        attempt
                );
                return withSeed(original, candidate, settings);
            }
            rejectedSeeds++;
        }

        GlobeWorld.LOGGER.warn(
                "Globe World could not find a non-water Overworld tile after {} random seed attempts; using seed {}",
                MAX_REROLL_ATTEMPTS,
                lastCandidate
        );
        GlobeDiagnostics.debug(
                DiagnosticsChannel.WORLDGEN,
                "GW_SEED_PREFLIGHT all_candidates_water_only rejected={}",
                rejectedSeeds
        );
        return lastCandidate == originalSeed ? original : withSeed(original, lastCandidate, settings);
    }

    private static LevelDataAndDimensions.WorldDataAndGenSettings withSeed(
            LevelDataAndDimensions.WorldDataAndGenSettings original,
            long seed,
            GlobeSettings settings) {
        WorldOptions options = original.genSettings().options().withSeed(OptionalLong.of(seed));
        WorldGenSettings genSettings = new WorldGenSettings(options, original.genSettings().dimensions());
        ((GlobeSettingsHolder) (Object) genSettings).globeWorld$setGlobeSettings(settings);
        return new LevelDataAndDimensions.WorldDataAndGenSettings(original.data(), genSettings);
    }

    private static boolean appearsWaterOnly(
            LayeredRegistryAccess<RegistryLayer> finalLayers,
            LevelDataAndDimensions.WorldDataAndGenSettings original,
            TopologySettings topology,
            long seed) {
        LevelStem overworld = finalLayers.compositeAccess()
                .lookupOrThrow(Registries.LEVEL_STEM)
                .getOptional(LevelStem.OVERWORLD)
                .or(() -> original.genSettings().dimensions().get(LevelStem.OVERWORLD))
                .orElse(null);
        if (overworld == null || skippedGenerator(overworld.generator())) {
            return false;
        }

        ChunkGenerator generator = overworld.generator();
        BiomeSource biomeSource = generator.getBiomeSource();
        Climate.Sampler sampler = climateSampler(finalLayers, generator, seed);
        int sampleStep = sampleStepBlocks(topology.tileSize());
        List<Integer> sampleXs = sampleCoordinates(topology.tileSize(), sampleStep);
        if (sampleXs.isEmpty()) {
            return false;
        }
        List<Integer> sampleZs = sampleXs;
        int sampleCount = sampleXs.size() * sampleZs.size();
        if (topology.tileSize() > SMALL_TILE_MAX_CHUNKS && sampleCount > LARGE_TILE_SAMPLE_CAP) {
            GlobeDiagnostics.debug(
                    DiagnosticsChannel.WORLDGEN,
                    "GW_SEED_PREFLIGHT skip_large_tile tileSize={} sampleStep={} sampleCount={}",
                    topology.tileSize(),
                    sampleStep,
                    sampleCount
            );
            return false;
        }

        for (int blockX : sampleXs) {
            int quartX = QuartPos.fromBlock(blockX);
            for (int blockZ : sampleZs) {
                int quartZ = QuartPos.fromBlock(blockZ);
                Holder<Biome> biome = biomeSource.getNoiseBiome(quartX, 0, quartZ, sampler);
                if (!isWaterLike(biome)) {
                    GlobeDiagnostics.debug(
                            DiagnosticsChannel.WORLDGEN,
                            "GW_SEED_PREFLIGHT accepted seed={} tileSize={} sampleStep={} sampleCount={} firstNonWater={} at={},{}",
                            seed,
                            topology.tileSize(),
                            sampleStep,
                            sampleCount,
                            biome.unwrapKey().map(key -> key.identifier().toString()).orElse("<unregistered>"),
                            blockX,
                            blockZ
                    );
                    return false;
                }
            }
        }

        GlobeDiagnostics.debug(
                DiagnosticsChannel.WORLDGEN,
                "GW_SEED_PREFLIGHT rejected_water_only seed={} tileSize={} sampleStep={} sampleCount={}",
                seed,
                topology.tileSize(),
                sampleStep,
                sampleCount
        );
        return true;
    }

    private static boolean skippedGenerator(ChunkGenerator generator) {
        return generator instanceof DebugLevelSource || generator instanceof FlatLevelSource;
    }

    private static Climate.Sampler climateSampler(
            LayeredRegistryAccess<RegistryLayer> finalLayers,
            ChunkGenerator generator,
            long seed) {
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            Registry<NormalNoise.NoiseParameters> noiseRegistry = finalLayers.compositeAccess().lookupOrThrow(Registries.NOISE);
            return RandomState.create(noiseGenerator.generatorSettings().value(), noiseRegistry, seed).sampler();
        }
        return Climate.empty();
    }

    private static boolean isWaterLike(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN)
                || biome.is(BiomeTags.IS_RIVER);
    }

    private static int sampleStepBlocks(int tileSizeChunks) {
        if (tileSizeChunks <= 32) {
            return 8;
        }
        if (tileSizeChunks <= SMALL_TILE_MAX_CHUNKS) {
            return 16;
        }
        return 32;
    }

    private static List<Integer> sampleCoordinates(int tileSizeChunks, int stepBlocks) {
        int minChunk = -tileSizeChunks / 2;
        int maxChunkExclusive = minChunk + tileSizeChunks;
        int blockMin = minChunk * 16;
        int blockMaxExclusive = maxChunkExclusive * 16;
        List<Integer> samples = new ArrayList<>();
        for (int block = blockMin; block < blockMaxExclusive; block += stepBlocks) {
            addSample(samples, block);
        }
        addSample(samples, blockMaxExclusive - 1);
        addSample(samples, blockMin + (blockMaxExclusive - blockMin) / 2);
        return samples;
    }

    private static void addSample(List<Integer> samples, int block) {
        if (!samples.contains(block)) {
            samples.add(block);
        }
    }
}
