# Configuration And Diagnostics

## Problem

V1 mixes several kinds of settings:

- World-topology settings that should be fixed after creation.
- Runtime presentation settings that can safely change.
- Debug controls and playtesting diagnostics.

Some diagnostics currently log directly at warning level because they were
added during focused investigations. That is useful while developing, but noisy
as a default behavior.

## V2 Direction

Separate settings by responsibility:

- Topology: tile sizes, dimension tiling modes, terrain modes, portal ratios,
  forced progression policy.
- Presentation: curvature, visual entity aliases, day/night presentation,
  client overlays.
- Runtime gameplay tuning: day-length multiplier if it remains mutable.
- Diagnostics: command-gated or config-gated debug channels.

## Current Status

Diagnostics channels are implemented as session-only command-gated debug
channels. The durable behavior is documented in
[Client diagnostics](../../mod-mechanics/client.md#local-sky-and-diagnostics).

The settings split is implemented as records/codecs with `GlobeSettings` as the
saved and synchronized schema. Old flat `TilingSettings` saved data is not
imported. The durable behavior is documented in
[Topology](../../mod-mechanics/topology.md) and
[Client](../../mod-mechanics/client.md#packet-and-cache-model).

## Requirements

- World-topology settings must be saved with the world and treated as fixed
  unless a migration tool intentionally changes them.
- Presentation settings may sync from server to client but should describe
  visual/runtime behavior, not world ownership.
- Diagnostics should be quiet by default.
- Diagnostic output should identify coordinate frames and dimensions.
- Multiplayer clients should not silently diverge from server-authoritative
  topology.

## Implementation Sketch

Implemented config groups:

```text
TopologySettings
PresentationSettings
GameplaySettings
GlobeSettings
```

Commands and UI should expose only the settings that make sense in that
context. Debug logging should use named channels such as `chunks`, `packets`,
`portals`, `entities`, `worldgen`, and `client-cache`.

## Expected Benefits

- Lower support noise.
- Clearer world-creation UI.
- Safer runtime commands.
- Easier test sessions because diagnostics can be enabled for one subsystem at
  a time.

## Open Questions

- Which v1 runtime commands should become migration/admin-only in v2?
- Should simple mode intentionally hide tiny/debug tile sizes while custom mode
  keeps them available?
