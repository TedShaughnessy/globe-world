# Globe World + Sodium/Iris Shader Packs

Sodium and Iris are compatible with Globe World's wrapping, but they can bypass
Globe World's vanilla terrain curvature shader path. Terrain will render flat
unless the active shader pack opts into Globe World's curvature bridge.

Globe World provides two Iris examples:

- `shaderpacks/globe-world-curvature/`: a small compatibility shader pack that
  only applies Globe World's curvature and fog behavior.
- `shaderpacks/makeup-ultra-fast-globe-world/`: a patched MakeUp Ultra Fast
  setup using the same bridge.

## Bridge Pattern

Globe World's UI and config remain the source of truth. Iris shader packs opt in
by including these exact placeholders in GLSL source:

```glsl
const float GLOBE_WORLD_CURVATURE_RADIUS = 0.0 /*GLOBE_WORLD_CURVATURE_RADIUS_FROM_MOD*/;
const float GLOBE_WORLD_CURVATURE_DROP_CLAMP = 256.0 /*GLOBE_WORLD_CURVATURE_DROP_CLAMP_FROM_MOD*/;
const float GLOBE_WORLD_FOG_DISTANCE_SCALE = 1.0 /*GLOBE_WORLD_FOG_DISTANCE_SCALE_FROM_MOD*/;
```

When Iris loads a shader pack, `IrisProgramSourceMixin` replaces those markers
with values from `GlobeCurvatureShader`. When the Globe World curvature UI
changes, `GlobeIrisShaderBridge` asks Iris to reload shaders so the baked values
refresh.

Use the constants in camera-relative or player-relative vertex positions before
projection:

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

Keep fog distance based on the uncurved horizontal range:

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

Cloud paths should use a radius adjusted by cloud height:

```glsl
float cloudRadius = GLOBE_WORLD_CURVATURE_RADIUS + max(pos.y, 0.0);
```

## Limitations

- The bridge bakes constants at Iris shader load time; it is not a live uniform
  path.
- Packs must copy the placeholder constants exactly.
- The bridge only applies to Iris shader packs. Sodium without Iris does not
  consume this GLSL contract.

## Key Files

- `mod-fabric/src/client/java/globe/world/client/GlobeCurvatureShader.java`
- `mod-fabric/src/client/java/globe/world/client/GlobeIrisShaderBridge.java`
- `mod-fabric/src/client/java/globe/world/client/mixin/IrisProgramSourceMixin.java`
- `mod-fabric/src/client/resources/globe-world.iris.mixins.json`
- `shaderpacks/globe-world-curvature/shaders/lib/globe_world_curvature.glsl`
- `shaderpacks/makeup-ultra-fast-globe-world/upstream.properties`
- `shaderpacks/makeup-ultra-fast-globe-world/patches/0001-add-globe-world-curvature.patch`
