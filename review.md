# Real Patch Plan

Target version: Minecraft `26.1.2`.

The mod already has the right first move: canonical chunk lookup plus chunk packet relabeling. Do not go back to scattered biome/feature filters. The remaining work is to replace the specific bytecode sites where vanilla still measures X/Z distance or emits absolute positions in raw space.

## Coordinate Rule

Use `CoordUtil` as the single coordinate layer:

- `wrapChunk(int)` for canonical chunk storage and lookup.
- `wrapBlock(int)` for canonical block positions.
- Add `wrappedDelta(double a, double b)` and `wrappedDistanceSqrXZ(...)`.
- Add `virtualBlock(double canonical, double viewer)` for packet coordinates nearest to the viewer.

```java
public static double wrappedDeltaBlock(double a, double b) {
    double d = a - b;
    double half = GlobeConfig.W_BLOCKS / 2.0;
    if (d > half) d -= GlobeConfig.W_BLOCKS;
    if (d < -half) d += GlobeConfig.W_BLOCKS;
    return d;
}

public static double virtualBlock(double canonical, double viewer) {
    return canonical + Math.rint((viewer - canonical) / GlobeConfig.W_BLOCKS) * GlobeConfig.W_BLOCKS;
}
```

## 0. Keep What Is Already Correct

Already implemented:

- `ServerChunkCacheMixin`
  - `ServerChunkCache.getChunk(int, int, ChunkStatus, boolean)`
  - `ServerChunkCache.getChunkNow(int, int)`
  - Action: redirect non-canonical chunk lookup to canonical chunk lookup.

- `PlayerChunkSenderMixin`
  - `PlayerChunkSender.sendChunk(...)`
  - Bytecode target: `NEW ClientboundLevelChunkWithLightPacket`
  - Action: serialize canonical chunk data, then write raw/virtual chunk X/Z into the outgoing packet.

- `ClientboundLevelChunkWithLightMixin`
  - `ClientboundLevelChunkWithLightPacket.write(...)`
  - Action: replace written packet `x`/`z` fields with virtual override.

- `NoiseBasedChunkGeneratorMixin`
  - `NoiseBasedChunkGenerator.<init>`
  - Bytecode target:
    `INVOKE ChunkGenerator.<init>(BiomeSource)`
  - Action: replace constructor biome source argument with `TiledBiomeSource`.

## 1. Entity Tracking Across The Seam

Problem: entities across the tile seam are raw-distance far away, so the server never starts tracking them.

Mixin:

- Add `ChunkMapTrackedEntityMixin`
- Target: `net.minecraft.server.level.ChunkMap$TrackedEntity`
- Method:
  `updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V`

Bytecode to replace:

```text
47: aload_2
48: getfield Vec3.x:D
51: aload_2
52: getfield Vec3.x:D
55: dmul
56: aload_2
57: getfield Vec3.z:D
60: aload_2
61: getfield Vec3.z:D
64: dmul
65: dadd
66: dstore 6
```

Action:

- Replace the computed `distXZsq` local with wrapped X/Z distance.
- Use the already available `ServerPlayer` and tracked `entity` fields, not the raw `Vec3.subtract` result.
- Best mixin shape: `@Inject` after local `dstore 6` with `LocalDoubleRef`, or `@ModifyVariable` on the `double` stored at local index `6`.

Desired replacement:

```java
double dx = CoordUtil.wrappedDeltaBlock(player.getX(), entity.getX());
double dz = CoordUtil.wrappedDeltaBlock(player.getZ(), entity.getZ());
distXZsq = dx * dx + dz * dz;
```

## 2. Initial Entity Packet Coordinates

Problem: after tracking starts, `ClientboundAddEntityPacket` still contains canonical X/Z, so clients render the entity on the wrong side of the tile.

Mixin:

- Add `ServerEntityMixin`
- Target: `net.minecraft.server.level.ServerEntity`
- Method:
  `sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V`

Bytecode to modify:

```text
32: aload_0
33: getfield entity:Lnet/minecraft/world/entity/Entity;
36: aload_0
37: invokevirtual Entity.getAddEntityPacket(ServerEntity):Packet
40: astore_3
41: aload_2
42: aload_3
43: invokeinterface Consumer.accept(Object):void
```

Action:

- Wrap the `Consumer.accept(packet)` call.
- If packet is `ClientboundAddEntityPacket`, replace X/Z with virtual coordinates nearest to the `ServerPlayer` argument.
- Implement with either:
  - accessor mixin for packet private final coordinate fields, plus a copy constructor/helper, or
  - `@WrapOperation` on `Consumer.accept` and pass a new packet when the type needs translation.

Also handle later absolute teleports:

