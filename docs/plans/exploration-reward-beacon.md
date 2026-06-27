# Exploration Reward Beacon

## Implemented MVP

The current implementation has shipped the first reward-beacon slice into
[Maps](../mod-mechanics/maps.md#exploration-rewards):

- Atlas Projectors remain the reward block family.
- Empty-hand use opens a client power UI with a custom name field; sneak-use
  still toggles the hologram, and the UI also exposes a free projection toggle.
- `GlobeDiscoveryRewards` derives shared Overworld points and radius caps from
  discovered pixels, discovered percentage, discovered area, tile size, and
  full completion.
- `GlobeAtlasPowerState` saves canonical Atlas loadouts and applies deterministic
  in-budget priority by most recently edited Atlas, then canonical position.
- The Atlas power UI uses a compact grey in-game panel, effect icon toggles,
  adjacent level II toggles, matching range `R`/`II`/`III` toggles, a powered
  destination tab, and disabled controls for unaffordable upgrades.
- Loaded powered Atlases apply speed, haste, and jump boost at selected level
  I or level II strength in wrapped-radius range.
- Full completion unlocks Mastered Atlas linked travel between loaded, powered,
  travel-enabled Atlases with destination discovery and safe-arrival validation,
  and awards the Mastered Atlas advancement.
- The Atlas power UI includes a discovery progress bar with reward milestone
  markers derived from `GlobeDiscoveryRewards`.

The earlier Atlas Flight, channeled travel, larger effect pool, active visual
feedback, and multiplayer ownership ideas are retired for now unless
playtesting gives them a clear reason to return.

## Goal

Make Atlas Projectors double as the exploration reward beacon. A placed Atlas
can spend shared discovery progress to emit local area effects, giving the
projector a practical reward role beyond showing the canonical map.

The mechanic needs to handle two pressures:

- players can place many Atlases, so power cannot be free per block;
- the largest tile size can be Earth-scale, so rewards cannot depend only on
  full-tile completion.

The preferred model is an **effect point budget** derived from canonical-world
discovery. Each active Atlas spends points on radius, effect count, and effect
strength. That permits one powerful Atlas, several smaller utility Atlases, or
one strong effect instead of two weaker effects.

## Concrete Design Snapshot

Implement the first version with these decisions:

- Atlas Projectors remain the block family; no new reward block.
- Empty-hand use opens the Atlas power UI.
- Sneak-use toggles the hologram projection.
- Discovery creates a shared Overworld point budget.
- Each active Atlas loadout spends from that shared budget.
- Full discovery unlocks a Mastered Atlas capstone point surge, linked-Atlas
  fast travel, and the Mastered Atlas advancement.
- Fast travel is an expensive completion-only power.
- Fast travel requires both source and destination Atlases to buy into the
  travel network.
- The UI prevents new selections that exceed the current shared budget. If saved
  loadouts later exceed the budget after settings or discovery-state changes, a
  deterministic in-budget subset remains powered.

## Player Model

- Players explore the canonical Overworld through the existing Atlas Projector
  discovery system.
- A placed Atlas opens a beacon-like UI instead of only toggling the hologram.
- The UI shows discovered area, discovered percent, world effect points, points
  spent by active Atlases, this Atlas's radius, and its selected effects.
- Players can activate an Atlas by assigning it an effect loadout whose point
  cost fits within the shared world budget.
- Nearby means topological X/Z distance in the wrapped Overworld, so an Atlas
  near one canonical edge can affect players near the opposite edge if they are
  within the wrapped radius.

The first implementation supports multiple active Atlases from the start.
The point budget is the balancing mechanism, and implementing it immediately
avoids a temporary one-Atlas rule that would need to be unwound later.

## Simplest Implementation Defaults

Use these defaults for the first implementation unless playtesting forces a
change:

- **Point curve**: use the initial formula in this plan, not hand-authored
  preset tables.
- **Earth-scale cap**: keep the post-completion per-Atlas radius cap at `512`
  blocks.
- **Completion**: treat `99%` discovered pixels as Mastered Atlas completion.
  The reveal cleanup fills the remaining hidden pixels so the rendered map
  catches up to the rounded completion state.
- **Fast travel arrival**: use the nearest safe block next to or above the
  destination Atlas. Do not add a separate arrival pad block.
- **Travel discovery restriction**: require the destination Atlas's canonical
  map pixel to be discovered.
- **Permissions**: any non-spectator player who can interact with the Atlas can
  edit its loadout and use travel. Add ownership or teams only if multiplayer
  testing needs it.
- **Overload priority**: most recently edited Atlas wins first, then canonical
  block-position order for deterministic ties.
- **Chunk loading**: unloaded Atlases keep reserving budget through
  `GlobeAtlasPowerState`; effects and travel only operate while the relevant
  Atlas block entity/chunk is loaded.
- **Item-in-hand interaction**: empty hand opens the power UI. Non-empty hand
  keeps ordinary block/item interaction behavior unless the item has no use, in
  which case fall back to opening the UI.

## Reward Scaling

Discovery rewards should scale from both discovered percentage and absolute
discovered area. Percentage matters for tiny and small worlds; absolute explored
area matters for huge and Earth-scale worlds where `100%` completion is not a
normal survival expectation.

Use `GlobeMapSavedData` to compute:

- `discoveredPixels`
- `discoveredPercent`
- `discoveredAreaBlocks = discoveredPixels / totalPixels * tileSizeBlocks^2`

Then derive two outputs:

- **World effect points**: global budget shared by all active Atlases.
- **Radius cap**: maximum radius any one Atlas can buy with points.

Small-tile gating:

| Tile size | Activation rule |
| --- | --- |
| `<= 16` chunks | no effect points until `100%` discovery, then a small fixed budget. |
| `17-64` chunks | first points unlock late, around `50%` discovery. |
| `65+` chunks | points can start from meaningful absolute exploration, with percentage still improving the budget. |

Continuous scaling direction:

- Point budget should grow in steps so the UI feels legible.
- Radius should be directly tied to explored area: more discovered space means
  a larger possible AOE.
- Radius should use a softened curve such as `sqrt(discoveredAreaBlocks)` so
  Earth-scale worlds reward huge exploration without producing absurd
  continent-sized status-effect queries.
- Radius should still have practical server caps per Atlas. If Earth-scale
  worlds need very large coverage, prefer many active Atlases paid from the
  shared budget over one massive query.

Initial numbers:

| Exploration state | World points | Per-Atlas radius cap |
| --- | ---: | ---: |
| Tiny tile incomplete | 0 | 0 blocks |
| Tiny tile complete | 2 | 32 blocks |
| Small tile mid/late progress | 2-4 | 32-64 blocks |
| Medium tile substantial progress | 4-8 | 64-128 blocks |
| Huge tile major expedition | 8-16 | 128-256 blocks |
| Earth-scale sustained exploration | 16+ | 256+ blocks, subject to server cap |

Initial formula:

- `basePoints = floor(sqrt(discoveredAreaBlocks) / 512)`, capped at `16`.
- `percentPoints = floor(discoveredPercent / 12.5)`, capped at `8`.
- `worldPoints = max(basePoints, percentPoints)`.
- `completionBonusPoints = complete ? max(4, ceil(worldPoints * 0.5)) : 0`.
- `totalPoints = worldPoints + completionBonusPoints`.
- `radiusCap = 0` when `totalPoints == 0`.
- `radiusCap = clamp(16 * 2^floor(totalPoints / 4), 32, 256)` before
  completion when points are available.
- `radiusCap = clamp(radiusCap * 2, 64, 512)` after completion.

Small tile overrides:

- `<= 16` chunks: `totalPoints = 0` until complete, then `4`; radius cap `32`.
- `17-64` chunks: no points until `50%`; completion adds at least `4` points.

Keep these constants in `GlobeDiscoveryRewards` so playtesting can tune point
thresholds, radius curve, and caps without touching UI or block-entity code.

## Effect Points

Each active Atlas has a loadout cost. The sum of all active Atlas costs in the
Overworld must be less than or equal to the world effect-point budget.

Suggested costs:

| Loadout item | Cost |
| --- | ---: |
| Activate Atlas with one level I effect | 2 |
| Add another level I effect | +2 |
| Upgrade one effect to level II | +2 |
| Increase radius tier | +1 per tier |
| Show or hide this projector's hologram | Free |
| Join linked-Atlas travel network | +4 |
| Completion advancement | Free |

This makes the core choice explicit:

- one level II effect with a larger radius;
- two level I effects in one base;
- several narrow Atlases spread across different bases or projects.

If the budget falls below the total spend after a settings change, the world
should keep saved Atlas loadouts but only apply the highest-priority active
Atlases that fit the current budget. Priority can be the most recently changed
Atlas first, then stable block-position order as a deterministic fallback.

## Mastered Atlas Capstone

Large-world completion should be a major moment, not just the last ordinary
point step. Reserve a separate capstone for `100%` discovery.

Recommended capstone:

- grant a large completion point surge;
- unlock linked-Atlas fast travel as an expensive completion-only network;
- show a distinct "Mastered Atlas" state in the UI;
- award the Mastered Atlas advancement to non-spectator Overworld players.

Atlas Flight is intentionally not part of the active reward design. Keeping the
capstone to points, linked travel, and a vanilla advancement gives completion a
clear payoff without adding a second movement-permission system.

## Linked Atlas Fast Travel

Fast travel is completion-only and budgeted. Each Atlas that wants to
participate buys the `travel network` power. A player can travel from one
travel-enabled Atlas to another travel-enabled Atlas in the same Overworld
power state.

Rules:

- both source and destination Atlases must be active and in the in-budget set;
- both Atlases must have the travel-network power selected;
- the player must be within the source Atlas radius;
- the destination must still exist at its canonical block position;
- travel is same-dimension only for the first version;
- travel is instant after server-side validation;
- arrival position is the nearest safe block next to or above the destination
  Atlas, evaluated in the destination's canonical tile;
- after arrival, render-facing packets should naturally show the nearest alias
  through existing player teleport and canonicalization rules.

UI:

- show a `Travel` toggle in the Atlas power UI after Mastered Atlas unlocks;
- enable a destination tab only when the source Atlas is powered with the
  `Travel` toggle selected;
- show a destination list of active, in-budget, travel-enabled Atlases there;
- display each destination by custom name if present, otherwise coordinates;
- disable destinations that are unloaded, missing, out of budget, or blocked.

This gives completion a strong infrastructure reward without making travel free
for every placed projector. A large completed world can support a real network,
but every endpoint competes with effects, radius, and other endpoints.

## Effect Pool

The active effect pool stays deliberately small:

- Speed
- Haste
- Jump Boost

The block should not grant effects that invalidate progression or create odd
server behavior, such as Regeneration, Resistance, Strength, Night Vision,
Invisibility, Fire Resistance, Water Breathing, or Saturation, unless
playtesting gives one of them a strong reason to return.

With effect points, the UI allows either:

- one selected effect at amplifier `1`, equivalent to level II, if the loadout
  can pay the upgrade cost; or
- multiple selected effects at amplifier `0`, equivalent to level I, if the
  loadout can pay each effect cost.

That keeps the familiar vanilla beacon tradeoff while allowing higher budgets
to feel broader instead of only stronger.

## State Model

Reuse `GlobeMapSavedData` as the discovery source of truth. It already stores
the discovered bitset and exposes `discoveredPercent()`.

Add a small derived helper named `GlobeDiscoveryRewards` that accepts:

- `DimensionTiling`
- discovered pixel count or percent
- optional future config values

and returns:

- discovered area in blocks
- discovered percent
- whether the canonical tile is complete
- world effect-point budget
- completion bonus points
- whether Mastered Atlas powers are unlocked
- spent effect points
- available effect points
- per-Atlas radius cap
- next point or radius threshold

Avoid storing the computed budget in each Atlas block entity. The budget is
world state derived from shared discovery, not per-block state. The Atlas block
entity only needs to store player choices:

- whether this Atlas is power-active
- selected effect ids
- which effect, if any, is boosted to level II
- whether this Atlas participates in linked-Atlas travel
- chosen radius tier
- priority or last-edited tick for budget allocation
- optional custom name

Track active Atlas loadouts in shared world state named
`GlobeAtlasPowerState`, keyed by canonical block position. That lets the server
answer "how many points are already spent?" without scanning every loaded block
entity each tick, and it lets unloaded active Atlases remain part of the budget.

`GlobeAtlasPowerState` stores:

- map from canonical `BlockPos` to `AtlasLoadout`;
- cached deterministic order for in-budget resolution;
- last computed `totalPoints`, `spentPoints`, and `availablePoints`;
- active/in-budget status per Atlas;
- optional custom display name copied from the block entity;
- enough destination metadata for the client UI to list linked travel targets.

`AtlasLoadout` stores:

- canonical block position;
- selected effect ids;
- boosted effect id, if any;
- radius tier;
- travel selected;
- active selected;
- last edited game time;
- cached point cost.

If recomputing discovered percent by scanning the full bitset becomes too costly
for many powered Atlases, add and persist a `discoveredCount` field to
`GlobeMapSavedData` when the save-data version is bumped. Until then, calculate
budget on the Atlas effect cadence rather than every game tick.

## Block And UI

Use the existing Atlas Projector block family as the reward interface. Do not
add a separate reward block unless the combined interaction becomes too crowded.

Suggested interaction split:

- right-click with empty hand opens the Atlas power UI;
- sneak-right-click toggles the hologram projection;
- redstone or comparator behavior can be considered later if active power state
  needs automation.

Server-side pieces:

- extend `GlobeBlock` interaction handling;
- extend `GlobeBlockEntity` with power loadout fields;
- add `GlobeAtlasPowerState` shared saved data for active loadouts and spend;
- add `GlobeAtlasPowerMenu`;
- add `GlobeAtlasSetPowerPayload` or a vanilla-style serverbound custom payload;
- register the menu type and payloads.

Client-side pieces:

- `GlobeAtlasPowerScreen`
- screen registration in client init
- small GUI texture or sprite-backed layout
- lang entries for title, stat labels, and effect tooltips

Use vanilla beacon classes as structure references:

- `BeaconBlockEntity` applies effects every `80` ticks, stores selected effects,
  exposes `ContainerData`, and plays activation sounds.
- `BeaconMenu` syncs levels/effects and handles effect confirmation.
- `BeaconScreen` lays out effect icon buttons and enables them based on the
  current unlocked level.

The Atlas power UI should not require a payment item or confirmation row.
Exploration is the cost, and controls for candidate loadouts that exceed the
available point budget should be disabled. If discovery drops because the world
settings changed and the saved map no longer matches, Atlases keep their saved
choices but apply nothing until compatible discovery state exists again.

UI layout:

- top bar: discovery percent, discovered area, total/spent/available points;
- left panel: radius tier selector and point cost;
- center panel: vanilla beacon-style effect icon grid;
- right panel: Mastered Atlas powers, locked until completion;
- bottom panel: travel destination list when the selected Atlas has travel
  enabled;
- confirm/cancel buttons matching vanilla beacon expectations.

## AOE Semantics

Apply effects server-side every `80` ticks, similar to vanilla beacon cadence.
Use a duration slightly longer than the cadence, such as `180` ticks, so players
do not flicker when standing near the boundary.

Candidate query:

1. Resolve this Atlas's active loadout from `GlobeAtlasPowerState`.
2. Skip the Atlas if its loadout is inactive, over budget, or has no selected
   effects or special powers.
3. Build an ordinary AABB around the Atlas large enough to cover its chosen raw
   search radius.
4. Include players from neighboring alias windows if the radius crosses a tile
   seam.
5. Deduplicate by player UUID.
6. Measure final inclusion with `CoordUtil.wrappedDistanceSqrXZ(...)`.
7. Preserve vanilla's vertical generosity by expanding through build height, or
   tune to a fixed vertical radius if playtesting says whole-column effects are
   too strong.

Normal selected effects use ordinary `MobEffectInstance` refreshes.

Linked Atlas travel is not an AOE refresh. It is a deliberate UI action from
inside the source radius. Validate all travel conditions on the server when the
request is received.

The stored Atlas block position should be canonical for server authority. When
players interact with an alias of the block, existing block-position
canonicalization should route the interaction to the canonical block entity and
the canonical power-state entry.

## Visual Feedback

Initial implementation can be modest:

- active/inactive block model state or particle pulse
- ambient sound on the same cadence as effect application
- UI progress bar showing discovery percent and reward milestone markers
- UI point meter showing total, spent, and available effect points
- distinct Mastered Atlas treatment once completion-only powers are available

Later polish:

- beam color based on selected effects
- projector-style hologram ring showing this Atlas's AOE radius
- Atlas Projector overlay markers for active powered Atlases

## Implementation Steps

Phase 1, reward math and saved state:

1. Add `discoveredCount` to `GlobeMapSavedData` while bumping its save-data
   version; maintain it in `updatePixel(...)` when a pixel changes from
   undiscovered to discovered.
2. Add `GlobeDiscoveryRewards` with the initial point and radius formulas.
3. Add tests for tiny, small, medium, huge, complete, and Earth-scale inputs.
4. Add `GlobeAtlasPowerState` saved data with `AtlasLoadout` records,
   deterministic in-budget resolution, and point-spend accounting.

Phase 2, Atlas loadouts and UI shell:

5. Extend `GlobeBlockEntity` save/load for selected effects, boosted effect,
   travel selection, active state, radius tier, custom name, and last-edited
   priority.
6. Extend `GlobeBlock` interaction handling so ordinary use opens the power UI
   and sneak-use toggles projection.
7. Register `GlobeAtlasPowerMenu`, sync fields, and
   `GlobeAtlasSetPowerPayload`.
8. Implement `GlobeAtlasPowerScreen` with point meter, radius controls, effect
   buttons, Mastered Atlas locked panel, and confirm/cancel flow.

Phase 3, effect application:

9. Implement the server cadence that recomputes reward state, resolves the
   in-budget Atlas set, and applies wrapped-radius mob effects.
10. Replace regeneration with jump boost so the supported effect set stays at
   three powers.
11. Keep active visual/audio polish retired unless playtesting identifies a
   concrete readability problem.

Phase 4, linked travel:

12. Add travel target listing to `GlobeAtlasPowerState` and the menu sync.
13. Implement a serverbound travel request payload with source and destination.
14. Validate source radius, in-budget status, destination existence, safe
   arrival position, and completion unlock when the request arrives.
15. Keep channeled travel and arrival effects retired unless instant travel
   proves confusing or too strong.

Phase 5, polish and docs:

16. Add a Mastered Atlas advancement for full completion and use the Atlas power
   UI progress bar to indicate point/radius milestones.
17. Document shipped behavior in `docs/mod-mechanics/maps.md` or a new
   `docs/mod-mechanics/exploration-rewards.md`, then update the mechanics index.
18. Keep or retire this plan depending on whether any follow-up work remains.

## Deferred Questions

These are intentionally not blockers for the first implementation:

- Whether the point curve should become hand-authored by tile-size preset after
  playtesting.
- Whether Earth-scale completed worlds should exceed the `512` block per-Atlas
  radius cap.
- Whether any retired capstone idea, such as Atlas Flight or channeled travel,
  earns its complexity back in playtesting.
- Whether fast travel should require a built arrival pad or named station.
- Whether multiplayer servers need placer ownership, team permissions, or
  operator-only loadout editing.
- Whether old/anchored Atlases should outrank recently edited Atlases during
  in-budget resolution.
- Whether active powered Atlases should force-load chunks or only work while
  naturally loaded.
- Whether the `99%` Mastered Atlas threshold should be tuned after playtesting.

## Validation

- On a `<= 16` chunk Overworld tile, no effect can be selected or applied until
  discovery reaches the rounded `99%` completion threshold.
- On a large or Earth-scale tile, effect points and radius caps increase from
  sustained exploration without requiring full-tile completion.
- Full completion grants the Mastered Atlas point surge and unlocks
  completion-only powers.
- World effect-point budget, spent points, and available points update without
  reopening the UI.
- Multiple active Atlases can apply effects when their combined loadout cost is
  within budget.
- If active Atlas loadouts exceed the budget, only the deterministic in-budget
  subset applies effects.
- Selected effects, active state, radius tier, and boosted effect save, reload,
  and sync to clients.
- Invalid or locked effects in saved NBT are ignored rather than applied.
- Any non-spectator player who can interact with an Atlas can edit the first
  implementation's loadout; spectators cannot.
- A player across an X seam, Z seam, or corner seam receives effects when the
  wrapped distance is within radius.
- A player outside the wrapped radius does not receive effects even if they are
  inside a raw AABB alias query.
- Multiple powered Atlases refreshing in the same tick do not duplicate or
  shorten effects unexpectedly.
- Linked travel is only available after completion, only between active
  in-budget travel Atlases, and only while the player is inside the source
  radius when the request is validated.
- Unloaded powered Atlases reserve budget but do not apply effects or serve as
  travel endpoints until loaded again.
- Linked travel refuses missing, obstructed, out-of-budget, cross-dimension, or
  non-travel-enabled destinations.
- Changing the Overworld tile size invalidates mismatched discovery data and
  leaves powered Atlases inactive until compatible discovery state exists.
