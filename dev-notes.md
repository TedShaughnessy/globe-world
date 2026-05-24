F3/HUD Essentials

Add a compact Globe World debug block to the F3 overlay:

World block: current raw player block X/Z
Canon block: wrapped/canonical X/Z
World chunk: current raw chunk X/Z
Canon chunk: wrapped/canonical chunk X/Z
Tile alias: offset from canonical tile, e.g. +1, -2
In canon tile: yes/no
Canon chunk loaded client-side: yes/no
Add tile border overlay:

Toggle separately from vanilla chunk borders, probably F3 + T if available, or a normal keybind if the F3 combo is awkward in this Minecraft version.
Show canonical tile boundary and repeated tile grid around the player.
Use distinct colors:
canonical tile border: green/cyan
current alias tile border: yellow
First version should be simple world-space lines at tile multiples. No labels needed.
Track whether the client has the canonical counterpart loaded:

Use ClientChunkCache.getChunk(canonX, canonZ, false) or equivalent after checking the 26.1.2 source names.
This should appear in F3 because it directly explains the masked-bug scenario you described.
I’d name it loaded rather than rendered unless we specifically inspect render chunk state. Loaded is still the valuable truth for “does the client have canonical data available?”
Commands
4. Add /globeworld debug pos:

Prints raw block/chunk, canonical block/chunk, alias offset, in-canon-tile, tile size.
Server authoritative, useful when client HUD and server behavior disagree.
Add /globeworld debug aliases:

For the executing player, print aliases tracked for the current canonical chunk via ChunkAliasTracker.
This is better as a command because alias lists can be noisy.
Add /globeworld debug packets on|off later:

Gate the existing client chunk receive/drop logs and any packet relabel logs.
Not first priority unless current logs are making testing painful.
Implementation Order

Add shared helper methods to CoordUtil for:

tileAliasX/Z
isInCanonicalTile
maybe canonicalBlock/chunk naming wrappers around existing wrap methods.
Add client debug state/render classes:

GlobeDebugHud for F3 lines.
GlobeTileBorderRenderer for line rendering.
GlobeDebugKeys or initializer registration for overlay toggle.
Add mixins after checking vanilla 26.1.2 sources:

F3 overlay mixin, likely around DebugScreenOverlay / debug entry list.
Level/debug renderer mixin or event hook for rendering tile borders.
Keyboard/debug shortcut hook if using F3 + key.
Add server command registration in GlobeWorld.onInitialize.

Build and run a quick client smoke test:

Verify F3 lines appear only when Globe World is enabled.
Verify border overlay toggles.
Verify moving outside canonical tile changes In canon tile and Tile alias.
Verify reducing view distance can flip Canon chunk loaded client-side to no.