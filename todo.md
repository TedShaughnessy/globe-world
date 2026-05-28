## TODO

## priorities
1. all block/ world / item related things
    - items
2. MOB related things
3. light related things

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

## Optimise chunk data
- currently client will request an alias chunk even if it have the real one, it could
    - only request an alias if it's doesn't have the real one loaded already, (still needs to request updates)
    - store the chunk data in the same cache
    - load cache data immediately, then update on recieving new (it could have changed while the client wasn't tracking it)


## final UI

enabled button

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
nether curvature

day night cycle