# Globe World + Distant Horizons Fabric Compatibility

## Scope

This note only considers the Fabric version of Distant Horizons and the sibling
`../globe-world` mod.

The goal is not to make Distant Horizons natively Globe World-aware or perfectly
optimized. The practical question is whether the two mods can work together well
enough, and what work would be needed if they do not.

## Current Read

These mods have a plausible chance of working together acceptably, especially
with Globe World curvature disabled.

The strongest reason for optimism is that Globe World makes terrain tileable at
the generation level. Distant Horizons is mostly summarizing and rendering
terrain, so when DH asks Minecraft for terrain chunks, it should often receive
terrain that already lines up across Globe World tile boundaries.

Globe World also serves normal-looking alias chunks to the client. DH can learn
from those chunks in the same way it learns from ordinary client chunks. That
means nearby alias-derived LOD data may work without a special DH integration.

Some duplicate DH LOD data is likely. That matches Globe World's own client-side
alias duplication model and is probably acceptable. Because Globe World
frequently resets/rebases coordinates back into the canonical tile, this
duplication should usually be bounded rather than growing with unlimited alias
exploration.

## Likely Good Enough Out Of The Box

- Terrain generation should be tileable because Globe World patches worldgen,
  not just rendering.
- DH may store/render LOD data at alias coordinates and still look coherent.
- Duplicate DH cache data is likely a storage/performance cost, not necessarily
  a correctness blocker.
- With Globe World curvature disabled, there may be no meaningful shader clash.
- Large tiles and ordinary play near the canonical tile are the best-case test
  conditions.

## Main Things That Could Still Break

- **Tile-edge LOD selection:** DH chooses LOD sections using ordinary X/Z
  distance, not Globe World's wrapped distance. Near a tile edge, DH may fail to
  ask for the closest wrapped terrain or may render a farther alias.
- **Cache/key duplication:** DH may store the same terrain under canonical and
  alias section positions. This is probably tolerable, but should be measured.
- **Update invalidation:** canonical block/chunk updates may not invalidate every
  DH alias section that represents the same terrain.
- **Small tile behavior:** small tiles stress alias duplication and wrapped
  distance assumptions the most.
- **Curvature:** Globe World rewrites vanilla shaders, but DH has its own LOD
  shaders. If Globe curvature is enabled, DH terrain will not automatically bend
  with vanilla terrain.

## Shader Compatibility

Fabric mod order is not a reliable "Globe World loads second and overwrites DH"
mechanism.

Globe World's current shader patch works by mixing into Minecraft's
`ShaderManager.loadShader` and rewriting selected vanilla shader source.
Distant Horizons' OpenGL LOD shader is loaded differently:

- `GlDhTerrainShaderProgram` uses
  `assets/distanthorizons/shaders/shared/gl/standard.vert`.
- `GlShader.loadFile` reads it with the Java classloader via
  `getResourceAsStream`.
- That does not pass through Minecraft's `ShaderManager`, so Globe World's
  existing vanilla shader rewrite will not affect it.

This is only a problem if Globe World curvature should affect DH terrain. If
Globe curvature is disabled, shader compatibility is probably not the main
issue.

Possible curvature support options:

- leave Globe curvature disabled when using DH
- manually configure DH's own `earthCurveRatio` as a rough approximation
- add a targeted Globe World compatibility mixin into DH shader loading or
  `GlDhTerrainShaderProgram.fillUniformData`
- implement a full DH `IDhApiShaderProgram` override
- test DH's newer Blaze renderer separately, since its shader identifiers may be
  closer to Minecraft's resource-managed path

## Support Work If Needed

Start with the smallest useful compatibility layer:

1. **Runtime test first**
   Run both mods with Globe curvature disabled. Check whether DH renders LODs,
   whether tile edges look acceptable, and whether DH cache growth stays
   reasonable after crossing tile boundaries.

2. **Measure duplicate LOD data**
   Inspect DH's saved LOD data before and after repeated canonical rebasing and
   tile-edge crossings. If duplication stays bounded, do not optimize it early.

3. **Fix wrapped-distance problems only if visible**
   If tile edges produce missing or wrong LODs, add a DH-side or Globe-side hook
   that makes DH's LOD selection use Globe World's wrapped distance or nearest
   alias coordinates.

4. **Handle update fanout if stale LODs appear**
   If block changes update the canonical chunk but leave alias LODs stale, add
   invalidation/fanout from Globe World canonical updates to affected DH alias
   sections.

5. **Add shader support only for curved presentation**
   If Globe curvature is required, patch or override DH's terrain shader path.
   This is presentation work, not required for flat/tileable terrain support.

## Practical Verdict

The mods are worth testing together before building a large integration.

Expected best first configuration:

- Fabric
- Globe World tiling enabled
- Globe World curvature disabled
- Distant Horizons enabled normally
- large or medium tile size first, then small tile stress tests

If that works, the only necessary support may be small targeted fixes for
wrapped tile-edge selection, cache invalidation, or optional curvature.

