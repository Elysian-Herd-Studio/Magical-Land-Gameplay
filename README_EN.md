# Magical Land Gameplay

English | [简体中文](README.md)

Independent gameplay addon for [Magical Land Appearance](https://github.com/Magical-Land-Official/Magical-Land), targeting Minecraft Fabric 1.20.1. Current work includes experimental unicorn remote presence, collision-aware flight, remote interactions, a single cargo slot and the golden-carrot horse encounter. Further tribe abilities remain planned; the latest prototype still requires in-game acceptance testing.

Install Gameplay and compatible Appearance with their dependencies on both client and server. Appearance does not depend on Gameplay. The same Appearance JAR retains server-side appearance synchronization; there is no separate sync download. Gameplay registers a custom entity and is not a server-only addon for vanilla clients.

Gameplay starts at 0.1.0, targeting Appearance 0.3.0 / API v1. Versions are independent; consult `fabric.mod.json` for the supported range. API artifacts are for compilation, not player installation.

Publish Appearance to a local Maven repository, then use the wrapper here with `-PappearanceMavenRepo=<absolute-path-to-appearance-build/repo>`. Maven Local is also supported. Builds do not require sibling source directories or copied artwork. See the [Chinese development guide](README.md) and [boundary document](docs/repository-boundary.md).

Mod, advancement, channel and cargo-save identifiers are preserved. Source was imported from Magical Land checkpoint `d4786bd`; earlier history remains in the original repository. Original authorship and MIT license are retained.
