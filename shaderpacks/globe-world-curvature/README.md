# Globe World Curvature Shader

Minimal Iris shader pack version of `GlobeCurvatureShader`.

It only applies the same camera-relative X/Z curvature drop, cloud drop, and
fog-position behavior as the mod's vanilla shader override. Terrain, block,
water, entity, hand, textured fallback, and armor-glint passes reuse the same
small helper shaders so the pack does not add lighting or material effects of
its own. The cloud pass uses its own vertex helper so cloud curvature is always
handled by the cloud shader path. The line pass uses a dedicated vertex helper
because Iris widens modern vanilla line vertices, including fishing rod strings,
from duplicated line vertices; the helper curves both endpoints before applying
screen-space widening. Shared fragment helpers apply the fog distance
produced by the curved vertex path, the cloud fragment pass preserves vanilla
cloud colors/alpha when available and falls back to Iris sky/fog tint when the
incoming cloud color is black, glowing eye layers and unlit textured fallback
passes render as textured cutouts, and sky programs preserve translucent sun
and moon textures while blending the basic sky horizon toward vanilla fog color
so curved terrain does not expose a dark daytime lower sky disc.
The Fabric mod's optional Iris bridge replaces the placeholder constants in
`shaders/lib/globe_world_curvature.glsl` when Iris loads the shader pack, so
Globe World's existing curvature controls remain the source of truth.

The generic shader-pack bridge contract is documented in
`docs/mod-compatibility/iris-shader-packs.md`.
