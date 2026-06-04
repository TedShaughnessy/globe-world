# Globe World Curvature Shader

Minimal Iris shader pack version of `GlobeCurvatureShader`.

It only applies the same camera-relative X/Z curvature drop, cloud drop, and
fog-position behavior as the mod's vanilla shader override.
The Fabric mod's optional Iris bridge replaces the placeholder constants in
`shaders/lib/globe_world_curvature.glsl` when Iris loads the shader pack, so
Globe World's existing curvature controls remain the source of truth.

The generic shader-pack bridge contract is documented in
`docs/mod-compatibility/iris-shader-packs.md`.
