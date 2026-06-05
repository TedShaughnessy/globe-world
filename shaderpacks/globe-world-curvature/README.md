# Globe World Curvature Shader

Minimal Iris shader pack version of `GlobeCurvatureShader`.

It only applies the same camera-relative X/Z curvature drop, cloud drop, and
fog-position behavior as the mod's vanilla shader override. Terrain, block,
water, cloud, line, and entity passes reuse the same small helper shaders so
the pack does not add lighting or material effects of its own. Shared fragment
helpers apply the fog distance produced by the curved vertex path, the cloud
fragment pass leaves vanilla cloud colors/alpha alone, glowing eye layers
render as unlit textured cutouts, and sky programs are explicit pass-through
shaders that preserve translucent sun, moon, and horizon colors instead of
falling back to the curved world programs.
The Fabric mod's optional Iris bridge replaces the placeholder constants in
`shaders/lib/globe_world_curvature.glsl` when Iris loads the shader pack, so
Globe World's existing curvature controls remain the source of truth.

The generic shader-pack bridge contract is documented in
`docs/mod-compatibility/iris-shader-packs.md`.
