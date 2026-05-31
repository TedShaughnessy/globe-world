There is only one real zombie on the server, stored in canonical coordinates. Every zombie you see outside that canonical tile is an alias: a translated view of the same real zombie.

For AI, we do not need separate zombie brains per alias. We just need to choose one consistent coordinate frame for the path calculation.

If the player is near a visible alias of the zombie, then instead of thinking:

visible zombie alias -> player
we can translate that same situation back into the canonical zombie’s frame:

canonical zombie -> corresponding player alias
Those two problems are equivalent because the whole world repeats by exact tile offsets. A path shape from canonical zombie to that player alias should be the same path shape the visible zombie alias would take to the player, just shifted by a whole tile.

So for small tiles, where multiple aliases may be within mob tracking range, the mob should consider several possible player alias positions around the canonical zombie:

player alias west
player alias center
player alias east
player alias north
player alias south
diagonals too
Then it should pick the path that best corresponds to the closest/relevant alias relationship.

The important rule is consistency:

actor position and target position must be in the same alias frame
If the zombie is treated as canonical, the target must be a player alias relative to that canonical zombie. If the zombie is treated as a visible alias, the target must be the player in that visible frame. Both are valid, as long as the whole path calculation uses one frame.