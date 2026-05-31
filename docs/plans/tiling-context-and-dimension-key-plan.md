# Tiling Context And Dimension Key Plan

Status: implemented.

Implemented by scoped `DimensionTiling` wrappers in worldgen and natural
spawning, level-aware section canonicalization in `BulkSectionAccess`, and
`.equals(...)` dimension-key comparisons in packet, map, compass, lodestone,
and portal paths. Durable behavior is documented in
[Topology](../mod-mechanics/topology.md) and
[Worldgen](../mod-mechanics/worldgen.md); the validation checklist remains
useful for manual Nether/Overworld regression testing.

## Problem

Globe World has two small architecture risks that can become hard-to-debug
Nether and worldgen bugs:

1. Some code still relies on ambient tiling context through
   `DimensionTiling.currentOrOverworld()`. That helper falls back to Overworld
   tiling when no thread-local context is set. This is useful for legacy helpers
   but risky in Nether/worldgen paths where the intended dimension may not be
   Overworld.
2. Some dimension comparisons use `==` or `!=` on `ResourceKey<Level>`.
   Vanilla static dimension keys usually make this work, but `.equals(...)` is
   the correct invariant and avoids surprises in wrapped broadcast, map,
   compass, lodestone, and portal code.

The desired result is not a new feature. It is a reliability pass that makes the
dimension context explicit everywhere it is available and makes remaining
ambient context scoped and exception-safe.

## Current Hooks And Risky Patterns

Known raw tiling context paths:

- `DimensionTiling.push(...)` / `DimensionTiling.clear(...)` are unscoped.
- `ChunkGeneratorMixin` uses manual push/clear in biome decoration and
  structure-reference generation.
- `NoiseBasedChunkGeneratorMixin` uses manual push/clear around carvers and
  surface building.
- `NaturalSpawnerMixin` uses manual push/clear around random spawn position and
  spawn-category logic.
- Some helpers call `CoordUtil.wrapBlockPos(pos)`,
  `CoordUtil.wrapChunkPos(pos)`, `CoordUtil.wrapBlock(x)`, or
  `CoordUtil.wrapChunk(x)` without a level or dimension.
- `BulkSectionAccessMixin` currently canonicalizes with
  `CoordUtil.wrapBlockPos(pos)`, so it depends on whatever ambient context is
  present.

Known dimension identity comparisons:

- `PlayerListBroadcastMixin` compares `player.level().dimension() == dimension`.
- `MapItemMixin` compares `level.dimension() != data.dimension`.
- `CompassAngleStateMixin` compares `target.dimension() != level.dimension()`.
- `LodestoneTrackerMixin` compares `target.get().dimension() != level.dimension()`.
- `NetherPortalBlockMixin` compares `currentLevel.dimension() == Level.NETHER`.

## Goals

- Preserve all current wrapping behavior in the Overworld.
- Preserve Nether-specific tile sizing and terrain mode whenever Nether tiling
  is enabled.
- Preserve disabled tiling behavior for the End and untiled dimensions.
- Make scoped tiling context restore any previous context after nested calls.
- Ensure early returns, cancellation, and exceptions cannot leak or clear an
  outer tiling context.
- Replace `ResourceKey<Level>` identity checks with `.equals(...)`.
- Leave deliberate nearest-alias-only entity and world-event behavior unchanged.

## Non-Goals

- Do not change entity multi-alias rendering.
- Do not remove current diagnostics or warning logs in this pass.
- Do not resolve the pending mob, light seam, structure, or pathfinding audits.
- Do not redesign coordinate math in `CoordUtil`.

## Concrete Implementation Plan

### 1. Add A Void Scoped Context Helper

Extend `DimensionTiling` with a void helper so mixins do not need awkward
`return null` lambdas:

```java
public static void runWith(DimensionTiling tiling, Runnable action) {
    DimensionTiling previous = CURRENT.get();
    CURRENT.set(tiling);
    try {
        action.run();
    } finally {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
```

Keep existing `with(DimensionTiling, Supplier<T>)` for value-returning code.

After all call sites are migrated, make `push(...)` and `clear(...)` private if
no mixin still needs them. If private access is awkward for future mixin work,
leave them public but add comments marking them as low-level and prefer
`with(...)` / `runWith(...)`.

### 2. Convert Manual Push/Clear In Worldgen

Update `ChunkGeneratorMixin`:

- In `applyBiomeDecoration` `HEAD`, avoid leaving a pushed context active until
  `RETURN` if possible. Prefer wrapping only the operations that need ambient
  context.
- If the method body must run with context until vanilla returns, replace the
  push/return clear pair with a small per-invocation context guard that restores
  the previous context on both `RETURN` and cancellation.
- Ensure the alias-decoration cancellation path restores the previous context
  before `ci.cancel()`.
- Ensure `StructurePlacementShifts.clear()` still runs at the same lifecycle
  boundaries.

Update `ChunkGeneratorMixin.createReferences`:

- Wrap the whole implemented body in `DimensionTiling.runWith(...)`.
- Preserve the alias chunk behavior:
  - set empty references
  - cancel
  - return
- Preserve the canonical behavior:
  - call `addToroidalStructureReferences(...)`
  - cancel

Update `NoiseBasedChunkGeneratorMixin`:

- Keep the existing async `DimensionTiling.with(...)` wrappers for
  `createBiomes` and `fillFromNoise`.
- Replace carver and surface `push`/`clear` pairs with scoped restoration.
- If the vanilla method body needs context between `HEAD` and `RETURN`, use the
  same context guard pattern as `ChunkGeneratorMixin`.

Update `NaturalSpawnerMixin`:

