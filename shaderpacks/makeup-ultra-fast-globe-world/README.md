# MakeUp Ultra Fast - Globe World Patch

This directory stores Globe World's reproducible MakeUp Ultra Fast shader-pack
patch setup.

The MakeUp source is not vendored here. Gradle fetches the pinned upstream
commit listed in `upstream.properties`, applies the patches in `patches/`, and
packages the patched shader pack from `build/shaderpacks/`.

Globe World changes should stay in small patch files so upstream MakeUp updates
remain auditable.

Current first-pass support:

- ordinary terrain, block, entity, hand, basic, glint, spider-eyes, and cloud
  vertex paths via `shaders/src/position_vertex.glsl`
- translucent water/glass path via `shaders/src/position_vertex_water.glsl`
- line and selected-block outline path via `shaders/lib/mu_ftransform.glsl`

Distant Horizons and Voxy-specific MakeUp paths are intentionally left for a
separate compatibility pass.
