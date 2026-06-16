# Commands And Admin Coordinates

Globe World keeps vanilla commands raw by default. Command coordinate parsing,
region iteration, selectors, success messages, and saved command metadata use
ordinary Minecraft coordinates unless a `/globeworld` command explicitly says
otherwise.

This policy keeps operator tools predictable. Vanilla command output remains
useful for debugging raw client/server state, while Globe-specific commands name
canonical and alias behavior directly.

## Vanilla Command Policy

Vanilla command families keep their raw command-level behavior:

- `setblock`, `fill`, `clone`, and `fillbiome` iterate raw vanilla regions.
  Lower-level block hooks may still canonicalize the final state access in
  tiled dimensions.
- `place`, `locate`, and structure queries remain raw command operations.
  Structure persistence and worldgen edge behavior are tracked separately in
  [Worldgen](worldgen.md).
- `forceload` remains raw. Loading an alias chunk through a command does not
  make that alias durable canonical ownership.
- `teleport`, `spreadplayers`, and selectors remain raw. Raw alias positions
  are useful during testing and administration.
- `summon` accepts raw vanilla coordinates. Entity storage canonicalization may
  move eligible non-player entities into the canonical tile afterward; see
  [Entities](entities.md).
- `spawnpoint` stores raw vanilla metadata. Respawn-block lookup canonicalizes
  that metadata at use time before validating beds, respawn anchors, or forced
  free-space positions.
- `setworldspawn` remains a raw command interface, but initial/default world
  spawn search and saved world-spawn metadata are canonicalized in tiled
  dimensions before player fallback spawn uses them.
- `worldborder` remains raw. The vanilla world border is not the Globe tile
  border.
- `raid`, `debug`, `data`, `loot`, and similar admin tools remain raw unless a
  specific user-facing topology bug needs a targeted hook.

## Globe Commands

The supported topology-aware admin helpers are implemented in
`GlobeDebugCommands`:

- `/globeworld pos` reports the executing player's raw position, canonical
  position, chunks, tile alias, configured tile settings, and local solar time.
- `/globeworld teleport_canon` moves the executing player from an alias back to
  the canonical equivalent.
- `/globeworld teleport_border [inset]` moves the executing player to the
  nearest canonical tile border.
- `/globeworld teleport_alias <tileX> <tileZ>` moves the executing player to
  the matching alias of their current canonical X/Z.
- `/globeworld query_block <pos>` reports a raw block position, its canonical
  owner, tile alias, canonical block state and block entity if the canonical
  chunk is already loaded, loaded aliases for the executing player, alias
  mutation access, and the interaction permission view for the executing entity.
- `/globeworld config` shows saved topology, presentation, day/night,
  natural-spawn, and forced-progression settings.
- `/globeworld config set allow_mobs_at_world_spawn <true|false>` toggles
  whether natural spawning ignores vanilla's saved world-spawn exclusion.
- `/globeworld config set player_mob_spawn_exclusion <4-24>` changes the
  natural-spawn minimum distance from the nearest non-spectator player.

`query_block` is read-only and avoids loading or generating canonical chunks
just to answer diagnostics. If the canonical chunk is absent, it reports that
state and block entity were not read.

## Interaction Permissions

`GlobeInteractionPermissions` centralizes ordinary player block interaction
policy for tiled dimensions:

- Untiled dimensions delegate to vanilla `ServerLevel.mayInteract(...)`.
- Non-player entities delegate to vanilla behavior.
- Player block breaking and item use test the vanilla world border at the raw
  visible position.
- Player block breaking and item use test spawn protection at the canonical
  block owner.
- Alias mutation still also requires the canonical chunk to be block-ticking;
  `ClientActionDiagnostics.shouldRejectAliasMutation(...)` keeps that guard and
  diagnostic logging.

The result is intentionally split. A visible alias outside the raw vanilla world
border remains blocked even if its canonical owner is inside the border. A
visible alias of a spawn-protected canonical block is blocked even when the raw
alias lies outside the vanilla spawn-protection radius.

Implementation anchors:

- `GlobeInteractionPermissions`
- `ServerGamePacketListenerImplMixin`
- `ServerPlayerGameModeMixin`
- `ClientActionDiagnostics`
- `TopologyContext.aliasMutationAccess(...)`
