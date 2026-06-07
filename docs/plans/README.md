# Plans

This folder tracks proposed or investigative work that is not yet fully
implemented.

Implemented investigations have been folded into
[Globe World Mod Mechanics](../mod-mechanics/README.md), which is the entry
point for current behavior and modded code anchors.

## Active Plans

- [Forced progression structures](forced-progression-structures.md): guarantee
  stronghold and fortress progression in small wrapped tiles without
  duplicating structures when vanilla already provides them.

## Retired Ideas

- Client canonical chunk cache: not pursued. Globe World keeps the client cache
  vanilla-shaped and relies on server-side canonical chunk ownership plus
  relabeled packets for alias rendering.
- Multiplayer settings sync: implemented and folded into
  [Client](../mod-mechanics/client.md).
