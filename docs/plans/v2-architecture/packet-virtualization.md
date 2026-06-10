# Packet Virtualization

## Problem

V1's packet relabeling model is the right shape, but packet coverage is
distributed across several utilities and mixins. Every new position-bearing
packet needs a hand audit: is it canonical, viewer-relative, entity-relative,
chunk-relative, or safe to leave alone?

## V2 Direction

Keep the vanilla-shaped client cache. Continue sending aliases as ordinary raw
client coordinates. Improve the internal packet model by making packet
virtualization policy explicit and auditable.

## Current Status

The policy table is implemented as `PacketVirtualizationPolicies` plus
[Packet Policies](../../mod-mechanics/packet-policies.md). It is an audit
registry, not a dispatcher rewrite; existing handwritten packet utilities still
preserve packet-specific semantics. Entity and waypoint packet helpers now use
`TopologyContext` at their receiver-frame boundaries while keeping handwritten
packet copies.

## Packet Policy Categories

- Chunk owner packets: full chunk, forget chunk, light, biome payloads.
- Block packets: block updates, section updates, block entities, signs.
- World-event packets: sounds, block events, particles, explosions, break
  progress.
- Entity packets: add, absolute sync, teleport, vehicle correction, damage
  source, minecart interpolation.
- Navigation or UI packets: waypoints, look-at, maps, compass/lodestone data.
- Non-position packets: explicitly documented as no-op.

## Requirements

- Each packet type should have one documented policy.
- Packet virtualization must be per receiver.
- Loaded-alias fanout and nearest-alias fallback should be distinguishable.
- Packet copies should preserve all non-position payload fields.
- Diagnostics should report unknown or intentionally ignored packet classes
  during audits, not during normal play.

## Implementation Sketch

Implemented: a packet policy registry/table exists in docs and code. The code
still dispatches with `instanceof`; future cleanup can organize more call sites
around policy categories when behavior is already being touched.

For packets with private fields, keep accessor mixins narrow and named after
the packet policy they support.

## Expected Benefits

- Easier Minecraft-version audits.
- Less chance of missing a new position-bearing packet.
- Cleaner multiplayer debugging because every packet position has an explicit
  receiver frame.

## Open Questions

- Should unsupported packets fail loudly in development builds?
- Can some packet relabeling be generated from codecs, or are hand policies
  safer because packet semantics matter more than field type?
- Which diagnostics are useful enough to keep as command-triggered packet
  tracing?
