# Globe World Agent Notes

This repository is a Fabric mod for Minecraft 26.1.2.
The root Gradle project is a small monorepo aggregator; the current Fabric mod
module lives in `mod-fabric/`.

## Mod Idea

Globe World makes a finite canonical Minecraft world tile appear continuous by wrapping chunk/block access in X/Z. The canonical tile owns mutable state; chunks outside it are virtual views of canonical chunks, with server packets relabeled so clients can render aliases at ordinary world coordinates.

Project documentation is split into three living indexes:

- [Globe World Mod Mechanics](docs/mod-mechanics/README.md): implemented mod behavior, why it exists, and the files that provide it.
- [Vanilla Mechanics Reference](docs/vanilla-mechanics/README.md): vanilla Minecraft mechanics and decompiled source anchors.
- [Plans](docs/plans/README.md): proposed or investigative work that is not yet fully implemented.

Update these docs as the project changes. When implementing a plan, move the durable explanation into mod mechanics and keep or remove the plan note based on whether the investigation is still useful.

## Minecraft Decompiled Sources

The local Loom cache contains Minecraft common code here:

`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-52430b475d/26.1.2/`

Useful files in that directory:

- `minecraft-common-52430b475d-26.1.2-sources.jar`
- `minecraft-common-52430b475d-26.1.2.jar`

The local Loom cache contains Minecraft client-only code here:

`.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-52430b475d/26.1.2/`

Useful files in that directory:

- `minecraft-clientOnly-52430b475d-26.1.2-sources.jar`
- `minecraft-clientOnly-52430b475d-26.1.2.jar`

When investigating vanilla Minecraft behavior, inspect these sources before guessing at method names or control flow. Use common sources for shared/server code and client-only sources for rendering, client networking, and other client-side behavior.

## Project Commands
- ask the user to run these

- Build: `./gradlew build`
- Run client: `./gradlew runClient`
- Stop client: `pkill -f runClient`

## Working Notes

- Prefer repo-local patterns and existing mixin style when changing code.
- Use `docs/vanilla-mechanics/README.md` as the progressive-disclosure index for vanilla Minecraft mechanics and source references.
- Use `docs/mod-mechanics/README.md` as the entry point for project-specific behavior and update it when mechanics or implementation files change.
- Use `docs/plans/README.md` for active/unimplemented work and update it when plans are added, implemented, superseded, or retired.
