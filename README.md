# Globe World

<p align="center">
  <img src="docs/assets/screenshots/globe-world-wrap.png" alt="Globe World seamless wrapped terrain screenshot" width="49%">
  <img src="docs/assets/screenshots/globe-world-curvature.png" alt="Globe World curved terrain screenshot" width="49%">
</p>


Globe World is a Fabric mod for **Minecraft 26.1.2**

This mod adds a finite world that seamlessly tiles giving the illusion of a globe world. there is no teleport at the border, it continues uninterupted and world generation tiles seamlessly as well so there is no clear border. structures will generate across the border and mobs will track you across the border. The world can have curvature applied to mimic the real curvature a globe of that size would have


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
Due to how Minecraft generates terrain, making it tile at different sizes is
challenging. Globe World picks a terrain method from the configured tile size:

- Compact torus: used for very small worlds. Noise is sampled on a wrapped
  torus so every edge meets every opposite edge. Expect a fully seamless but
  more stylized world, because vanilla-scale terrain cannot really fit inside a
  tiny tile.
- Edge blend: used for medium and large sizes that do not line up cleanly with
  the periodic noise grid. Most of the world keeps vanilla-looking terrain, with
  a blend band near the tile edges that makes opposite sides converge. Expect the
  border to be playable and continuous, though terrain near the wrap can look a
  little more shaped than the interior.
- Periodic lattice: used for clean large sizes, such as Overworld tiles that are
  multiples of 256 chunks and Nether tiles of at least 128 chunks in multiples
  of 128. Noise cells wrap on the same lattice, giving the best seam quality
  while preserving the most vanilla-like terrain scale.

most generation feature will span across borders like caves and trees

### Seamless wrapping
the client side is not aware of coordinate wrapping, yopur coordinates are reset at events like load inm, death and sleep to keep the world size small

### End portals
If a world is too small for a stronghold to spawn, throwing an Eye of Ender will generate an end portal (~ <20 chunks)


### Commands
- `/globeworld` or `/globeworld pos`: shows the current dimension, active tile
  size, terrain mode, configured/effective simulation distance, canonical
  block/chunk position, tile alias, and whether you are already inside the
  canonical tile. It also reports the longitude offset and local solar day tick
  used by scrolling day/night mode.
- `/globeworld border_distance`: shows how far your canonical position is from
  each tile border and which border is nearest.
- `/globeworld teleport_canon`: teleports you to the canonical copy of your
  current X/Z position. Requires command permission level 2. This is useful if
  you want to reset yourself from a far-away alias without changing the place
  you are standing on in the wrapped world.
- `/globeworld teleport_border [inset]`: teleports you near the closest tile
  border for seam testing. Requires command permission level 2. The optional
  inset defaults to `1` block.
- `/globeworld teleport_alias <tileX> <tileZ>`: teleports you to a chosen visual
  alias of your current canonical position. Requires command permission level 2.
- `/globeworld entity <target>`: shows an entity's raw and canonical position,
  canonicalization status, passenger/root state, and mob target/pathing alias
  information when relevant.
- `/globeworld entities`: counts loaded entities in the current dimension that
  should be canonicalized and reports examples that are outside the canonical
  tile.
- `/globeworld end_portal`: reports whether the Overworld currently has a
  canonical stronghold, whether Eyes of Ender will use the fallback portal path,
  and any saved fallback portal frame.
- `/globeworld end_portal validate`: does the same End portal report with extra
  stronghold start validation. This may load or generate `STRUCTURE_STARTS`
  chunks.
- `/globeworld config` or `/globeworld config show`: shows saved Globe World
  settings.
- `/globeworld config set curvature <0-100>` and
  `/globeworld config set nether_curvature <0-100>`: change curvature strength.
- `/globeworld config set day_night <vanilla|scrolling>`: changes the day/night
  presentation mode.
- `/globeworld config set day_length <0.5-10>`: changes day length. Values up to
  `0.75` become `0.5`; larger values are rounded to whole multipliers.
- `/globeworld client entity_aliases`: shows local entity visual alias settings.
- `/globeworld client entity_aliases mode`: cycles local entity visual alias rendering.
- `/globeworld client entity_aliases rings`: cycles the local entity visual alias ring limit.

### Debug visuals
- `F3+Y`: toggles the Globe World debug overlay and tile-border renderer.


## Downloads

Published builds are attached to [GitHub Releases](../../releases):

- `globe-world-mc26.1.2-VERSION.jar`: the Fabric mod.
- `globe-world-curvature-mc26.1.2-VERSION.zip`: minimal Iris curvature
  shader pack.
- `makeup-ultra-fast-globe-world-mc26.1.2-VERSION.zip`: MakeUp Ultra Fast
  with Globe World curvature support.
