# Command And Admin Coordinate Policy Plan

This plan resolves the command/admin coordinate gap raised in the
[Minecraft coordinate coverage audit](minecraft-coordinate-coverage-audit.md).

## Problem

Minecraft commands use raw coordinates heavily. Lower-level Globe hooks often
canonicalize final block/entity state access, but command iteration, loaded
checks, selector geometry, success messages, and stored metadata can remain raw.

That is not automatically wrong. Commands are admin tools, test tools, and data
manipulation tools, so predictable raw vanilla behavior may be preferable to
transparent topology in many cases.

## Goals

- Document an explicit policy for vanilla commands.
- Decide which needs are better served by `/globeworld` commands instead of
  patching vanilla.
- Add topological command affordances only where they help testing or avoid
  user-hostile behavior.
- Make spawn protection, world border, spawnpoint, and worldspawn behavior
  explicit.

## Non-Goals

- Making every vanilla command topology-aware.
- Hiding raw coordinates from operators.
- Changing command behavior in untiled dimensions.

## Vanilla Source Anchors

- `net/minecraft/server/commands/FillCommand.java`
- `net/minecraft/server/commands/SetBlockCommand.java`
- `net/minecraft/server/commands/PlaceCommand.java`
- `net/minecraft/server/commands/LocateCommand.java`
- `net/minecraft/server/commands/ForceLoadCommand.java`
- `net/minecraft/server/commands/SetSpawnCommand.java`
- `net/minecraft/server/commands/SetWorldSpawnCommand.java`
- `net/minecraft/server/commands/TeleportCommand.java`
- `net/minecraft/server/commands/SummonCommand.java`
- `net/minecraft/server/commands/SpreadPlayersCommand.java`
- `net/minecraft/server/level/ServerLevel.java`

## Current Globe Anchors

- `GlobeDebugCommands`
- `TopologyContext`
- `PlayerCanonicalizer`
- `ServerGamePacketListenerImplMixin`
- `ServerPlayerGameModeMixin`
- `topology.md`
- `entities.md`

## Recommended Policy

Keep vanilla commands raw by default. Add Globe-specific commands for topology
inspection, canonical/alias teleports, and test fixtures.

Rationale:

- Operators often expect exact raw coordinate control.
- Vanilla command output is useful for debugging raw client/server state.
- Many commands iterate regions, load chunks, or mutate metadata in ways that
  would become ambiguous on a finite torus.
- Globe-specific commands can name canonical and alias behavior directly.

## Policy Table

| Command family | Proposed policy | Notes |
| --- | --- | --- |
| `setblock`, `fill`, `clone`, `fillbiome` | Raw vanilla iteration; lower-level state access may canonicalize | Consider `/globeworld fill_canon` only if testing needs it. |
| `place`, `locate` | Raw vanilla | Structure query/persistence has its own worldgen audit. |
| `forceload` | Raw vanilla | Alias chunks should not become durable canonical ownership through command policy. |
| `teleport`, `spreadplayers` | Raw vanilla plus existing `/globeworld teleport_*` helpers | Raw alias positions are useful during testing. |
| `summon` | Raw vanilla input; entity canonicalizer owns final non-player storage | Document canonicalization side effect. |
| selectors | Raw vanilla unless a specific `/globeworld` diagnostic needs wrapped selectors | Global selector topology is high-risk. |
| `spawnpoint`, `setworldspawn` | Raw vanilla metadata for now | Player lifecycle hooks canonicalize at selected boundaries; document exact behavior. |
| `worldborder` | Raw vanilla | Globe tile border is separate from vanilla world border. |
| `raid`, `debug`, `data`, `loot` | Raw vanilla | Patch only if a user-facing bug appears. |

## Spawn Protection And World Border

`ServerLevel.mayInteract(...)` asks vanilla spawn protection and world border
about the raw block position. Existing interaction mixins can retry canonical
positions for alias actions, but the policy should be explicit:

Option 1:

- Treat spawn protection and world border as raw admin boundaries.
- Alias interaction can be blocked if the visible alias is outside those raw
  boundaries.

Option 2:

- Treat spawn protection as canonical tile policy.
- Wrap `mayInteract(...)` for tiled dimensions so protection tests canonical
  block positions.

Recommended first choice: Option 2 for ordinary player block interaction, with
clear docs, because a visible alias of a protected canonical block should not
change protection status by tile offset. Keep vanilla world border raw unless
the user configures it to match the tile.

## New `/globeworld` Affordances

Add only after a concrete need appears:

- `/globeworld query_block <pos>`: print raw, canonical, alias, loaded aliases,
  block state, block entity, and mutation permission.
- `/globeworld fill_canon <from> <to> <block>`: canonical-tile bounded fill for
  regression setup.
- `/globeworld clone_canon ...`: only if clone tests become frequent.
- `/globeworld summon_alias <tileX> <tileZ> <entity>`: summon at an alias but
  report final canonical storage.
- `/globeworld select_wrapped ...`: deferred unless diagnostics need it.

## Concrete Implementation Plan

### 1. Document The Policy First

Add `docs/mod-mechanics/commands.md` and link it from
`docs/mod-mechanics/README.md`.

Document these rules:

- vanilla command coordinate parsing, region iteration, selectors, success
  messages, and metadata remain raw unless a specific Globe command says
  otherwise;
- lower-level block/entity hooks may still canonicalize the final state access;
- `/globeworld teleport_canon`, `teleport_border`, and `teleport_alias` are the
  supported topology-aware teleport tools;
