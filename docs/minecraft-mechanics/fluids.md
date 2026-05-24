# Fluids

Fluids combine block updates, scheduled ticks, neighbor shape updates, and local searches around a source position. Water and lava behavior is mostly implemented through `FlowingFluid` and `LiquidBlock`.

## Key Source Files

Common sources jar:

- `net/minecraft/world/level/material/FlowingFluid.java`
- `net/minecraft/world/level/material/Fluid.java`
- `net/minecraft/world/level/material/FluidState.java`
- `net/minecraft/world/level/material/WaterFluid.java`
- `net/minecraft/world/level/material/LavaFluid.java`
- `net/minecraft/world/level/block/LiquidBlock.java`
- `net/minecraft/server/level/ServerLevel.java`

## Scheduling

`LiquidBlock` schedules fluid ticks on placement, neighbor changes, and shape changes.

Important anchors:

- `LiquidBlock.java:109` fluid state random ticking
- `LiquidBlock.java:113` fluid random tick delegation
- `LiquidBlock.java:154` `onPlace`
- `LiquidBlock.java:156` `scheduleTick`
- `LiquidBlock.java:166` `tick`
- `LiquidBlock.java:174` `updateShape`
- `LiquidBlock.java:184` schedule when source states interact
- `LiquidBlock.java:200` `neighborChanged`
- `LiquidBlock.java:204` schedule after neighbor change

`ServerLevel` runs fluid ticks through its `LevelTicks<Fluid>`:

- `ServerLevel.java:381` fluid tick processing
- `ServerLevel.java:797` `tickFluid`
- `ServerLevel.java:1278` `getFluidTicks`

## Flow And Spread

`FlowingFluid` computes flow vectors and spreads fluid to neighboring positions.

Important anchors:

- `FlowingFluid.java:58` `getFlow`
- `FlowingFluid.java:121` `spread`
- `FlowingFluid.java:146` `spreadToSides`
- `FlowingFluid.java:158` side neighbor position
- `FlowingFluid.java:164` `getNewLiquid`
- `FlowingFluid.java:170` adjacent source checks
- `FlowingFluid.java:267` `spreadTo`
- `FlowingFluid.java:277` `level.setBlock(...)`
- `FlowingFluid.java:353` `sourceNeighborCount`
- `FlowingFluid.java:367` `getSpread`
- `FlowingFluid.java:442` `tick`
- `FlowingFluid.java:454` schedule next fluid tick

## Interaction With Blocks

`LiquidBlock.shouldSpreadLiquid(...)` handles lava/water conversion cases.

Important anchors:

- `LiquidBlock.java:219` `shouldSpreadLiquid`
- `LiquidBlock.java:224` checks neighboring fluid states
- `LiquidBlock.java:226` obsidian/cobblestone conversion decision
- `LiquidBlock.java:243` fizz side effect

## Audit Questions

- Are all neighbor positions in `FlowingFluid` looked up in storage coordinates?
- Can `spreadToSides` cross a tile or chunk boundary?
- If `level.setBlock(...)` is called by fluid logic, does it trigger the desired block update coordinate space?
- Does `scheduleTick(pos, fluid, delay)` enqueue against the correct chunk container?
- Do flow vectors use visual continuity or storage continuity?

