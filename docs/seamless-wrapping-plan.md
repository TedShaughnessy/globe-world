Yes, agreed: smoothing is the wrong foundation. It can hide a seam, but it does not make the world topologically periodic. The target should be “the generator’s sample space is a torus,” not “blend unrelated samples near the edge.”

Core Principle

Do not just wrapBlock(x) before vanilla noise. That creates a hard jump where maxX wraps to minX, because vanilla noise was not generated with matching gradients at those endpoints.

Instead, implement true periodic noise: for every X/Z-dependent noise octave, the lattice/hash domain itself must repeat with period W_BLOCKS, so:

noise(x, y, z) == noise(x + W_BLOCKS, y, z)
noise(x, y, z) == noise(x, y, z + W_BLOCKS)
and the seam has matching local behavior, not an edge blend.

Systems To Cover

From the vanilla worldgen flow in NoiseBasedChunkGenerator, NoiseChunk, RandomState, Climate, and SurfaceSystem, the wrapping-sensitive systems are:

Climate sampler:
temperature, vegetation/humidity, continents, erosion, depth, ridges.
These drive biome selection through Climate.Sampler and MultiNoiseBiomeSource.

Terrain density:
final_density, preliminary_surface_level, caves, entrances, spaghetti/noodle caves, pillars, ridges, slides, jaggedness.
These are mostly density functions inside NoiseRouter.

Aquifers:
Use noise plus random aquifer grid points from PositionalRandomFactory.at(gridX, gridY, gridZ).
Needs periodic grid seeding, not block-coordinate wrapping.

Ore veins:
Use vein_toggle, vein_ridged, vein_gap, plus positional random at block coords.
Needs periodic noise and periodic positional random.

Surface rules:
Surface depth, secondary surface noise, badlands pillars, icebergs, clay bands, vertical gradients, noise threshold rules, biome/temperature checks.
These use both NormalNoise, PerlinSimplexNoise, and positional randoms.

Biome-dependent features:
Trees, vegetation, ores, disks, lakes, etc. The current canonical read/write wrapping helps spillover, but placement noise/counts and biome filters also need periodic inputs.

Carvers:
Cave/ravine start seeds are chunk-coordinate based. Existing carver seed wrapping is pointing in the right direction, but needs auditing with periodic terrain and biome sampling.

Structures:
Random-spread placement, probability reducers, references, locate behavior, and large structures crossing tile edges. Existing structure hooks are useful, but this is a separate phase from terrain/biome seamlessness.

Implementation Plan

Delete the smoothing path:
Remove or replace the current PeriodicNoiseUtil.sampleEdgeBlend(...) approach and the density-function mixins that depend on it. Keep the lesson, not the code.

Add a real periodic noise layer:
Build a PeriodicNoiseSampler that reimplements vanilla-style octave sampling with X/Z lattice periods. It should use the same noise parameters and seeds where possible, but choose integer cycles per tile per octave so every octave repeats exactly over W_BLOCKS.

Cover terminal noise functions first:
Replace sampling in:
DensityFunctions$Noise, Shift, ShiftA, ShiftB, ShiftedNoise, and WeirdScaledSampler.
This covers most NoiseRouter terrain, climate, caves, and ore-vein noise.

Add periodic 2D/simplex support:
Cover Biome.BIOME_INFO_NOISE, TEMPERATURE_NOISE, FROZEN_TEMPERATURE_NOISE, placement count noise, and feature state-provider noise. These are outside the main NoiseRouter.

Add periodic positional random utilities:
Do not globally wrap every PositionalRandomFactory.at(...); units differ by caller.
Add call-site utilities for:
block coords, chunk coords, aquifer grid coords, and surface-rule coords.

Audit worldgen phases in order:
Terrain and biomes first, then aquifers/ore veins, then surface rules, then features, then carvers, then structures.

Verify with seam tests:
Add deterministic tests/assertions for sampled values:
sample(x,z) == sample(x + W,z) and sample(x,z) == sample(x,z + W).
Then generate tiny tiles, especially 1, 2, and 6 chunks, and inspect heightmaps, biome bands, caves, aquifers, ores, trees, and surface materials across all four edges and corners.

Important Risk

Very small tiles will necessarily distort low-frequency vanilla terrain. A 1 or 2 chunk world cannot honestly contain vanilla-scale continents. The best implementation will be seamless and stable, but not vanilla-identical. We should treat “periodic and good-looking” as the goal, not “exact vanilla terrain folded into a torus.”