- vanilla world border remains raw;
- ordinary player spawn protection is evaluated against the canonical block
  owner in tiled dimensions;
- `spawnpoint` and `setworldspawn` store raw vanilla metadata until a separate
  feature changes that.

Risk mitigation:

- Make the raw/default policy explicit before adding new mutating commands, so
  later command hooks do not silently change operator expectations.

### 2. Add An Interaction Permission Helper

Create `mod-fabric/src/main/java/globe/world/util/GlobeInteractionPermissions.java`.

Public API:

- `static boolean mayInteract(ServerLevel level, Entity entity, BlockPos rawPos, BooleanSupplier vanillaMayInteract)`
- `static PermissionView inspect(ServerLevel level, Entity entity, BlockPos rawPos)`

`PermissionView` fields:

- `BlockPos rawPos`
- `BlockPos canonicalPos`
- `boolean topologyEnabled`
- `boolean spawnProtectedAtRaw`
- `boolean spawnProtectedAtCanonical`
- `boolean rawInsideWorldBorder`
- `boolean allowed`

Implementation:

- If topology is disabled, delegate to `vanillaMayInteract.getAsBoolean()`.
- If `entity` is not a player, delegate to vanilla.
- For players in tiled dimensions:
  - test world border at the raw visible position with
    `level.getWorldBorder().isWithinBounds(rawPos)`;
  - test spawn protection at the canonical position with
    `level.getServer().isUnderSpawnProtection(level, canonicalPos, player)`;
  - return `rawInsideWorldBorder && !spawnProtectedAtCanonical`.

Risk mitigation:

- Do not mix raw and canonical checks implicitly in each mixin; centralize the
  policy in this helper.
- Keep the helper independent from MixinExtras types; mixins pass
  `() -> original.call(level, entity, pos)`.
- Preserve raw world-border behavior so world-border commands do not become
  topology settings by accident.
- Keep diagnostics available through `PermissionView` rather than logging every
  interaction.

### 3. Replace Existing Interaction Wraps

Update existing mixins:

- `ServerGamePacketListenerImplMixin`
  - `handleUseItemOn` should call `GlobeInteractionPermissions.mayInteract(...)`
    instead of raw `original.call(...)`.
  - Keep the existing alias mutation ticking guard through
    `ClientActionDiagnostics.shouldRejectAliasMutation(...)`.
- `ServerPlayerGameModeMixin`
  - `handleBlockBreakAction` should use the same helper before the mutation
    ticking guard.

Acceptance criteria:

- Breaking/placing/using an alias of a spawn-protected canonical block is
  rejected even if the raw alias is outside the vanilla spawn-protection radius.
- Breaking/placing/using an alias outside the vanilla world border remains
  rejected even if its canonical owner is inside the border.
- Untiled dimensions keep vanilla `mayInteract`.

### 4. Add `/globeworld query_block`

Extend `GlobeDebugCommands`.

Command:

```text
/globeworld query_block <pos>
```

Output fields:

- dimension;
- raw block and chunk;
- canonical block and chunk;
- tile alias X/Z;
- block state at canonical position;
- block entity type at canonical position, if present;
- loaded aliases for the canonical chunk for the executing player, if the source
  is a player;
- alias mutation access from `TopologyContext.aliasMutationAccess(...)`;
- permission view from `GlobeInteractionPermissions.inspect(...)`, if the source
  has an entity.

Risk mitigation:

- This is read-only and helps explain raw vanilla command behavior before any
  canonical mutating commands are added.

### 5. Optional Canonical Mutators

Implement these only if manual regression setup remains painful after
`query_block`:

- `/globeworld fill_canon <from> <to> <block>`
- `/globeworld summon_alias <tileX> <tileZ> <entity>`

Rules:

- Require gamemaster permission.
- Reject regions larger than vanilla `fill` limits.
- Canonicalize every target before mutation and dedupe canonical blocks.
- Report both raw requested bounds and canonical affected count.
- Do not add wrapped selectors in this phase.

## Implementation Phases

### Phase 1: Document Policy

Move the chosen policy into `docs/mod-mechanics/topology.md` or a new
`docs/mod-mechanics/commands.md`.

Acceptance criteria:

- Users can tell which command behavior is raw and which is topological.
- Spawn protection/world border behavior is named.

### Phase 2: Interaction Permission Audit

Inspect `ServerGamePacketListenerImplMixin` and `ServerPlayerGameModeMixin`
against vanilla `ServerLevel.mayInteract(...)`. Decide whether to canonicalize
permission checks inside the existing hooks or add a small helper.

Acceptance criteria:

- Block place/break/use at an alias obeys the same spawn-protection policy as
  the canonical owner.

### Phase 3: Add Diagnostics Only

Prefer diagnostics before mutation commands. `query_block` is the most useful
first command because it explains confusing command and interaction behavior
without changing policy.

### Phase 4: Optional Canonical Mutators

Add canonical fill/clone/summon helpers only if manual seam regression remains
too slow with vanilla commands.

## Documentation Updates When Implemented

- Add command policy to `docs/mod-mechanics/topology.md` or
  `docs/mod-mechanics/commands.md`.
- Add any new `/globeworld` commands to `docs/mod-mechanics/topology.md`.
- Shrink the commands and spawn-protection sections in
  `minecraft-coordinate-coverage-audit.md`.
