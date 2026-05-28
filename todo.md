## TODO

## priorities
1. all block/ world / item related things
    - test the nether
        should tile
        should probably be 1/8th the size, make that a yes no option, can always have no tiling
        should teleport to canon positions
        look at noise size so we know how to tile / what mode of tiling
    - the end, little value in tiling, leave as is
    - items
2. MOB related things
3. light related things


## nether
    - option to not tile the nether
    - option to proportional tile of the nether? (1/8th size)
    - nether portals should be canonical on coordinates. always go to the canon coordinates both ways

## Items
    - lodestone needs to point to nearest lodestone, check is dimension is tiling first
    - recovery compass point to canon death point
    - bed, teleport on wake to canon
    - Map, all player should always be on it


## Events
- tp on spawn to canon

## time

## animals
- getting on a horse triggers teleport



## new UI

enabled button, perhaps simple

defined sizes slider
- "32 m
- "64 m"
- "128 m"
- "256 m"
- "512 m"
- "1 km"
- "2 km"
- "4.1 km"
- "8.2 km
- "16.4 km" Width of Ibiza
- "32.8 km" Length of Lake Tahoe
- "65.5 km" ~Width of Singapore
- "131 km" (~ width of hawai'i)
- "262 km" (~width of Ireland)
- "524 km"  (~ width of Iceland)
- "1,048 km (~Length of Italy)"
- "2,097 km"
- "4,194 km (~Width of the US)"
- "8,388 km (~The Moon)"
- "16,777 km"
- "33,554 km (~The Earth)"

globe curvature simplified down to 0, 50% and 100% (need to investigate small tiles)
merge nether globe with enabled with 1/8 (disabled, same size, 1/8th size)

nether structure tile fine when not in 1/8 mode?

./gradlew runClient --debug > debug_log.txt 2>&1