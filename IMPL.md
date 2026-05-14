# Implementation List


Phase 0 — Setup
Install Java 21, IntelliJ IDEA, Gradle.
Clone Fabric Example Mod and confirm runClient works.
Verify Fabric API dependencies.
Set up version control (git init) and create a repo called orbiterra.
Phase 1 — Core Repeating World
Define the world period W in chunks (e.g., 1024×1024).
Create a wrapper function for chunk/world coordinates:
int wrappedX = Math.floorMod(chunkX, W);
int wrappedZ = Math.floorMod(chunkZ, W);
Hook into chunk generation via Fabric Mixin to apply wrapped coordinates.
Test: player walks past initial square → chunks repeat.
Phase 2 — Seamless Terrain
Modify noise functions to be periodic along both axes.
Ensure biome selection wraps correctly.
Apply structure seed wrapping so villages, temples, and other structures repeat properly.
Optional: blend edges to hide minor artifacts.
Test in-game: edges should match perfectly with no visible seams.
Phase 3 — Player Coordinate Management
Track player position relative to the “center.”
Optional: teleport player back to middle on death/load to prevent coordinate overflow.
Ensure entities, AI, and redstone continue functioning correctly.
Phase 4 — Cosmetic Enhancements
Implement curvature shader to make the horizon look globe-like.
Optional: distance fog or subtle visual effects at edges of repeated world.
Test from multiple perspectives (high altitude, player view, FOV).
Phase 5 — Future Extensions
Add hexagonal tiling mode for terrain generation.
Experiment with different “world tile” sizes for variety.
Multiplayer support testing: ensure tiling is consistent across clients.
Optional: additional terrain variations or “planetary” features (gravity effects, biome cycles).
Phase 6 — Packaging & Distribution
Clean up code, remove test logs.
Build .jar using Gradle.
Prepare a mod metadata file (fabric.mod.json) with name, version, dependencies.
Publish to GitHub and optionally CurseForge.