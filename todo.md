## TODO

## priorities
- follow up tree generation at tile borders: terrain/biomes/caves are seamless, but trees can still be cut where feature writes cross the canonical boundary. Audit whether wrapped `WorldGenRegion.setBlock` writes land in raw neighbor chunks instead of the opposite canonical chunk, and consider a pending spillover/canonical mirror pass after terrain generation.
- village cut in half by tile boundary
- getting on a horse triggers teleport
- compare terrain with and without mod
- terrain gen is worse; see `docs/terrain-periodicity-investigation.md`
    - current torus-embedded periodic sampler is seamless but changes vanilla noise statistics globally
    - next likely path: auto-select compact torus, edge-blended arbitrary-size terrain, or periodic lattice for clean large tiles

- mobs - all the things
- day night offset


## nether
    - option to not tile the nether
    - option to proportional tile of the nether? (1/8th size)
    - nether portals should be canonical on coordinates. always go to the canon coordinates both ways

## Items
    - lodestone needs to point to nearest lodestone, check is dimension is tiling first
    - recovery compass point to canon death point
    - bed, teleport on wake to canon
    - Map, fine as is I guess


## Events
- tp on spawn to canon

## time
