# Globe World Agent Notes

This repository is a Fabric mod for Minecraft 26.1.2.

## Mod Idea

Globe World makes a finite canonical Minecraft world tile appear continuous by wrapping chunk/block access in X/Z. The canonical tile owns mutable state; chunks outside it are virtual views of canonical chunks, with server packets relabeled so clients can render aliases at ordinary world coordinates.

Project design/status details live in [Globe World System Plan](docs/globe-world-system-plan.md).

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
- Use `docs/minecraft-mechanics/README.md` as the progressive-disclosure index for vanilla Minecraft mechanics and source references.