- Replace `pushRandomSpawnPosTilingContext` /
  `clearRandomSpawnPosTilingContext` with scoped restoration.
- Replace `pushSpawnCategoryTilingContext` /
  `clearSpawnCategoryTilingContext` with scoped restoration.
- Confirm the `CoordUtil.wrapBlock(...)` calls in stored local-variable hooks
  still run while the intended level context is active.

Implementation note: Mixin `HEAD` plus `RETURN` context lifetimes are
inherently brittle because exceptions skip `RETURN` injections. Prefer wrapping
specific invocations with `@WrapOperation` when the level is available. Use a
guard only where the method body itself contains several unlevelled helper calls
and wrapping every use would be more brittle.

### 3. Reduce Ambient `CoordUtil` Usage

Run:

```bash
rg "CoordUtil\\.(wrapBlockPos|wrapChunkPos|wrapBlock|wrapChunk|virtualBlock|virtualChunk|wrappedDeltaBlock|wrappedDistance|tileAlias|isInCanonicalTile)\\(" src/main/java src/client/java
```

For each call without a level, dimension, or explicit `DimensionTiling`:

- If a `Level`, `ServerLevel`, `ClientLevel`, `LevelAccessor.getLevel()`, or
  `ResourceKey<Level>` is available in the method, switch to the explicit
  overload.
- If the call is inside a worldgen/noise path that genuinely lacks a level,
  keep the ambient overload but make sure a scoped context is set by the
  enclosing hook.
- If the call is inside a client visual path and the active client level is
  available, prefer `DimensionTiling.forLevel(level)` or the level-aware
  overload.

Specific first-pass targets:

- `BulkSectionAccessMixin`: replace `CoordUtil.wrapBlockPos(pos)` only if a
  dimension or level can be obtained from vanilla `BulkSectionAccess`. If not,
  document that this mixin requires an enclosing worldgen scoped context.
- `NaturalSpawnerMixin`: where `Level level` or `ServerLevel level` is present,
  use level-aware overloads instead of ambient overloads.
- Noise and terrain utilities may remain ambient because their call sites are
  often vanilla noise samplers without level parameters. Their enclosing
  generator hooks must provide the scoped context.

### 4. Replace Dimension Identity Checks

Use `.equals(...)` for all `ResourceKey<Level>` comparisons.

Concrete replacements:

- `player.level().dimension() == dimension`
  -> `player.level().dimension().equals(dimension)`
- `level.dimension() != data.dimension`
  -> `!level.dimension().equals(data.dimension)`
- `target.dimension() != level.dimension()`
  -> `!target.dimension().equals(level.dimension())`
- `target.get().dimension() != level.dimension()`
  -> `!target.get().dimension().equals(level.dimension())`
- `currentLevel.dimension() == Level.NETHER`
  -> `Level.NETHER.equals(currentLevel.dimension())`

Then run:

```bash
rg "dimension\\(\\)\\s*[!=]=|[!=]=\\s*Level\\." src/main/java src/client/java
```

Review every remaining hit. Static-key checks such as `Level.OVERWORLD.equals`
are preferred.

### 5. Compile And Fix Mixin Shape Issues

Ask the user to run:

```bash
./gradlew build
```

Likely compile issues to watch for:

- `Runnable` lambda cannot contain checked-return flow from a cancellable
  injection. If so, use a tiny private helper method that returns an enum or
  boolean describing whether to cancel.
- Mixin local capture may differ after refactoring. Keep target signatures and
  injection points unchanged unless the compiler or Mixin annotation processor
  requires otherwise.
- If `push(...)` / `clear(...)` are made private, confirm no remaining mixins or
  utility classes call them.

## Validation

Manual validation should cover dimension-specific behavior rather than only
Overworld success:

1. Overworld square tiling enabled: create/load chunks across a seam and confirm
   block edits still affect aliases.
2. Nether tiling disabled: portal to Nether and confirm vanilla non-wrapped
   behavior.
3. Nether tiling enabled with one-eighth size: confirm Nether chunk wrapping uses
   the Nether effective tile, not the Overworld tile.
4. End dimension: confirm it remains untiled.
5. Generate new chunks near Overworld and Nether tile edges; inspect obvious
   seams, features, and structures.
6. Trigger natural spawning near a tile edge in Overworld and Nether.
7. Use maps, lodestone compass, recovery compass, spawn compass, sounds, and
   block events across a seam.

Debug/audit commands after implementation:

```bash
rg "DimensionTiling\\.push|DimensionTiling\\.clear" src/main/java src/client/java
rg "dimension\\(\\)\\s*[!=]=|[!=]=\\s*Level\\." src/main/java src/client/java
rg "CoordUtil\\.(wrapBlockPos|wrapChunkPos|wrapBlock|wrapChunk)\\([^,)]*\\)" src/main/java src/client/java
```

Expected outcome:

- No production call sites use raw `push(...)` or `clear(...)`, unless those
  methods remain only for tightly documented low-level use.
- No `ResourceKey<Level>` identity comparisons remain.
- Remaining ambient `CoordUtil` calls are either in noise/worldgen paths with an
  enclosing scoped context or are intentionally documented.

## Documentation Updates After Implementation

When implemented, update:

- [../mod-mechanics/topology.md](../mod-mechanics/topology.md): document that
  level-aware helpers are preferred and ambient tiling context is limited to
  scoped worldgen/noise paths.
- [../mod-mechanics/worldgen.md](../mod-mechanics/worldgen.md): note that
  worldgen ambient context is restored after nested calls and exceptions.
- This plan status: mark implemented, or keep it with any remaining validation
  notes if Nether/worldgen manual testing is still pending.