- Target packet: `ClientboundTeleportEntityPacket`
- Hook either `ServerEntity.sendChanges()` at the packet send site or `ChunkMap$TrackedEntity.sendToTrackingPlayers*`.
- Action: replace packet X/Z with `CoordUtil.virtualBlock(entityCoord, viewerCoord)` per receiving player.

Leave relative move packets alone at first; deltas are fine while entities do not server-teleport across the boundary in the same tick.

## 3. Mob Spawn Eligibility

Problem: natural spawning skips chunks that are visually close across the seam.

Mixin:

- Add `ChunkMapSpawningMixin`
- Target: `net.minecraft.server.level.ChunkMap`
- Method:
  `playerIsCloseEnoughForSpawning(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;)Z`

Bytecode to replace:

```text
9:  aload_2
10: aload_1
11: invokevirtual ServerPlayer.position():Vec3
14: invokestatic ChunkMap.euclideanDistanceSquared(ChunkPos, Vec3):D
17: dstore_3
```

Action:

- `@WrapOperation` the private static `euclideanDistanceSquared(ChunkPos, Vec3)` call.
- Return wrapped X/Z distance squared from the chunk center to the player.
- Keep the existing threshold `16384.0` unchanged.

Second spawn check:

- Target: `NaturalSpawner.spawnCategoryForPosition(...)`
- Bytecode:

```text
223: aload 25
225: dload 21
227: iload 8
229: i2d
230: dload 23
232: invokevirtual Player.distanceToSqr(DDD):D
235: dstore 26
```

Action:

- `@WrapOperation` `Player.distanceToSqr(double, double, double)`.
- Return wrapped X/Z distance squared with normal Y distance.
- This preserves the 24-block minimum and 128-block maximum inside `isRightDistanceToPlayerAndSpawnPoint(...)`.

## 4. Mob Despawn

Problem: mobs near the seam despawn because nearest-player and distance checks use raw space.

Mixin:

- Add `MobDespawnMixin`
- Target: `net.minecraft.world.entity.Mob`
- Method:
  `checkDespawn()V`

Bytecode to replace:

```text
48: aload_0
49: invokevirtual level():Level
52: aload_0
53: ldc2_w -1.0
56: invokevirtual Level.getNearestPlayer(Entity, double):Player
59: astore_1

64: aload_1
65: aload_0
66: invokevirtual Entity.distanceToSqr(Entity):D
69: dstore_2
```

Action:

- First patch `Entity.distanceToSqr(Entity)` with wrapped X/Z distance.
- If despawn still picks the wrong player in multiplayer, add a second patch for `Level.getNearestPlayer(Entity, double)` that manually scans players with wrapped distance. The distance replacement alone is enough for single-player and most tests.

## 5. Block Updates To Virtual Chunks

Problem: chunk packets are virtualized, but live block changes are still sent at canonical positions.

Find exact bytecode before implementing:

```bash
javap -classpath ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.1.2/minecraft-common-deobf-26.1.2.jar -p -c net.minecraft.server.level.ChunkMap
javap -classpath ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.1.2/minecraft-common-deobf-26.1.2.jar -p -c net.minecraft.server.level.ServerLevel
```

Targets to look for:

- `NEW ClientboundBlockUpdatePacket`
- `NEW ClientboundSectionBlocksUpdatePacket`
- `ServerGamePacketListenerImpl.send(Packet)`

Action:

- At each send site, remap `BlockPos` / `SectionPos` X/Z into the receiver player's virtual frame.
- Use the same alias selection rule as chunk send: choose the virtual position nearest to the player.

## 6. Player Border Rebase

Problem: player coordinates can drift forever. Chunk aliasing works, but other vanilla systems become less predictable as raw positions grow.

Mixin:

- Target: `ServerGamePacketListenerImpl.handleMovePlayer(...)`
- Action: after vanilla movement validation, if player block X/Z leaves canonical range, teleport to `CoordUtil.wrapBlock(x/z)` while preserving Y, rotation, velocity, and dimension.

Do this after entity packet virtualization, otherwise visible entities may pop while testing the border.

## 7. Validation Order

1. Build after each mixin:
   `./gradlew build`
2. Start integrated client/server.
3. Set `W_CHUNKS = 64`, stand near chunk `31` / `-32` edges.
4. Verify in this order:
   - chunks are still continuous;
   - passive/hostile entities become visible across the seam;
   - newly visible entities spawn at the virtual side, not canonical side;
   - mobs do not despawn when crossing the seam;
   - natural spawning works near the seam;
   - block changes across the seam update the visible alias.

## Deferred

Skip these until the MVP above is stable:

- full periodic terrain noise;
- structure seed periodicity;
- cross-boundary pathfinding;
- projectile raycast splitting;
- block/entity interaction range across seam.

Those are real features, but they are not the next bytecode fixes. The next useful work is the tracking/spawn/despawn/packet coordinate replacements above.
