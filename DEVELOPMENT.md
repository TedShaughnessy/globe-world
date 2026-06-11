# Globe World Development

The root README is for players and published artifacts. This file is the short
repo map for people changing the mod.

## Layout

```text
mod-fabric/                         Fabric mod module
shaderpacks/globe-world-curvature/  minimal Iris curvature shader pack
shaderpacks/makeup-ultra-fast-globe-world/
docs/mod-mechanics/                 implemented Globe World behavior
docs/vanilla-mechanics/             vanilla source notes
docs/mod-compatibility/             compatibility notes
docs/plans/                         active or historical plans
```

## Commands

Project notes ask the user to run Gradle commands.

```text
./gradlew build
./gradlew runClient
./gradlew buildAll
./gradlew shaderpacks
./gradlew stageDevRunAssets
pkill -f runClient
```

`buildAll` builds the mod, packages shader packs, and stages dev assets into
`mod-fabric/run/`.

## Releases

Artifact versions live in `gradle.properties`:

- `minecraft_version`
- `mod_version`
- `globe_world_curvature_shaderpack_version`
- `makeup_ultra_fast_globe_world_shaderpack_version`

Built artifact filenames include both the target Minecraft version and artifact
version, using the form `name-mcMINECRAFT_VERSION-VERSION`.

Runtime and metadata settings such as `loader_version`, `fabric_api_version`,
`java_version`, `mod_author`, `mod_homepage`, and `mod_sources` also live in
`gradle.properties`.

Changing one of those versions makes the GitHub Actions workflow upload the
corresponding artifact after the next push. GitHub Releases are published
manually through workflow dispatch with `publish_release`; that release attaches
the Fabric mod jar and both shader pack zips together.

## Docs

Start with:

- [Mod Mechanics](docs/mod-mechanics/README.md)
- [Vanilla Mechanics](docs/vanilla-mechanics/README.md)
- [Mod Compatibility](docs/mod-compatibility/README.md)
- [Plans](docs/plans/README.md)

When behavior changes, update the relevant living doc. When a plan is
implemented, move the durable explanation into `docs/mod-mechanics/`.

## Vanilla Sources

Local Loom sources:

```text
.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-52430b475d/26.1.2/
.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-52430b475d/26.1.2/
```

Inspect these sources before guessing at vanilla method names or control flow.
