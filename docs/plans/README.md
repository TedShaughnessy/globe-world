# Plans

This folder tracks proposed or investigative work that is not yet fully
implemented.

Implemented investigations have been folded into
[Globe World Mod Mechanics](../mod-mechanics/README.md), which is the entry
point for current behavior and modded code anchors.

## Active Plans

- [Ranged mob alias combat audit](ranged-mob-alias-combat.md): investigate
  ranged and special mob attack readiness, launch, line-of-sight, and projectile
  collision gaps around tiled entity aliases.
- [Chest lid alias sync](chest-lid-alias-sync.md): fan out chest/container block
  events to loaded aliases and make container opener rechecks alias-aware.
- [Nether size and portal scale options](nether-portal-scale.md): replace the
  fixed one-eighth Nether option with separate Nether tile-size and portal-ratio
  settings, including same-size fast travel and reverse ratios.
- [Wandering trader alias spawning audit](wandering-trader-alias-spawning.md):
  confirm whether rare vanilla trader spawning has an alias-coordinate failure
  before adding fixes.

## Retired Ideas

- Client canonical chunk cache: not pursued. Globe World keeps the client cache
  vanilla-shaped and relies on server-side canonical chunk ownership plus
  relabeled packets for alias rendering.
