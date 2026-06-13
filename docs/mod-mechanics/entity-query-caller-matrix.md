# Entity Query Caller Matrix

Globe World keeps vanilla entity storage canonical and does not replace
`EntityGetter` globally. Callers that need visible wrapped behavior are hooked
one at a time so side effects still run once on the real entity.

## Implemented Matrix

| Source | Method | Query API | Classification | Side effects | Hook | Regression |
| --- | --- | --- | --- | --- | --- | --- |
| `BasePressurePlateBlock` via `PressurePlateBlock` | `getSignalStrength` | `BasePressurePlateBlock.getEntityCount` | Topological gameplay query | Redstone signal, block events, scheduled recheck | `PressurePlateBlockEntityQueryMixin` returns deduped visible entity count | Player/mob/item-visible entity across X/Z seam powers a pressure plate once. |
| `BasePressurePlateBlock` via `WeightedPressurePlateBlock` | `getSignalStrength` | `BasePressurePlateBlock.getEntityCount` | Topological gameplay query | Weighted redstone strength, scheduled recheck | `WeightedPressurePlateBlockEntityQueryMixin` returns deduped visible count | Multiple items straddling seam produce stable weighted strength without alias double count. |
| `ButtonBlock` | `checkPressed` | `Level.getEntitiesOfClass` | Topological gameplay query | Arrow button power, block events, scheduled recheck | `ButtonBlockEntityQueryMixin` queries visible arrow box | Arrow visible through seam presses wooden button once. |
| `DetectorRailBlock` | `getInteractingMinecartOfType` | `Level.getEntitiesOfClass` | Topological gameplay query | Rail power and comparator output | `DetectorRailBlockEntityQueryMixin` queries visible-frame minecart boxes | Minecart visible through seam powers detector rail and comparator reads one cart/container. |
| `TripWireBlock` | `checkPressed(Level, BlockPos)` | `Level.getEntities` | Topological gameplay query | Tripwire powered state, hook update, scheduled recheck | `TripWireBlockEntityQueryMixin` queries visible shape box | Entity visible through seam keeps tripwire powered once. |
| `HopperBlockEntity` | `getItemsAtAndAbove` | `Level.getEntitiesOfClass` | Topological gameplay query | Item insertion and item discard/update | `HopperBlockEntityQueryMixin` queries visible item pickup box | Item entity visible above seam-adjacent hopper is sucked once. |
| `HopperBlockEntity` | `getEntityContainer` | `Level.getEntities` | Topological gameplay query | Selects container entity for extraction/insertion | `HopperBlockEntityQueryMixin` queries visible container box | Container minecart visible through seam can feed a hopper. |
| `ChestBlock` | `isCatSittingOnChest` | `LevelAccessor.getEntitiesOfClass` | Topological gameplay query | Blocks menu opening | `ChestBlockCatQueryMixin` queries visible cat box when accessor is a `Level` | Sitting cat visible above seam-adjacent chest blocks opening. |
| `ShulkerBoxBlock` | `canOpen` | `Level.noCollision(AABB)` | Narrow physical collision | Blocks menu opening when lid space is occupied | `ShulkerBoxBlockEntityQueryMixin` adds visible entity-collision check | Entity visible in lid path blocks opening. |
| `ShulkerBoxBlockEntity` | `moveCollidedEntities` | `Level.getEntities` | Narrow physical collision | Moves entities pushed by opening lid | `ShulkerBoxBlockEntityCollisionMixin` queries visible lid sweep | Opening shulker box pushes one visible seam entity. |
| `ItemEntity` | `mergeWithNeighbours` | `Level.getEntitiesOfClass` | Narrow physical collision | Merges item stacks, discards source item | `ItemEntityMergeMixin` queries visible merge box and dedupes identity | Matching item stacks on opposite seam sides merge once. |
| `ExperienceOrb` | `scanForMerges`, `tryMergeToExisting`, `followNearbyPlayer` | `Level.getEntities(EntityTypeTest, AABB, Predicate)`, player distance/vector math | Topological gameplay query | Merges XP orb groups, chooses and pulls toward following player | `ExperienceOrbAliasMixin` queries visible merge boxes and uses wrapped player distance and pull X/Z | XP orbs near a seam merge and pull toward the visible alias player without raw-coordinate slingshotting. |
| `Player` | `aiStep` | `Level.getEntities` | Already covered | Player pickup stats, inventory mutation, item discard | `PlayerItemPickupMixin` | Player picks up visible seam item once. |
| `OldMinecartBehavior` | `pushAndPickupEntities` | `Level.getEntities` | Narrow physical collision | Entity push and ride pickup | `OldMinecartBehaviorCollisionMixin` queries visible hitbox | Cart pushes/picks one real entity across seam. |
| `NewMinecartBehavior` | `pickupEntities`, `pushEntities` | `Level.getEntities` | Narrow physical collision | Entity push and ride pickup | `NewMinecartBehaviorCollisionMixin` queries visible hitbox | Experimental cart behavior sees one visible entity across seam. |
| `MinecartHopper` | `suckInItems` | `Level.getEntitiesOfClass` | Topological gameplay query | Item insertion and item discard/update | `MinecartHopperEntityQueryMixin` queries visible item box | Hopper minecart picks up visible seam item once. |
| `BoatItem` | `use` | `Level.getEntities`, `AABB.contains`, `Level.noCollision` | Narrow physical collision | Placement pass/fail, item consume, entity spawn | `BoatItemPlacementMixin` checks visible obstruction boxes | Boat placement fails if visible entity blocks the ray or spawn box through seam. |
| `MinecartItem` | `useOn` | `Level.getEntities` | Narrow physical collision | Placement pass/fail in experimental movement mode | `MinecartItemPlacementMixin` queries visible cart box | Minecart placement rejects existing visible cart across seam. |
| `ArmorStandItem` | `useOn` | `Level.noCollision`, `Level.getEntities` | Narrow physical collision | Placement pass/fail, item consume, entity spawn | `ArmorStandItemPlacementMixin` checks visible entity collision and query box | Armor stand placement rejects visible seam entity. |
| `EndCrystalItem` | `useOn` | `Level.getEntities` | Narrow physical collision | Placement pass/fail, crystal spawn, dragon fight respawn check | `EndCrystalItemPlacementMixin` queries visible obstruction box | End crystal placement rejects visible seam entity. |
| `PistonMovingBlockEntity` | `moveCollidedEntities`, `moveStuckEntities` | `Level.getEntities`, `Entity.getBoundingBox` | Narrow physical collision | Piston movement, honey/slime side effects | `PistonMovingBlockEntityQueryMixin` queries visible piston boxes and uses alias entity boxes for movement math | Piston at seam pushes or carries the real entity once. |
| `ContainerOpenersCounter` | `getEntitiesWithContainerOpen` | `Level.getEntities` | Already covered | Canonical open count and lid events | `ContainerOpenersCounterMixin` | Alias-opened chest remains open while player stands near visible chest. |
| `MobEffectUtil` beacon/conduit helpers | effect queries | helper/player distance paths | Already covered | Applies status effects | `MobEffectUtilMixin` | Alias-near player receives beacon/conduit effects once. |
| `PlayerDetector` | trial spawner/vault player checks | player distance and line of sight | Already covered | Spawner/vault activation | `PlayerDetectorMixin` | Alias-near player activates detector with visible LOS. |
| `ThrownSplashPotion` | `onHitAsPotion` | `Level.getEntitiesOfClass`, `AABB.distanceToSqr` | Already covered | Applies potion effects once per real entity | `ThrownSplashPotionAliasEffectMixin` | Splash potion affects visible seam target with wrapped falloff. |
| `BedBlock` | villager wake-up path | `Level.getEntitiesOfClass` | Intentionally vanilla | Wakes sleeping villagers during bed use/explosion paths | No hook; sleeping villager lifecycle remains canonical until a bed-specific seam bug is proven | Bed use does not duplicate wake-up side effects. |
| `BeehiveBlock` | anger nearby bees/players | `Level.getEntitiesOfClass` | Already covered | Bee anger target selection | `BeehiveBlockEntityMixin` wraps release distance; broader bee/POI search remains in the POI plan | Released bees use wrapped player distance. |
| `Block` | entity support/shape helper | `Level.getEntities` | Intentionally vanilla | Generic collision/support helper shared by many block internals | No hook; broad block collision replacement is outside this caller-specific pass | Avoids changing all block collision side effects globally. |
| `CarvedPumpkinBlock`, `WitherSkullBlock` | boss/golem spawn advancement ranges | `Level.getEntitiesOfClass` | Intentionally vanilla | Awards advancements after successful structure spawn | No hook; spawn result is canonical and advancement fanout is not a seam trigger | Boss/golem spawn remains single canonical event. |
| `ComparatorBlock` | item-frame signal lookup | `Level.getEntitiesOfClass` | Intentionally vanilla | Comparator output from item frame on attached block | No hook in this pass; item-frame comparator semantics need a redstone-specific audit | No duplicate comparator frame selection. |
| `CrafterBlock` | crafting advancement range | `Level.getEntitiesOfClass` | Intentionally vanilla | Awards nearby players after crafting | No hook; not a trigger/collision query | Crafting side effects stay canonical. |
| `BeaconBlockEntity`, `ConduitBlockEntity` | effect radius and target checks | `Level.getEntitiesOfClass` | Already covered | Applies effects or conduit attack target | `MobEffectUtilMixin` covers player effects; conduit hostile target search remains intentionally vanilla pending combat audit | Alias-near players receive effects once. |
| `BellBlockEntity` | resonation/raider reveal cache | `Level.getEntitiesOfClass` | Intentionally vanilla | Stores nearby living entity cache for bell behavior | No hook; bell entity cache needs a separate gameplay audit before widening | Bell behavior remains vanilla outside canonical query box. |
| `TestInstanceBlockEntity` | test cleanup bounds | `Level.getEntities` | Intentionally vanilla | Discards test entities | No hook; development/test block storage utility | Test cleanup does not cross virtual aliases. |
| `AbstractBoat` | boat push behavior | `Level.getEntities` | Intentionally vanilla | General entity pushing | No hook; general mob/player/vehicle pushing is not a v1 goal | Boat pushing remains vanilla raw-space behavior. |
| `BottleItem`, `MaceItem` | area cloud pickup, smash knockback | `Level.getEntitiesOfClass` | Intentionally vanilla | Consumes clouds or applies combat knockback | No hook; item-specific combat/interaction behavior needs separate audit | No duplicated area-cloud consume or mace knockback. |

## Policy

Topological gameplay queries are block-trigger checks whose visible frame should
cross seams. They use `TopologicalCollisionQueries` or
`TopologicalEntityQueries` and return canonical entity instances once.

Narrow physical collision hooks are limited to item merge/pickup, minecart
push/pickup, vehicle and entity placement obstruction, shulker lid obstruction,
and moving piston displacement. They use visible alias boxes for intersection
tests, but all side effects apply to the real canonical entity.

Canonical storage, entity-section maintenance, and broad vanilla
`EntityGetter` behavior remain intentionally vanilla unless a caller-specific
row is added here.
