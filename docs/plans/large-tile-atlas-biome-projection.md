# Large-Tile Atlas Biome Projection Follow-Ups

Core large-tile survey projection behavior is implemented and documented in
[Maps](../mod-mechanics/maps.md). Survey-mode Atlases now keep per-visited-chunk
biome ids, send centered survey-window payloads, render held player-centered
windows, render nearby placed Atlas-centered domed windows, and allow projection
toggling in survey mode.

Open follow-ups:

- Decide whether newly placed large-tile Atlas Projectors should default their
  projection on instead of keeping the existing conservative hidden default.
- Consider a client-registry biome color source or hybrid palette if the stable
  keyword palette is not expressive enough for modded biomes.
- Decide whether old visited chunks without biome ids should be backfilled when
  their chunks are already loaded for unrelated reasons, instead of only when
  players revisit them.
