# Remaining Work

Current direction: keep the client in smooth virtual coordinates during normal play. Do not teleport/rebase the client at the seam. The server should canonicalize storage and logic while packets translate between canonical server state and each player's virtual view.

## Done Enough For MVP

- Canonical chunk lookup through `ServerChunkCacheMixin`.
- Chunk packet aliasing through `PlayerChunkSenderMixin` and `ClientboundLevelChunkWithLightMixin`.
- Biome/feature seam work through `BiomeFilterMixin`.
- Wrapped entity tracking distance and entity position packet virtualization.
- Wrapped mob spawn proximity checks.
- Block, section, and block-entity update packet virtualization.
- Alias-aware player lookup for chunk/block broadcasts.

## Keep Client-Side Teleport Disabled

The previous `ServerGamePacketListenerMixin` rebase made coordinates tidy, but caused a visible chunk reload flash and velocity disruption. Normal movement should now allow player coordinates to continue increasing in virtual space.

Later, add non-jarring canonical resets only at natural interruption points:

- sleep/wake
- death/respawn
- dimension change
- world load/login

Those resets should happen before the player is actively viewing the seam, or behind a loading/death transition.

## Next Server Compensation Work

### Player Interaction Canonicalization

Client interactions arrive in virtual coordinates. Patch the server receive paths to canonicalize X/Z before world lookup, while keeping response packets virtualized:

- `ServerGamePacketListenerImpl.handlePlayerAction(...)`
- `ServerGamePacketListenerImpl.handleUseItemOn(...)`
- `ServerGamePacketListenerImpl.handleUseItem(...)` if needed for contextual placement
- block reach checks that use raw distance
- entity attack/interact reach checks

Goal: breaking, placing, using, and attacking across aliases should always act on canonical world state and update the visible alias.

### Player Lifecycle Rebase

Implement canonical position reset on non-jarring transitions:

- on login/load: canonicalize saved player X/Z or reconstruct a virtual offset
- on death/respawn: spawn at canonical equivalent
- on sleep/wake: optional canonical rebase while the screen transition hides it
- on dimension transfer: canonicalize before/after transfer

Track whether a separate per-player virtual offset is needed so player-facing packets remain stable after these resets.

### Clouds, Particles, Weather, And Sky Cosmetics

Fix visual systems that key off raw position:

- cloud offset/scrolling near seams
- biome color sampling for grass/foliage/water if it visibly shifts
- weather/rain column checks
- particles or ambient effects that spawn at canonical positions instead of virtual positions

### Entity Lifecycle Edge Cases

Entity tracking and absolute packets are virtualized, but more cases may need passes:

- newly spawned entities from block interactions near the seam
- projectiles crossing a seam
- vehicle/mount movement near a seam
- item drops from mined blocks near a seam
- player-to-player visibility after long virtual drift

### Mob AI

Mobs can be visible across the seam, but navigation is still vanilla:

- pathfinding across the seam is not seamless
- sensors that query AABBs may miss targets across the seam
- despawn nearest-player lookup may need a wrapped candidate search in multiplayer

### Worldgen Correctness

Longer-term worldgen work:

- replace the feature-specific biome mixin with root biome-source wrapping if the local version supports it cleanly
- periodic terrain noise
- structure placement/seed wrapping
- decoration and feature seed wrapping

### Cleanup

- Remove noisy debug logging before packaging.
- Reconcile `review.md`, `IMPL.md`, and this file once the architecture settles.
- Add a config toggle for experimental systems if needed.
