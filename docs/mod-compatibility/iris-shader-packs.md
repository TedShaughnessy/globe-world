# Globe World Iris Shader Pack Contract

## Scope

This note documents how Iris shader packs can consume Globe World's existing
curvature controls.

It applies to:

- the minimal shader pack in `shaderpacks/globe-world-curvature/`
- the pinned MakeUp Ultra Fast patch setup in
  `shaderpacks/makeup-ultra-fast-globe-world/`
- any other Iris shader pack that wants Globe curvature without adding its own
  curvature slider

## Bridge Behavior

Globe World keeps the existing mod UI and config as the source of truth. The
Iris bridge does not add shader-pack options and does not require shader packs
to read Java state directly.

When Iris is present, `IrisProgramSourceMixin` inspects shader program source as
Iris loads it. If the source contains Globe placeholder markers, the mixin asks
`GlobeCurvatureShader` for the current values and replaces the placeholders
with baked GLSL constants.

Values currently exposed:

- curvature radius in blocks
- curvature drop clamp
- fog distance scale

The minimal Globe shader pack provides explicit terrain, block, water, cloud,
line, entity, hand, textured fallback, armor-glint, glowing-eye, and sky program
files. Textured entity and hand passes are included so mobs, players, held
items, and omitted textured geometry do not fall back to a flat-color basic
pass. Shared fragment helpers apply fog from the curved vertex path, the cloud
fragment pass leaves vanilla cloud colors and alpha unchanged, and sky passes
are explicit pass-through shaders that preserve translucent sky textures and
horizon colors instead of inheriting the curved world fallback.

When Globe curvature settings or the active dimension change,
`GlobeIrisShaderBridge` reflectively asks Iris to reload shaders so the baked
constants are refreshed. If Iris is absent, this bridge is inactive.

## Shader Pack Requirements

Compatible shader packs should include these exact placeholder constants in a
shared GLSL file:

```glsl
const float GLOBE_WORLD_CURVATURE_RADIUS = 0.0 /*GLOBE_WORLD_CURVATURE_RADIUS_FROM_MOD*/;
const float GLOBE_WORLD_CURVATURE_DROP_CLAMP = 256.0 /*GLOBE_WORLD_CURVATURE_DROP_CLAMP_FROM_MOD*/;
const float GLOBE_WORLD_FOG_DISTANCE_SCALE = 1.0 /*GLOBE_WORLD_FOG_DISTANCE_SCALE_FROM_MOD*/;
```

The defaults are intentionally valid GLSL. If Globe World is absent or the Iris
bridge does not run, radius remains `0.0` and the shader renders flat.

The mixin currently replaces the exact default-plus-comment fragments above, so
shader packs should copy them unchanged.

## Curvature Math

Use the same camera-relative shape as `GlobeCurvatureShader`:

```glsl
vec3 globeWorld_applyCurvature(vec3 pos) {
    float radius = GLOBE_WORLD_CURVATURE_RADIUS;
    if (radius <= 0.0) {
        return pos;
    }

    float distanceSqr = dot(pos.xz, pos.xz);
    float drop = min(distanceSqr / (2.0 * radius), GLOBE_WORLD_CURVATURE_DROP_CLAMP);
    pos.y -= drop;
    return pos;
}
```

For fog, keep a separate uncurved horizontal position:

```glsl
vec3 globeWorld_fogPosition(vec3 pos) {
    if (GLOBE_WORLD_CURVATURE_RADIUS <= 0.0) {
        return pos;
    }

    return vec3(
        pos.x * GLOBE_WORLD_FOG_DISTANCE_SCALE,
        0.0,
        pos.z * GLOBE_WORLD_FOG_DISTANCE_SCALE
    );
}
```

Clouds should use the same cloud-radius adjustment as the vanilla override:

```glsl
float cloudRadius = GLOBE_WORLD_CURVATURE_RADIUS + max(pos.y, 0.0);
```

## Applying The Transform

Apply curvature after the shader has a camera-relative or player-relative
position and before projection.

For MakeUp Ultra Fast, the first patched insertion points are:

- `shaders/src/position_vertex.glsl`
- `shaders/src/position_vertex_water.glsl`
- `shaders/lib/mu_ftransform.glsl`

Preserve MakeUp's existing lighting, material, post-processing, and option
logic. The Globe patch should only change vertex position and any fog distance
that needs to match the uncurved horizontal range.

## Limitations

- The current bridge bakes constants at Iris shader load time rather than
  uploading live uniforms every frame.
- It depends on Iris program sources passing through
  `net.irisshaders.iris.shaderpack.programs.ProgramSource`.
- It does not yet provide debug overlay status for whether the active shader
  pack consumed the placeholders.
- Sodium without Iris remains separate; this bridge is for Iris shader packs.

## Key Files

- `mod-fabric/src/client/java/globe/world/client/GlobeCurvatureShader.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeIrisShaderBridge.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/IrisProgramSourceMixin.java`
- `mod-fabric/src/client/resources/globe-world.iris.mixins.json`
- `shaderpacks/globe-world-curvature/shaders/lib/globe_world_curvature.glsl`
- `shaderpacks/makeup-ultra-fast-globe-world/upstream.properties`
- `shaderpacks/makeup-ultra-fast-globe-world/patches/0001-add-globe-world-curvature.patch`
