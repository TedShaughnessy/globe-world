# Explosions

## What

Server-side explosions use topological geometry when tiling is enabled.
Explosion packets were already virtualized per viewer; the server-authoritative
block and entity calculations now also treat the nearest visible alias as the
local frame for seam-crossing blasts.

## Why

Vanilla `ServerExplosion` traces block rays, gathers entities, measures entity
distance, and samples exposure in raw coordinates. Near a tile edge that can
miss entities that are visibly adjacent through the wrap, push them in the
wrong direction, or list multiple raw aliases for the same canonical block.

## Block Damage

`ServerExplosionMixin` lets vanilla calculate its ray-shaped `toBlow` list
first, preserving vanilla resistance checks, ray stopping, fire behavior, loot
behavior, gamerules, and explosion side effects. The returned target list is
then canonicalized through `TopologicalExplosions.canonicalAffectedBlocks(...)`.

The canonicalization preserves first-seen order and dedupes by canonical
`BlockPos`, so `interactWithBlocks(...)` and `createFire(...)` operate on the
single mutable block owner instead of repeating work for raw aliases of the same
block.

## Entity Damage And Knockback

`ServerExplosionMixin` replaces `ServerExplosion.hurtEntities()` only when the
dimension has tiling enabled. Untiled dimensions continue through vanilla.

`TopologicalExplosions.hurtEntities(...)` mirrors the vanilla entity path but
changes the coordinate frame:

- The blast AABB is built around the visible explosion center and gathered
  through `TopologicalEntityQueries`.
- Each candidate entity keeps its real server identity, but its canonical box
  and damage origin are mapped into the nearest alias frame to the explosion
  center.
- Distance falloff, exposure sampling, and knockback direction use that
  visible-frame alias.
- `ExplosionDamageCalculator.shouldDamageEntity(...)` and
  `getKnockbackMultiplier(...)` still gate damage and knockback. The damage
  amount uses vanilla's formula with the visible-frame distance, because
  vanilla's calculator method reads raw entity distance from the explosion
  center.
- Redirectable projectile ownership, player knockback recording, and
  `entity.onExplosionHit(...)` stay aligned with vanilla behavior.

Exposure samples copy vanilla's sampling grid, but sample the visible alias
box. Each sample clips to the visible explosion center with collider blocks and
no fluids, relying on the existing canonical block lookup path during the clip.

## Packets

Outbound `ClientboundExplodePacket` handling remains in
`WorldEventPacketUtil` and `ServerLevelWorldEventMixin`: each receiver gets the
explosion center in their nearest visible alias, while relative knockback and
payload shape remain vanilla.

## Key Files

- `TopologicalExplosions`
- `ServerExplosionMixin`
- `TopologicalEntityQueries`
- `WorldEventPacketUtil`
- `ServerLevelWorldEventMixin`

## Related Vanilla Mechanics

- `net.minecraft.world.level.ServerExplosion`
- `net.minecraft.world.level.ExplosionDamageCalculator`
- `net.minecraft.server.level.ServerLevel`
