# MakeUp Ultra Fast Curvature Compatibility

## Goal

Make Globe World's curvature work when users render with Sodium, Iris, and a
patched MakeUp Ultra Fast shader pack while keeping Globe World's existing
curvature UI as the single source of truth.

The intended user experience is:

1. Install Globe World, Sodium, Iris, and Fabric API.
2. Put a Globe-compatible MakeUp shader pack zip in `shaderpacks/`.
3. Select the shader pack in Iris.
4. Configure curvature only through Globe World's existing world/options UI.

## Current Behavior

Globe World currently curves vanilla world rendering by rewriting selected
Minecraft core vertex shaders in `ShaderManagerMixin` and
`GlobeCurvatureShader`. The rewritten shaders bake in the active curvature
radius, drop clamp, and fog distance scale at resource-load time.

This does not cover two important modded rendering paths:

- Sodium's chunk renderer does not use vanilla's terrain core shader for chunk
  terrain, even when no Iris shader pack is active.
- Iris shader packs such as MakeUp Ultra Fast provide their own vertex shader
  programs, so Globe World's vanilla shader rewrite does not reach them.

## Desired Architecture

Split the compatibility into three related deliverables plus a repository
layout pass.

1. A small Globe shader pack that only applies Globe World's existing curvature
   math. This gives Sodium users a shader-pack route through Iris without
   needing MakeUp Ultra Fast.
2. A pinned MakeUp Ultra Fast source checkout with local Globe curvature
   patches applied during packaging.
3. Continued maintenance of the existing Globe World Fabric mod as the source
   of truth for curvature settings, renderer status, and values exposed to
   compatible shader packs.
4. A future monorepo-style layout that can produce the Fabric mod jar and
   shader-pack zips from one workspace while keeping shader patches auditable.

### Generic Curvature Render Hook

Create a small Globe World client-side abstraction for render curvature support
so the rest of the mod does not need to know whether vanilla shaders, Sodium,
Iris, or a shader pack is currently responsible for terrain rendering.

Possible shape:

```java
interface GlobeCurvatureRenderHook {
    String id();
    boolean isRendererPresent();
    boolean isActive();
    boolean supportsCurvature();
    boolean supportsDynamicUniforms();
    void publish(GlobeCurvatureRenderState state);
}
```

The state would contain the values already computed by Globe World:

- dimension key
- curvature enabled flag
- curvature radius in blocks
- curvature drop clamp
- fog distance scale
- settings version

Renderer-specific adapters then register behind that interface:

- vanilla core shader adapter
- Sodium chunk-renderer adapter
- Iris shader-pack adapter
- optional MakeUp shader-pack capability detector

This keeps the hook generic from Globe World's point of view while still
allowing renderer-specific implementation details where unavoidable.

### Globe World Mod Support

Add optional shader-compatibility support in Globe World:

- Detect Iris and/or Sodium only when present.
- Track which curvature render hook is currently active.
- Expose debug/status text showing the active renderer hook and whether it
  supports curvature.
- Expose active Globe curvature values to compatible shader programs:
  - curvature enabled flag
  - curvature radius in blocks
  - curvature drop clamp
  - fog distance scale if the shader pack chooses to match Globe's tiny-tile fog
- Keep the existing Globe World curvature UI and config as the only place users
  set the curvature amount.
- Reload or update shader uniforms when the active dimension or curvature
  settings change.

If Iris has a stable custom uniform API for Fabric/this Minecraft version, use
that first. If not, investigate a narrowly scoped Iris mixin. Avoid a generic
shader-source rewrite until the MakeUp-specific patch proves the math and user
experience.

Sodium-without-Iris is a separate compatibility pass. It likely needs a Sodium
chunk shader or vertex-transform mixin so ordinary Sodium terrain receives the
same camera-relative drop.

Capability detection should be explicit rather than guessed from installed
mods. For example, if Sodium is installed but the Sodium adapter has not been
implemented for the active Sodium version, Globe World should report:

```text
Renderer: Sodium
Curvature hook: unsupported
```

If Iris is active with a shader pack that declares or exposes Globe curvature
support, Globe World should report:

```text
Renderer: Iris shader pack
Curvature hook: supported
Shader pack: MakeUp Ultra Fast - Globe World Edition
```

This status can live in the Globe debug overlay first, then become a settings
screen hint if it proves useful.

### Shader Pack Capability Signal

Compatible shader packs should include a simple capability signal so Globe
World can tell the difference between an arbitrary Iris shader pack and one
that knows how to consume Globe curvature values.

Candidate signals:

- a small marker file in the shader pack, such as
  `shaders/globe_world.properties`
- a shader-pack property in `shaders.properties`
- a required custom uniform name that Globe World can inspect after Iris loads
  the pack

Prefer a marker file or property if Iris exposes enough resource-pack metadata
to read it. Uniform-name inspection is a fallback because it is more coupled to
Iris internals.

