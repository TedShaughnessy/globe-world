# Globe World

[nice image here]

Globe World is a Fabric mod for **Minecraft 26.1.2**

This mod adds a finite world that seamlessly tiles giving the illusion of a globe world. there is no telport at the border, it continues uninterupted and world generation tiles seamlessly as well so there is no clear border. structures will generate across the border and mobs will track you across the border. The world can have curvature applied to mimic the real curvature a globe of that size would have


## Features:
- A finite tile size from 2 chunks wide to as large as you want
- a nether dimension that also tiles and can be 1/8th the size of the overworld
- a built in shader that allows you to add curvature to your world
- adjusting day length (I personally dislike how short minecarft days are)
- scrolling day night cycle option - one side of the globe will have day while the other has night


## Mod compatibility:
- Distant horizons should work fine, but it is not aware of this mod so it will do unnecessary work. for worlds where it is useful disable globe world curvature and use distant horizons curvature instead
- Sodium will work fine but will break the curved terrain, either disable curvature or use the curvature shader pack below. for medium to large worlds curvature isn't important

## Shader packs:
The shader packs provided will use the globe world curvature settings

globe-world-curvature - a simple Iris shader pack that adds curvature to the world so that curvature works when sodium is present
makeup-ultra-fast-globe-world - MakeUp Ultra Fast shader pack with patched in curvature support


## Things you'll need to know

### Seamless world generation
due to how minecraft generates terrain making it tile at different sizes is challenging and uses different methods.
 - list methods here

### Seamless wrapping
- the client side is not aware of coordinate wrapping, yopur coordinates are reset at events like load inm, death and sleep to keep the world size small

### End portals
- if a world is too small for a stronghold to spawn, throwing an Eye of Ender will generate an End portal
- once that portal exists, later Eyes of Ender fly toward it


### Commands
- is end portal present or does throwing an eye create one?
- others like day cycle mode, offset, tile size, distance to border, etc


### Debug visuals
-


## Downloads

Published builds are attached to [GitHub Releases](../../releases):

- `globe-world-mc26.1.2-VERSION.jar`: the Fabric mod.
- `globe-world-curvature-mc26.1.2-VERSION.zip`: minimal Iris curvature
  shader pack.
- `makeup-ultra-fast-globe-world-mc26.1.2-VERSION.zip`: MakeUp Ultra Fast
  with Globe World curvature support.
