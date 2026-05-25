## TODO

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

when recreating a world tile size and curvature are not retrieved


## trees and foliage at tile border
## trees only use quarter chunk on 1 chunk tile

2026-05-25T19:37:03.073+0100 [QUIET] [system.out] [19:37:03] [Worker-Main-13/ERROR] (Minecraft) Detected setBlock in a far chunk [-3, -3], pos: BlockPos{x=-46, y=-32, z=-35}, status: minecraft:features, currently generating: ResourceKey[minecraft:worldgen/placed_feature / minecraft:amethyst_geode]
2026-05-25T19:37:03.092+0100 [QUIET] [system.out] [19:37:03] [Worker-Main-13/ERROR] (Minecraft) Detected setBlock in a far chunk [2, 2], pos: BlockPos{x=46, y=73, z=47}, status: minecraft:features, currently generating: ResourceKey[minecraft:worldgen/placed_feature / minecraft:trees_taiga]