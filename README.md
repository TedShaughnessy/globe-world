# Globe World
A minecraft mod

build: ./gradlew build
test: ./gradlew runClient
pkill -f runClient


Create a visually and functionally “round” Minecraft world by generating a finite, tileable square of terrain that repeats seamlessly when the player walks beyond its bounds. The mod gives the illusion of a continuous globe without breaking vanilla game systems. Optional enhancements include a curvature shader for a round-horizon effect.

Key Features:

Repeating World:
The world is generated as a finite square of chunks.
When the player walks past its edges, the same chunks tile seamlessly.
Seamless Terrain:
Procedural generation ensures edges match up perfectly.
Noise, biomes, and structure seeds are wrapped to prevent seams.
Player Continuity:
No forced teleportation during normal movement.
Optional rebasing on death or world load to keep players near the “center” if coordinates grow too large.
Optional Cosmetic Enhancements:
Curvature shader to simulate a globe.
Hexagonal tiling (future) for alternate terrain patterns.
Vanilla-Compatible:
All vanilla mechanics (mobs, AI, redstone, entities) continue to work.
No modifications to Minecraft physics, networking, or rendering logic beyond worldgen and cosmetic shaders.