Example marker content:

```properties
globeWorld.curvature=true
globeWorld.curvature.version=1
globeWorld.curvature.uniforms=radius,dropClamp,fogDistanceScale
```

### Simple Globe Curvature Shader Pack

Create a minimal shader pack whose only job is to reproduce Globe World's
existing vanilla curvature shader behavior through the Iris shader-pack path.
This is the Sodium compatibility bridge: Sodium users can install Sodium, Iris,
and the simple Globe shader pack, then keep using Globe World's own curvature
UI.

The simple shader pack should:

- Apply the same camera-relative X/Z distance drop as `GlobeCurvatureShader`.
- Avoid MakeUp-specific lighting, post-processing, and style changes.
- Preserve flat rendering when Globe curvature is disabled or Globe-provided
  values are unavailable.
- Include the Globe shader-pack capability marker.
- Be small enough to use as a reference implementation for future shader-pack
  compatibility work.

This pack should be developed before broad MakeUp patching because it isolates
the Iris uniform/capability plumbing from MakeUp's much larger rendering
pipeline.

First pass: `shaderpacks/globe-world-curvature/` contains only the standalone
curvature shader-pack path, and `IrisProgramSourceMixin` bakes the existing
Globe World curvature controls into its GLSL placeholders when Iris is present.
The generic placeholder contract is documented in
`../mod-compatibility/iris-shader-packs.md`.

### MakeUp Shader Pack Patch

Maintain a patched MakeUp Ultra Fast shader pack that consumes Globe-provided
values and applies the same curvature math as Globe's vanilla shader rewrite.

First pass: `shaderpacks/makeup-ultra-fast-globe-world/` now stores the
upstream repository and pinned commit in `upstream.properties`, plus
`patches/0001-add-globe-world-curvature.patch`. The root Gradle build can fetch
that exact MakeUp source, apply the Globe patch, and package an unversioned zip
from `build/shaderpacks/`.

High-value MakeUp files:

- `shaders/src/position_vertex.glsl`: main terrain, block, entity, hand, cloud,
  basic, glint, and spider-eyes positioning path.
- `shaders/src/position_vertex_water.glsl`: translucent water/glass positioning
  path.
- `shaders/lib/mu_ftransform.glsl`: line and block-outline positioning path.

The initial patch also adds `shaders/lib/globe_world_curvature.glsl`, using
the same placeholder constants as the minimal Globe shader pack, and includes
that helper from MakeUp's shared vertex entry points.

The shader-pack patch should:

- Apply curvature in camera-relative/player-relative vertex space before
  projection.
- Fall back to uncurved rendering when Globe World is absent, uniforms are
  unavailable, or curvature radius is `0`.
- Prefer an `Auto / Off` compatibility option over a duplicate curvature
  slider.
- Keep MakeUp's normal options, profiles, and visual behavior unchanged except
  where curvature requires adjustment.

## Shader Math

Use the same shape as `GlobeCurvatureShader`:

```glsl
vec3 globeWorld_applyCurvature(vec3 pos) {
    if (globeWorld_curvatureRadius <= 0.0) {
        return pos;
    }

    float distanceSqr = dot(pos.xz, pos.xz);
    float drop = min(
        distanceSqr / (2.0 * globeWorld_curvatureRadius),
        globeWorld_curvatureDropClamp
    );
    pos.y -= drop;
    return pos;
}
```

Where possible, preserve uncurved horizontal distance for fog calculations so
MakeUp's fog does not treat the downward bend as extra depth. Globe World's
vanilla shader path uses a separate fog position for this reason.

## Repository Layout Plan

Globe World now uses a small monorepo-style layout for the mod itself. The
root project aggregates build tasks, while the current Fabric implementation
lives in `mod-fabric/`. Shader projects and a shared `mod-common/` module are
deferred until they have real contents.

Distant Horizons is a useful reference because its sibling repo splits common
Minecraft-dependent code from loader-specific projects:

- `coreSubProjects/` and `common/` hold shared code.
- `fabric/`, `forge/`, and `neoforge/` are loader-specific modules.
- `settings.gradle` includes only the selected loader modules.

Globe World does not need Forge or NeoForge support now, but it can still
benefit from a similar separation:

```text
globe-world/
  settings.gradle
  build.gradle                         root aggregation and shared config
  mod-fabric/                          Fabric entrypoints, mixins, resources
  docs/

shaderpacks/
  globe-world-curvature/               minimal reference shader pack
  makeup-ultra-fast-globe-world/       upstream pin and local patches
```

Deferred layout additions:

- Add `mod-common/` only when a loader-neutral boundary is clear.
- Add versioned shader-pack release metadata once the manual bump workflow is
  designed.
- Do not add Forge/NeoForge modules unless there is a real support goal.

The root artifact goal would eventually produce:

- the Globe World Fabric mod jar
- the simple Globe curvature shader-pack zip
- the MakeUp Ultra Fast Globe World shader-pack zip

