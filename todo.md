# TODO

## Priority

1. mob path finding
2. portal gen issues when across or very close to boundaries
3. leaves on the ground sometimes replace tree trunks


## Bugs

- path finding sucks, only works while in canon tile. it seems it's only the zombie in the canon tile that can pathfind to player, konws where the player is when they are outside the canon tile, but forgets once it crosses the border

## Optimizations
- is it worth limiting render distance and simulation distance?
- simulation distance should never be more than one tile
- render distance doesn't need to be more then one tile with more than 50% curvature since only one tiles worth of chunks will be visible