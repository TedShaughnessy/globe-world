# Globe World

Globe World is a Fabric mod for **Minecraft 26.1.2** that makes a finite world
tile feel continuous. Walk past an edge and the world wraps around instead of
ending, giving the impression of a small globe.

## Downloads

Published builds are attached to [GitHub Releases](../../releases):

- `globe-world-mc26.1.2-VERSION.jar`: the Fabric mod.
- `globe-world-curvature-mc26.1.2-VERSION.zip`: minimal Iris curvature
  shader pack.
- `makeup-ultra-fast-globe-world-mc26.1.2-VERSION.zip`: MakeUp Ultra Fast
  with Globe World curvature support.


## Install

Requirements:

- Minecraft 26.1.2
- Fabric Loader 0.19.2 or newer
- Fabric API
- Java 25 or newer

Put `globe-world-mc26.1.2-VERSION.jar` in `mods/`, then create a new world
and open the **Globe World** tab. Enable Globe World, choose a tile size, and
adjust optional Nether, curvature, and day-cycle settings.

For multiplayer, install the mod on the server. Client installs are needed for
the creation/options UI and built-in curvature visuals.

## Shader Packs

The client mod includes vanilla-renderer curvature. Iris users can also place a
published Globe World shader-pack zip in `shaderpacks/`; the shader pack reads
the mod's curvature settings when Iris and Globe World are both present.

## Notes

Small tile sizes are intentionally toy-like. Larger tiles are better for normal
survival worlds that should feel familiar but finite.

Globe World changes core coordinate and chunk behavior, so mods that cache
positions, generate terrain, track entities, or render distant terrain may need
testing. See [Distant Horizons notes](docs/mod-compatibility/distant-horizons.md)
for the current DH investigation.

Developers should start with [DEVELOPMENT.md](DEVELOPMENT.md).

## License

Globe World is released under [CC0-1.0](LICENSE).