Recommended MakeUp source workflow:

```text
shaderpacks/makeup-ultra-fast-globe-world/
  upstream.properties
  patches/
    0001-add-globe-world-curvature.patch
```

The current pinned upstream is:

```text
repo=https://github.com/javiergcim/MakeUpUltraFast.git
commit=ab8b7ccfb8126a577630c704e7ed9e64dfb4cb82
```

Typical workflow:

1. Fetch the pinned upstream MakeUp commit.
2. Apply Globe patch files in lexical order.
3. Test the generated shader pack.
4. When MakeUp updates, bump `upstream.properties`, refresh patches against the
   new upstream, then rebuild the shader pack zip.

Useful commands:

```bash
./gradlew packageGlobeWorldCurvatureShaderpack
./gradlew packageMakeupUltraFastGlobeWorldShaderpack
./gradlew shaderpacks
```

Use the patch files to see exactly what changed from MakeUp's upstream code.
That patch stack is the audit trail: it keeps Globe curvature changes separate
from upstream MakeUp changes.

Current package outputs:

```text
build/distributions/shaderpacks/globe-world-curvature-mcMINECRAFT_VERSION-VERSION.zip
build/distributions/shaderpacks/makeup-ultra-fast-globe-world-mcMINECRAFT_VERSION-VERSION.zip
```

Future versioned releases should tag each artifact independently:

- target Minecraft version.
- Globe World Fabric mod version.
- minimal Globe curvature shader-pack version.
- MakeUp Ultra Fast Globe World shader-pack version.
- upstream MakeUp commit or release used as the base.
- Globe World mod version tested.
- Iris/Sodium versions tested.

Because MakeUp Ultra Fast is LGPLv3, distributed modified packs must preserve
the license and credits and make the modified source available. The pinned
upstream commit plus local patch files satisfies the practical source-tracking
need when published with the release artifacts.

## Implementation Steps

1. Add the generic Globe curvature render hook abstraction and debug reporting
   in the `mod-fabric` layout.
2. Add Globe World mod support for dynamic shader-pack values through Iris if a
   stable API is available.
3. Build a minimal Globe curvature shader pack that consumes those values and
   reproduces the existing mod shader math.
4. Add a shader-pack capability marker and detect it through the Iris
   adapter.
5. Validate the simple shader pack with Sodium and Iris.
6. Prove the MakeUp GLSL patch generated from the pinned upstream commit.
7. Verify the visual result for terrain, entities, water, selected-block
   outlines, fog, and hand rendering.
8. Expand or adjust the patch based on visual validation.
9. Package the patched shader pack as a zip with license, credits, and release
   notes.
10. Document user installation in Globe World docs and the shader-pack repo.
11. Only after the shader artifacts are real, decide whether to extract shared
    code into a `mod-common` module.
12. Investigate Sodium-without-Iris terrain curvature separately.

## Validation

- With Sodium disabled and no shader pack, existing vanilla curvature still
  works.
- With Sodium, Iris, and the simple Globe shader pack enabled, terrain follows
  Globe World's curvature UI.
- With Sodium, Iris, and patched MakeUp enabled, terrain follows Globe World's
  curvature UI without a duplicate shader-pack curvature setting.
- With Sodium enabled and no Iris shader pack, known behavior is documented
  until Sodium-specific compatibility is implemented.
- Globe World can report whether the active renderer/shader path supports
  curvature.
- Sodium being installed is not treated as proof that Sodium curvature support
  exists for the active version.
- Iris being active is not treated as proof that the selected shader pack
  supports Globe curvature.
- Curvature `0%` renders flat through the patched shader pack.
- Changing dimension or curvature setting updates shader behavior after reload
  or uniform update.
- Selected-block outlines remain aligned with curved terrain.
- Water and translucent surfaces are curved consistently enough for ordinary
  play.

## Open Questions

- Does Iris expose a suitable custom uniform API for Minecraft 26.1.2, or will
  Globe World need a targeted mixin?
- What is the least brittle way to identify the selected Iris shader pack and
  read a Globe compatibility marker?
- Should renderer-hook status appear only in the debug overlay, or also in the
  settings UI near the curvature controls?
- Should Globe World publish raw radius/drop values only, or also helper values
  for fog and cloud curvature?
- Should the patched MakeUp pack include Distant Horizons and Voxy paths, or
  should those remain separate compatibility projects?
- How much shadow and screen-space reflection correction is necessary for a
  first usable release?
- Can Sodium's no-shader-pack chunk path share the same values and math, or
  does it need its own compatibility abstraction?

## Success Criteria

- Users can install a Globe-compatible MakeUp zip and use the existing Globe
  World curvature UI without manual shader edits.
- The Globe changes remain easy to inspect in the patch stack.
- Upstream MakeUp updates can be merged with small, understandable conflicts.
- License, credits, and modified source availability are handled cleanly.
