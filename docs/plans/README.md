# Plans

This folder tracks proposed or investigative work that is not yet fully
implemented.

Implemented investigations have been folded into
[Globe World Mod Mechanics](../mod-mechanics/README.md), which is the entry
point for current behavior and modded code anchors.

## Active Plans

- [Finish v2 settings split](v2-architecture/low-risk-implementation-plan.md#finish-clean-settings-split):
  make `GlobeSettings` the saved/network settings model after the low-risk
  compatibility layer.

## Future Architecture

- [V2 architecture plan](v2-architecture/README.md): rebuild-oriented plan for
  keeping the same canonical-world concept while giving coordinate frames,
  topology access, packets, entities, raycasts, worldgen, configuration, and
  migration their own stronger boundaries.

## Implemented From Plans

- The first v2 low-risk pass is implemented and folded into
  [Globe World Mod Mechanics](../mod-mechanics/README.md): topology context,
  packet policies, diagnostics channels, actor-local target views, and the
  split settings compatibility layer.
